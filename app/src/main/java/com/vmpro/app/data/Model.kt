package com.vmpro.app.data

import org.json.JSONObject

/** A single downloadable file attached to a GitHub release. */
data class Asset(
    val name: String,
    val sizeBytes: Long,
    val downloadUrl: String,
    val downloadCount: Int,
) {
    val isApk: Boolean get() = name.endsWith(".apk", ignoreCase = true)
    val isModule: Boolean get() = name.endsWith(".zip", ignoreCase = true)

    val readableSize: String get() = formatBytes(sizeBytes)
}

/** A GitHub release with its assets. */
data class Release(
    val tag: String,
    val name: String,
    val publishedAt: String,
    val htmlUrl: String,
    val prerelease: Boolean,
    val body: String,
    val assets: List<Asset>,
)

/** An asset together with the release it came from (release body holds patch versions). */
data class ResolvedAsset(
    val asset: Asset,
    val release: Release,
    /** Version string when known from an index (e.g. the Morphe-Builds manifest). */
    val version: String? = null,
)

/** One build listed in Morphe-Builds' manifest.json (key = "app_name|source|arch"). */
data class ManifestEntry(
    val appName: String,
    val source: String,
    val arch: String,
    val apk: String,
    val version: String,
)

/** The resolved Morphe-Builds `latest` release together with its parsed manifest. */
data class MorpheBuilds(
    val release: Release?,
    val entries: Map<String, ManifestEntry>,
)

/** Parse Morphe-Builds' manifest.json into entries keyed "app_name|source|arch". */
fun parseManifest(json: String): Map<String, ManifestEntry> {
    val entriesObj = JSONObject(json).optJSONObject("entries") ?: return emptyMap()
    val out = LinkedHashMap<String, ManifestEntry>()
    val keys = entriesObj.keys()
    while (keys.hasNext()) {
        val k = keys.next()
        val e = entriesObj.getJSONObject(k)
        out[k] = ManifestEntry(
            appName = e.optString("app_name"),
            source = e.optString("source"),
            arch = e.optString("arch"),
            apk = e.optString("apk"),
            version = e.optString("built_version"),
        )
    }
    return out
}

/**
 * True when [available] is a strictly newer version string than [installed], comparing
 * dot/underscore/dash separated numeric components. Non-numeric parts are ignored.
 */
fun isNewerVersion(available: String?, installed: String?): Boolean {
    if (available.isNullOrBlank() || installed.isNullOrBlank()) return false
    fun parts(v: String) = v.removePrefix("v").split('.', '_', '-').mapNotNull { it.toIntOrNull() }
    val a = parts(available)
    val b = parts(installed)
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}

/**
 * True when [installedVersion] belongs to the same product as [availableVersion], compared by
 * major-version family. Used to tell apart the two builds that share the MicroG package —
 * GmsCore ships 0.x, MicroG RE ships 6.x — so an in-place update isn't mistaken for a switch.
 */
fun sameVersionFamily(availableVersion: String?, installedVersion: String?): Boolean {
    val a = availableVersion?.removePrefix("v")?.substringBefore('.')?.toIntOrNull() ?: return false
    val b = installedVersion?.removePrefix("v")?.substringBefore('.')?.toIntOrNull() ?: return false
    return a == b
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var i = 0
    while (value >= 1024 && i < units.lastIndex) {
        value /= 1024
        i++
    }
    return if (i == 0) "$bytes B" else String.format("%.1f %s", value, units[i])
}
