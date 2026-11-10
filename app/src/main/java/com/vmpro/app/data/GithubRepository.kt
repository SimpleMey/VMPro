package com.vmpro.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/** Result of a VirusTotal hash lookup. */
sealed class VtOutcome {
    /** [flagged] of [total] engines flagged the file (flagged == 0 means clean). */
    data class Found(val flagged: Int, val total: Int) : VtOutcome()
    /** No one has scanned this exact file yet (common for a freshly built APK). */
    object NotFound : VtOutcome()
    object BadKey : VtOutcome()
    object RateLimited : VtOutcome()
    data class Error(val code: Int) : VtOutcome()
}

/** Thrown by a cache-first read when nothing is cached yet, so callers can fall through. */
class CacheMissException : RuntimeException()

/**
 * The aggregated catalog Morphe-Builds publishes as a single CDN file: the latest releases for
 * every repo VMPro shows, keyed "owner/repo", plus the raw Morphe manifest JSON. The app resolves
 * its whole catalog from this instead of making many per-repo GitHub API calls.
 */
private class Catalog(
    val repos: Map<String, List<Release>>,
    val morpheManifestJson: String,
)

/** catalog.json on the Morphe-Builds `latest` release (CDN, not the rate-limited API). */
private const val CATALOG_URL =
    "https://github.com/SimpleMey/Morphe-Builds/releases/latest/download/catalog.json"
private const val CATALOG_KEY = "catalog.json"

/**
 * Fetches releases for any public GitHub repository via the REST API.
 * No token is required for public, low-volume access (60 req/h per IP).
 *
 * To live within that 60/hour limit two caches work together:
 *  - an OkHttp HTTP cache, so repeat calls revalidate with ETags; GitHub answers unchanged data
 *    with `304 Not Modified`, which does NOT count against the rate limit;
 *  - a [DiskCache] of the last good response body per endpoint, so the Apps tab can paint
 *    instantly ("cache-first") and, when the limit is still hit (HTTP 403), every tab falls back
 *    to its last list instead of showing an error.
 */
class GithubRepository(cacheDir: File? = null) {
    private val cache: DiskCache? = cacheDir?.let { DiskCache(it) }

    // The fetched catalog, reused across one load; dropped by invalidateCatalog() on refresh.
    @Volatile private var cachedCatalog: Catalog? = null
    private val catalogLock = Mutex()

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .apply {
            // 5 MB is plenty for small JSON release listings; enables ETag revalidation (free 304s).
            if (cacheDir != null) cache(Cache(File(cacheDir, "http-cache"), 5L * 1024 * 1024))
        }
        .build()

