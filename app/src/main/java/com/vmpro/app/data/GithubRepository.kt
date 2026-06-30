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

    /**
     * Walk [releases] newest-first and return the first asset matching [entry] of the
     * requested file type, choosing the most useful architecture within that release.
     */
    fun resolveApp(releases: List<Release>, entry: AppEntry, wantApk: Boolean): ResolvedAsset? {
        for (release in releases) {
            val matches = release.assets.filter { assetMatches(it.name, entry, wantApk) }
            if (matches.isNotEmpty()) return ResolvedAsset(pickPreferredArch(matches), release)
        }
        return null
    }

    /** Latest release's preferred APK for a standalone MicroG repo. */
    suspend fun resolveMicroG(entry: MicroGEntry): ResolvedAsset? {
        val releases = fetchReleases(entry.owner, entry.repo, perPage = 5)
        for (release in releases) {
            val apks = release.assets.filter { it.isApk }
                .filter { entry.avoid == null || !it.name.contains(entry.avoid, ignoreCase = true) }
            val chosen = apks.firstOrNull {
                entry.prefer != null && it.name.contains(entry.prefer, ignoreCase = true)
            } ?: apks.firstOrNull()
            if (chosen != null) return ResolvedAsset(chosen, release)
        }
        return null
    }
}
