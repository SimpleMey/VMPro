package com.vmpro.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

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
 * Fetches releases for any public GitHub repository via the REST API.
 * No token is required for public, low-volume access (60 req/h per IP).
 *
 * When a [DiskCache] is supplied, the Apps-tab fetches can run "cache-first": return the last
 * saved response instantly (for a no-spinner first paint) and refresh from the network after.
 */
class GithubRepository(private val cache: DiskCache? = null) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * Core GET-as-text. With [cacheFirst] it returns the cached body for [cacheKey] and never
     * touches the network (throws [CacheMissException] when nothing is cached). Otherwise it
     * fetches over the network and, when a [cacheKey] is given, saves the body for next time.
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
        val url = "https://api.github.com/repos/$owner/$repo/releases?per_page=$perPage"
        parseReleases(getString(url, "application/vnd.github+json", cacheKey, cacheFirst))
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

    private fun parseReleases(json: String): List<Release> {
        val arr = JSONArray(json)
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
