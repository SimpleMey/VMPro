package com.vmpro.app.data

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
)

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
