package com.vmpro.app.data

import androidx.annotation.DrawableRes
import com.vmpro.app.R

/** The patch project an app build is based on (used as a section heading). */
enum class Project(val label: String, @DrawableRes val iconRes: Int) {
    MORPHE("Morphe", R.drawable.ic_morphe),
    REVANCED("ReVanced", R.drawable.ic_revanced),
}

/**
 * One curated app in the Apps / Modules tabs. Its files are resolved from the latest
 * matching release of [J_HC] (j-hc/revanced-magisk-module).
 *
 * Asset names look like `youtube-morphe-v20.51.39-all.apk` or
 * `music-revanced-module-v8.40.54-arm64-v8a.zip`, i.e. `{appKey}-{variant}-...`.
 */
data class AppEntry(
    val label: String,
    @DrawableRes val iconRes: Int,
    val project: Project,
    val appKey: String,
    val variant: String,
    /** Candidate installed package ids (renamed non-root builds) for install detection. */
    val packages: List<String> = emptyList(),
)

/** A MicroG-tab entry resolved from the latest release of its own repository. */
data class MicroGEntry(
    val label: String,
    @DrawableRes val iconRes: Int,
    val owner: String,
    val repo: String,
    val packages: List<String> = emptyList(),
    /** Prefer assets whose name contains this token, if several APKs exist. */
    val prefer: String? = null,
    /** Skip assets whose name contains this token. */
    val avoid: String? = null,
)

val J_HC = Source(
    title = "ReVanced Modules",
    subtitle = "j-hc / revanced-magisk-module",
    owner = "j-hc",
    repo = "revanced-magisk-module",
)

private val YT_REVANCED = listOf("app.revanced.android.youtube")
private val YTM_REVANCED = listOf("app.revanced.android.apps.youtube.music")
private val YT_MORPHE = listOf("app.morphe.android.youtube")
private val YTM_MORPHE = listOf("app.morphe.android.apps.youtube.music")

/** GmsCore (ReVanced) and MicroG RE (Morphe) ship under the same package id — only one installs. */
const val GMS_PACKAGE = "app.revanced.android.gms"

val APP_CATALOG: List<AppEntry> = listOf(
    // Morphe-based builds
    AppEntry("Twitter", R.drawable.ic_twitter, Project.MORPHE, "twitter", "piko", listOf("com.twitter.android")),
    AppEntry("YouTube", R.drawable.ic_youtube, Project.MORPHE, "youtube", "morphe", YT_MORPHE),
    AppEntry("YT Music", R.drawable.ic_ytmusic, Project.MORPHE, "music", "morphe", YTM_MORPHE),
    AppEntry("Reddit", R.drawable.ic_reddit, Project.MORPHE, "reddit", "morphe", listOf("com.reddit.frontpage")),
    // ReVanced-based builds
    AppEntry("YouTube", R.drawable.ic_youtube, Project.REVANCED, "youtube", "revanced", YT_REVANCED),
    AppEntry("YT Music", R.drawable.ic_ytmusic, Project.REVANCED, "music", "revanced", YTM_REVANCED),
)

val MICROG_CATALOG: List<MicroGEntry> = listOf(
    MicroGEntry(
        "GmsCore", R.drawable.ic_gmscore, "ReVanced", "GmsCore",
        packages = listOf(GMS_PACKAGE), avoid = "hw",
    ),
    MicroGEntry(
        "MicroG RE", R.drawable.ic_microg_re, "MorpheApp", "MicroG-RE",
        packages = listOf(GMS_PACKAGE),
    ),
)

/** GitHub owner of the patch set for a build variant (used to read the patch version). */
private fun patchOwnerFor(variant: String): String? = when (variant) {
    "morphe" -> "MorpheApp"
    "piko" -> "crimera"
    "revanced" -> "ReVanced"
    else -> null
}

/** Pull this variant's patch version out of a release body, e.g. "MorpheApp/patches-1.32.0.mpp". */
fun parsePatchVersion(body: String, variant: String): String? {
    val owner = patchOwnerFor(variant) ?: return null
    return Regex("""$owner/[\w-]*patches-([0-9][\w.]*?)\.mpp""", RegexOption.IGNORE_CASE)
        .find(body)?.groupValues?.get(1)
}

/** Preferred ABI order when a release ships per-architecture builds. */
private val ARCH_PREFERENCE = listOf("arm64-v8a", "all", "arm-v7a", "armeabi-v7a", "x86_64", "x86")

/** True when [name] is the asset for [entry] of the requested file type. */
fun assetMatches(name: String, entry: AppEntry, wantApk: Boolean): Boolean {
    val lower = name.lowercase()
    if (!lower.startsWith("${entry.appKey}-${entry.variant}-")) return false
    val isApk = lower.endsWith(".apk")
    val isModule = lower.endsWith(".zip") && (lower.contains("-module-") || lower.contains("-magisk-"))
    return if (wantApk) isApk else isModule
}

/** Among same-release matches, pick the most broadly useful architecture. */
fun pickPreferredArch(assets: List<Asset>): Asset {
    for (arch in ARCH_PREFERENCE) {
        assets.firstOrNull { it.name.lowercase().contains(arch) }?.let { return it }
    }
    return assets.first()
}

/** Pull a human version like "v20.51.39" out of an asset name, if present. */
fun versionOf(name: String): String? =
    Regex("""v\d[\w.]*""").find(name)?.value