    /**
     * Core GET-as-text. With [cacheFirst] it returns the cached body for [cacheKey] and never
     * touches the network (throws [CacheMissException] when nothing is cached). Otherwise it
     * fetches over the network and, when a [cacheKey] is given, saves the body for next time.
     *
     * On a network failure (no connection, or GitHub's 403 rate limit) it returns the last saved
     * body for [cacheKey] when there is one, so the user keeps seeing the previous list instead of
     * an error; only a first-ever failure with nothing cached propagates.
     */
    private fun getString(
        url: String,
        accept: String? = null,
        cacheKey: String? = null,
        cacheFirst: Boolean = false,
    ): String {
        if (cacheFirst) {
            return (cacheKey?.let { cache?.read(it) }) ?: throw CacheMissException()
        }
        try {
            val builder = Request.Builder().url(url).header("User-Agent", "VMPro-App")
            if (accept != null) builder.header("Accept", accept)
            client.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    val hint = if (response.code == 403)
                        " (GitHub rate limit reached — try again later)" else ""
                    throw RuntimeException("GitHub API error ${response.code}$hint")
                }
                val body = response.body?.string().orEmpty()
                if (cacheKey != null) cache?.write(cacheKey, body)
                return body
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            (cacheKey?.let { cache?.read(it) })?.let { return it }
            throw e
        }
    }

    suspend fun fetchReleases(source: Source, perPage: Int = 40): List<Release> =
        fetchReleases(source.owner, source.repo, perPage)

    suspend fun fetchReleases(
        owner: String,
        repo: String,
        perPage: Int = 40,
        cacheKey: String? = null,
        cacheFirst: Boolean = false,
    ): List<Release> = withContext(Dispatchers.IO) {
        // Prefer the aggregated catalog.json (one CDN file, no GitHub API call); the live API is
        // only a fallback for repos the catalog does not carry or when it is unavailable.
        catalog(cacheFirst)?.repos?.get("$owner/$repo")?.let { rels ->
            return@withContext if (rels.size > perPage) rels.take(perPage) else rels
        }
        val url = "https://api.github.com/repos/$owner/$repo/releases?per_page=$perPage"
        // Every endpoint gets a cache key by default, so a 403 falls back to its last response.
        val key = cacheKey ?: "rel.$owner.$repo.$perPage"
        parseReleases(getString(url, "application/vnd.github+json", key, cacheFirst))
    }

    /**
     * The aggregated catalog.json from Morphe-Builds (one CDN file covering every app), or null
     * when it is unavailable so callers fall back to the live API. With [cacheFirst] it reads only
     * the last saved copy (for the instant first paint); otherwise the fresh copy is fetched once
     * per load and reused. [invalidateCatalog] drops it on an explicit refresh.
     */
    private suspend fun catalog(cacheFirst: Boolean): Catalog? {
        if (cacheFirst) {
            val json = withContext(Dispatchers.IO) {
                runCatching {
                    getString(CATALOG_URL, cacheKey = CATALOG_KEY, cacheFirst = true)
                }.getOrNull()
            } ?: return null
            return runCatching { parseCatalog(json) }.getOrNull()
        }
        cachedCatalog?.let { return it }
        return catalogLock.withLock {
            cachedCatalog ?: run {
                val json = withContext(Dispatchers.IO) {
                    runCatching { getString(CATALOG_URL, cacheKey = CATALOG_KEY) }.getOrNull()
                }
                json?.let { runCatching { parseCatalog(it) }.getOrNull() }?.also { cachedCatalog = it }
            }
        }
    }

    /** Drop the cached catalog so the next load re-fetches it (called on an explicit refresh). */
    fun invalidateCatalog() {
        cachedCatalog = null
    }

    private fun parseCatalog(json: String): Catalog {
        val root = JSONObject(json)
        val reposObj = root.optJSONObject("repos") ?: JSONObject()
        val repos = HashMap<String, List<Release>>()
        val keys = reposObj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val arr = reposObj.optJSONArray(k) ?: continue
            repos[k] = parseReleaseArray(arr)
        }
        val manifest = root.optJSONObject("morphe_manifest")?.toString().orEmpty()
        return Catalog(repos, manifest)
    }

    /**
     * Look up a file's VirusTotal report by SHA-256 using the user's own API key (a hash lookup,
     * not an upload). Returns [VtOutcome.NotFound] when no one has scanned that exact file yet.
     */
    suspend fun virusTotalLookup(apiKey: String, sha256: String): VtOutcome =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("https://www.virustotal.com/api/v3/files/$sha256")
                .header("x-apikey", apiKey)
                .header("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { resp ->
                when (resp.code) {
                    200 -> {
                        val stats = JSONObject(resp.body?.string().orEmpty())
                            .getJSONObject("data").getJSONObject("attributes")
                            .getJSONObject("last_analysis_stats")
                        val flagged = stats.optInt("malicious") + stats.optInt("suspicious")
                        val total = flagged + stats.optInt("harmless") + stats.optInt("undetected") +
                            stats.optInt("timeout") + stats.optInt("failure") +
                            stats.optInt("type-unsupported")
                        VtOutcome.Found(flagged, total)
                    }
                    404 -> VtOutcome.NotFound
                    401 -> VtOutcome.BadKey
                    429 -> VtOutcome.RateLimited
                    else -> VtOutcome.Error(resp.code)
                }
            }
        }

    private fun parseReleases(json: String): List<Release> = parseReleaseArray(JSONArray(json))

    private fun parseReleaseArray(arr: JSONArray): List<Release> {
        val releases = ArrayList<Release>(arr.length())
        for (i in 0 until arr.length()) {
            val r = arr.getJSONObject(i)
            val assetsArr = r.optJSONArray("assets") ?: JSONArray()
            val assets = ArrayList<Asset>(assetsArr.length())
            for (j in 0 until assetsArr.length()) {
                val a = assetsArr.getJSONObject(j)
                assets.add(
                    Asset(
                        name = a.optString("name"),
                        sizeBytes = a.optLong("size"),
                        downloadUrl = a.optString("browser_download_url"),
                        downloadCount = a.optInt("download_count"),
                    )
                )
            }
            assets.sortBy { it.name }
            releases.add(
                Release(
                    tag = r.optString("tag_name"),
                    name = r.optString("name").ifBlank { r.optString("tag_name") },
                    publishedAt = r.optString("published_at").take(10),
                    htmlUrl = r.optString("html_url"),
                    prerelease = r.optBoolean("prerelease"),
                    body = r.optString("body"),
                    assets = assets,
                )
            )
        }
        return releases
    }

    // ---- Apps tab: Morphe-Builds (single `latest` release + manifest.json) ----

    /** Fetch the Morphe-Builds release that carries manifest.json and parse the manifest.
     *  The manifest has a stable `releases/latest/download/manifest.json` URL, so we download
     *  it in PARALLEL with the release list instead of waiting for the list first (two
     *  sequential round-trips becomes one). Only the latest release is needed. */
    suspend fun fetchMorpheBuilds(source: Source, cacheFirst: Boolean = false): MorpheBuilds =
        coroutineScope {
        // Prefer the aggregated catalog.json: it carries the Morphe manifest and the Morphe-Builds
        // release assets together, so no manifest download or API call is needed.
        catalog(cacheFirst)?.let { cat ->
            val entries = runCatching { parseManifest(cat.morpheManifestJson) }.getOrDefault(emptyMap())
            val releases = cat.repos["${source.owner}/${source.repo}"].orEmpty()
            val release = releases.firstOrNull { r -> r.assets.any { it.name == "manifest.json" } }
                ?: releases.firstOrNull()
            if (entries.isNotEmpty() && release != null) {
                return@coroutineScope MorpheBuilds(release, entries)
            }
        }
        val manifestUrl =
            "https://github.com/${source.owner}/${source.repo}/releases/latest/download/manifest.json"
        val entriesDeferred = async(Dispatchers.IO) {
            runCatching {
                parseManifest(getString(manifestUrl, cacheKey = "morphe.manifest", cacheFirst = cacheFirst))
            }.getOrDefault(emptyMap())
        }
        val releasesDeferred = async(Dispatchers.IO) {
            runCatching {
                fetchReleases(
                    source.owner, source.repo, perPage = 1,
                    cacheKey = "morphe.releases", cacheFirst = cacheFirst,
                )
            }.getOrDefault(emptyList())
        }
        val releases = releasesDeferred.await()
        val release = releases.firstOrNull { r -> r.assets.any { it.name == "manifest.json" } }
            ?: releases.firstOrNull()
        var entries = entriesDeferred.await()
        if (entries.isEmpty() && !cacheFirst) {
            // Stable URL failed (e.g. not marked "Latest"); fall back to the fetched release's asset.
            val manifestAsset = release?.assets?.firstOrNull { it.name == "manifest.json" }
            entries = manifestAsset?.let {
                runCatching {
                    parseManifest(getString(it.downloadUrl, cacheKey = "morphe.manifest"))
                }.getOrDefault(emptyMap())
            } ?: emptyMap()
        }
        MorpheBuilds(release, entries)
    }

    /** Resolve one app from the manifest by app_name + source, choosing the best architecture. */
    fun resolveMorphe(builds: MorpheBuilds, appName: String, source: String): ResolvedAsset? {
        val release = builds.release ?: return null
        val candidates = builds.entries.values.filter { it.appName == appName && it.source == source }
        if (candidates.isEmpty()) return null
        val chosen = ARCH_PREFERENCE.firstNotNullOfOrNull { arch ->
            candidates.firstOrNull { it.arch == arch }
        } ?: candidates.first()
        val asset = release.assets.firstOrNull { it.name == chosen.apk } ?: return null
        return ResolvedAsset(asset, release, version = chosen.version)
    }

    // ---- Modules tab: j-hc (.zip modules) ----

    /** First release (newest-first) holding a module .zip for [entry], best architecture. */
    fun resolveModule(releases: List<Release>, entry: ModuleEntry): ResolvedAsset? {
        for (release in releases) {
            val matches = release.assets.filter { moduleMatches(it.name, entry.appKey, entry.variant) }
            if (matches.isNotEmpty()) return ResolvedAsset(pickPreferredArch(matches), release)
        }
        return null
    }

    // ---- MicroG tab ----

    /** Latest *stable* (non-prerelease) release's APK for a MicroG repo, matched to the device. */
    suspend fun resolveMicroG(entry: MicroGEntry): ResolvedAsset? {
        val huawei = isHuaweiDevice()
        val releases = fetchReleases(entry.owner, entry.repo, perPage = 15)
        for (release in releases.filter { !it.prerelease }) {
            var apks = release.assets.filter { it.isApk }
            if (entry.avoid.isNotEmpty()) {
                apks = apks.filter { a -> entry.avoid.none { a.name.contains(it, ignoreCase = true) } }
            }
            // GmsCore ships a Huawei (hw) build and a normal one: take the right one per device.
            if (entry.hwAware) {
                apks = if (huawei) {
                    apks.filter { it.name.contains("-hw", ignoreCase = true) }.ifEmpty { apks }
                } else {
                    apks.filter { !it.name.contains("-hw", ignoreCase = true) }
                }
            }
            if (apks.isEmpty()) continue
            val chosen = if (entry.archAware) chooseForDevice(apks) else apks.firstOrNull()
            if (chosen != null) return ResolvedAsset(chosen, release)
        }
        return null
    }

    // ---- TV tab: Android TV apps from their own release pages ----

    /** Latest non-prerelease APK for an Android TV app, preferring stable + the best arch. */
    suspend fun resolveTv(entry: TvEntry, cacheFirst: Boolean = false): ResolvedAsset? {
        val releases = runCatching {
            fetchReleases(
                entry.owner, entry.repo, perPage = 6,
                cacheKey = "rel.${entry.owner}.${entry.repo}", cacheFirst = cacheFirst,
            )
        }.getOrDefault(emptyList())
        for (release in releases.filter { !it.prerelease }) {
            val apks = release.assets.filter { it.isApk }
            if (apks.isEmpty()) continue
            val pool = apks.filter { it.name.contains("stable", ignoreCase = true) }.ifEmpty { apks }
            val chosen = chooseForDevice(pool) ?: pool.first()
            return ResolvedAsset(chosen, release)
        }
        return null
    }
}
