package com.vmpro.app.data

import androidx.annotation.DrawableRes
import com.vmpro.app.R

// ---------------------------------------------------------------------------------------------
// Apps tab — non-root APKs, built by the user's Morphe auto-builder and resolved from the
// manifest.json published in its single `latest` release.
// ---------------------------------------------------------------------------------------------

/** The user's Morphe auto-build repository (publishes one `latest` release + manifest.json). */
val MORPHE_BUILDS = Source(
    title = "Morphe APKs (auto-built)",
    subtitle = "SimpleMey / Morphe-Builds",
    owner = "SimpleMey",
    repo = "Morphe-Builds",
)

/**
 * One app in the Apps tab. Resolved from Morphe-Builds' manifest by matching
 * `app_name` + `source`; the best available architecture is chosen automatically.
 */
data class MorpheApp(
    val label: String,
    @DrawableRes val iconRes: Int,
    val appName: String,
    val source: String,
    val packages: List<String> = emptyList(),
)

val APP_CATALOG: List<MorpheApp> = listOf(
    MorpheApp("YouTube", R.drawable.ic_youtube, "youtube", "morphe", listOf("app.morphe.android.youtube")),
    MorpheApp("YT Music", R.drawable.ic_ytmusic, "youtube-music", "morphe", listOf("app.morphe.android.apps.youtube.music")),
    MorpheApp("Twitter", R.drawable.ic_twitter, "x-new", "piko-newx", listOf("com.twitter.android")),
    MorpheApp("Reddit", R.drawable.ic_reddit, "reddit", "morphe", listOf("com.reddit.frontpage")),
)

/** Preferred architecture order when the manifest has several builds of one app. */
val ARCH_PREFERENCE = listOf("arm64-v8a", "universal", "armeabi-v7a", "arm-v7a", "x86_64", "x86")

// ---------------------------------------------------------------------------------------------
// Modules tab — Magisk/KernelSU .zip modules, still built by j-hc (Morphe-Builds is APK-only).
// ---------------------------------------------------------------------------------------------

val J_HC = Source(
    title = "ReVanced Modules",
    subtitle = "j-hc / revanced-magisk-module",
    owner = "j-hc",
    repo = "revanced-magisk-module",
)

/** One module in the Modules tab, resolved from the latest j-hc release (`.zip`). */
data class ModuleEntry(
    val label: String,
    @DrawableRes val iconRes: Int,
    val appKey: String,
    val variant: String,
)

val MODULE_CATALOG: List<ModuleEntry> = listOf(
    ModuleEntry("YouTube", R.drawable.ic_youtube, "youtube", "morphe"),
    ModuleEntry("YT Music", R.drawable.ic_ytmusic, "music", "morphe"),
    ModuleEntry("Twitter", R.drawable.ic_twitter, "twitter", "piko"),
    ModuleEntry("Reddit", R.drawable.ic_reddit, "reddit", "morphe"),
)

// ---------------------------------------------------------------------------------------------
// MicroG tab — GmsCore (ReVanced) and MicroG RE (Morphe) share one package; only one installs.
// ---------------------------------------------------------------------------------------------

/** GmsCore (ReVanced) and MicroG RE (Morphe) ship under the same package id — only one installs. */
const val GMS_PACKAGE = "app.revanced.android.gms"

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

// ---------------------------------------------------------------------------------------------
// TV tab — Android TV apps, taken straight from their own GitHub release pages.
// ---------------------------------------------------------------------------------------------

/** An Android TV app, resolved from the latest release of [owner]/[repo]. */
data class TvEntry(
    val label: String,
    val subtitle: String,
    @DrawableRes val iconRes: Int,
    val owner: String,
    val repo: String,
    /** Match tokens against APK names, first hit wins (falls back to the first APK). */
    val archPreference: List<String>,
    val packages: List<String> = emptyList(),
)

val TV_CATALOG: List<TvEntry> = listOf(
    TvEntry(
        "SmartTube", "YouTube for Android TV", R.drawable.ic_smarttube,
        owner = "yuliskov", repo = "SmartTube",
        archPreference = listOf("arm64-v8a", "universal", "armeabi-v7a"),
        packages = listOf("org.smarttube.stable", "app.smarttube", "com.teamsmart.videomanager.tv"),
    ),
    TvEntry(
        "TizenTube (Cobalt)", "YouTube (Cobalt) for Android TV", R.drawable.ic_tizentube,
        owner = "reisxd", repo = "TizenTubeCobalt",
        archPreference = listOf("arm64", "arm"),
    ),
)

/**
 * Phone apps taken straight from their own GitHub releases (not the Morphe-Builds manifest).
 * Shown in the Apps tab alongside the Morphe builds, resolved with the same release logic as TV.
 */
val PHONE_DIRECT: List<TvEntry> = listOf(
    TvEntry(
        "NewTube", "YouTube client", R.drawable.ic_newtube,
        owner = "aleixrodriala", repo = "newtube",
        archPreference = listOf("arm64-v8a", "universal", "armeabi-v7a"),
        packages = listOf("io.github.aleixrodriala.arc"),
    ),
)

// ---------------------------------------------------------------------------------------------
// Asset-name helpers (modules + version parsing).
// ---------------------------------------------------------------------------------------------

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

/** True when [name] is the j-hc module (`.zip`) asset for [appKey]/[variant]. */
fun moduleMatches(name: String, appKey: String, variant: String): Boolean {
    val lower = name.lowercase()
    if (!lower.startsWith("${appKey}-${variant}-")) return false
    return lower.endsWith(".zip") && (lower.contains("-module-") || lower.contains("-magisk-"))
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
