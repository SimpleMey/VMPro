package com.vmpro.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

/**
 * Fetches releases for any public GitHub repository via the REST API.
 * No token is required for public, low-volume access (60 req/h per IP).
 */
class GithubRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun fetchReleases(source: Source, perPage: Int = 40): List<Release> =
        fetchReleases(source.owner, source.repo, perPage)

    suspend fun fetchReleases(owner: String, repo: String, perPage: Int = 40): List<Release> =
        withContext(Dispatchers.IO) {
        val url = "https://api.github.com/repos/$owner/$repo/releases?per_page=$perPage"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "VMPro-App")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val hint = if (response.code == 403)
                    " (GitHub rate limit reached — try again later)" else ""
                throw RuntimeException("GitHub API error ${response.code}$hint")
            }
            val body = response.body?.string().orEmpty()
            parseReleases(body)
        }
    }

    /** Fetch a text file (e.g. the Morphe-Builds manifest.json) over HTTP. */
    private fun download(url: String): String =
        client.newCall(Request.Builder().url(url).header("User-Agent", "VMPro-App").build())
            .execute().use { resp ->
                if (!resp.isSuccessful) throw RuntimeException("Download error ${resp.code}")
                resp.body?.string().orEmpty()
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

    /** Fetch the Morphe-Builds release that carries manifest.json and parse the manifest. */
    suspend fun fetchMorpheBuilds(source: Source): MorpheBuilds = withContext(Dispatchers.IO) {
        val releases = fetchReleases(source.owner, source.repo, perPage = 3)
        val release = releases.firstOrNull { r -> r.assets.any { it.name == "manifest.json" } }
            ?: releases.firstOrNull()
        val manifestAsset = release?.assets?.firstOrNull { it.name == "manifest.json" }
        val entries = manifestAsset?.let {
            runCatching { parseManifest(download(it.downloadUrl)) }.getOrDefault(emptyMap())
        } ?: emptyMap()
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

    /** Latest *stable* (non-prerelease) release's preferred APK for a standalone MicroG repo. */
    suspend fun resolveMicroG(entry: MicroGEntry): ResolvedAsset? {
        val releases = fetchReleases(entry.owner, entry.repo, perPage = 15)
        for (release in releases.filter { !it.prerelease }) {
            val apks = release.assets.filter { it.isApk }
                .filter { entry.avoid == null || !it.name.contains(entry.avoid, ignoreCase = true) }
            val chosen = apks.firstOrNull {
                entry.prefer != null && it.name.contains(entry.prefer, ignoreCase = true)
            } ?: apks.firstOrNull()
            if (chosen != null) return ResolvedAsset(chosen, release)
        }
        return null
    }

    // ---- TV tab: Android TV apps from their own release pages ----

    /** Latest non-prerelease APK for an Android TV app, preferring stable + the best arch. */
    suspend fun resolveTv(entry: TvEntry): ResolvedAsset? {
        val releases = fetchReleases(entry.owner, entry.repo, perPage = 6)
        for (release in releases.filter { !it.prerelease }) {
            val apks = release.assets.filter { it.isApk }
            if (apks.isEmpty()) continue
            val pool = apks.filter { it.name.contains("stable", ignoreCase = true) }.ifEmpty { apks }
            val chosen = entry.archPreference.firstNotNullOfOrNull { pref ->
                pool.firstOrNull { it.name.contains(pref, ignoreCase = true) }
            } ?: pool.first()
            return ResolvedAsset(chosen, release)
        }
        return null
    }
}
