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
    MorpheApp("RVX App", R.drawable.ic_rvx_app, "youtube", "revanced-anddea", listOf("app.rvx.android.youtube")),
    MorpheApp("RVX Music", R.drawable.ic_rvx_music, "youtube-music", "revanced-anddea", listOf("app.rvx.android.apps.youtube.music")),
    MorpheApp("Twitter", R.drawable.ic_twitter, "x-new", "piko-newx", listOf("com.twitter.android")),
    MorpheApp("Reddit", R.drawable.ic_reddit, "reddit", "morphe", listOf("com.reddit.frontpage")),
)

/**
 * Architecture tokens this device can actually run, best first, with "universal" as a safe
 * fallback. Built from the device's own supported ABIs (Build.SUPPORTED_ABIS) so a 32-bit
 * (armeabi-v7a) phone is never offered an arm64-v8a APK it cannot run, while a 64-bit phone
 * prefers the smaller native build over a larger universal one.
 */
val ARCH_PREFERENCE: List<String> = deviceArchPreference()

private fun deviceArchPreference(): List<String> {
    val order = LinkedHashSet<String>()
    val abis = android.os.Build.SUPPORTED_ABIS ?: emptyArray()
    for (abi in abis) {
        when (abi) {
            "arm64-v8a" -> order += "arm64-v8a"
            "armeabi-v7a", "armeabi" -> order += "armeabi-v7a"
            "x86_64" -> order += "x86_64"
            "x86" -> order += "x86"
        }
    }
    // A universal APK runs on any ABI; try the native build first, then fall back to it.
    order += "universal"
    // Alternate token sometimes seen in asset names.
    order += "arm-v7a"
    return order.toList()
}

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
    /** Skip any asset whose name contains one of these tokens. */
    val avoid: List<String> = emptyList(),
    /** Pick the build matching the device CPU architecture (else the first match). */
    val archAware: Boolean = false,
    /** On Huawei devices pick the HMS "hw" build; on other devices skip it. */
    val hwAware: Boolean = false,
)

val MICROG_CATALOG: List<MicroGEntry> = listOf(
    // GmsCore ships a normal build and a Huawei (hw) build; choose by device maker.
    MicroGEntry(
        "GmsCore", R.drawable.ic_gmscore, "ReVanced", "GmsCore",
        packages = listOf(GMS_PACKAGE), hwAware = true,
    ),
    // MicroG RE ships per-arch and universal APKs (each with/without a launcher icon).
    // Keep the icon builds and pick the one matching the device architecture.
    MicroGEntry(
        "MicroG RE", R.drawable.ic_microg_re, "MorpheApp", "MicroG-RE",
        packages = listOf(GMS_PACKAGE), avoid = listOf("noicon"), archAware = true,
    ),
)

// ---------------------------------------------------------------------------------------------
// TV tab — Android TV apps, taken straight from their own GitHub release pages.
// ---------------------------------------------------------------------------------------------

/**
 * An Android TV app, resolved from the latest release of [owner]/[repo]. The APK matching the
 * device architecture is chosen automatically (see [chooseForDevice]).
 */
data class TvEntry(
    val label: String,
    val subtitle: String,
    @DrawableRes val iconRes: Int,
    val owner: String,
    val repo: String,
    val packages: List<String> = emptyList(),
)

val TV_CATALOG: List<TvEntry> = listOf(
    TvEntry(
        "SmartTube", "YouTube for Android TV", R.drawable.ic_smarttube,
        owner = "yuliskov", repo = "SmartTube",
        packages = listOf("org.smarttube.stable", "app.smarttube", "com.teamsmart.videomanager.tv"),
    ),
    TvEntry(
        "TizenTube (Cobalt)", "YouTube (Cobalt) for Android TV", R.drawable.ic_tizentube,
        owner = "reisxd", repo = "TizenTubeCobalt",
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

/** CPU-arch tokens that can appear in an APK file name. */
private val ARCH_TOKENS =
    listOf("arm64-v8a", "arm64", "aarch64", "armeabi-v7a", "arm-v7a", "armeabi", "x86_64", "x86")

/** True when an APK name carries a CPU-arch token, so a token-less name counts as universal. */
fun hasArchToken(name: String): Boolean {
    val n = name.lowercase()
    return ARCH_TOKENS.any { it in n }
}

/** True when [name] names an APK built for the ABI family [key] (arm64/arm32/x64/x86). */
private fun matchesArch(name: String, key: String): Boolean {
    val n = name.lowercase()
    return when (key) {
        "arm64" -> "arm64" in n || "aarch64" in n
        // Covers "armeabi-v7a", "arm-v7a", "armv7", and a bare "arm" token (e.g. cobalt-arm.apk),
        // while never matching a 64-bit build.
        "arm32" -> ("armeabi-v7a" in n || "armeabi" in n || "armv7" in n || "arm-v7a" in n ||
            ("arm" in n && "arm64" !in n && "aarch64" !in n))
        "x64" -> "x86_64" in n || "x64" in n
        "x86" -> "x86" in n && "x86_64" !in n
        else -> false
    }
}

/**
 * Choose the APK that best fits this device from [assets], across the different arch naming
 * styles projects use (arm64-v8a / arm64, armeabi-v7a / arm, x86_64, x86, universal). Native
 * builds are tried in the device's own ABI order (Build.SUPPORTED_ABIS); a token-less or
 * universal build is the final fallback.
 */
fun chooseForDevice(assets: List<Asset>): Asset? {
    if (assets.isEmpty()) return null
    val tried = LinkedHashSet<String>()
    for (abi in (android.os.Build.SUPPORTED_ABIS ?: emptyArray())) {
        val key = when (abi) {
            "arm64-v8a" -> "arm64"
            "armeabi-v7a", "armeabi" -> "arm32"
            "x86_64" -> "x64"
            "x86" -> "x86"
            else -> continue
        }
        if (!tried.add(key)) continue
        assets.firstOrNull { matchesArch(it.name, key) }?.let { return it }
    }
    // No native match: use a universal / ABI-agnostic build, else whatever is there.
    return assets.firstOrNull { !hasArchToken(it.name) } ?: assets.firstOrNull()
}

/** Huawei devices lack Google Play Services and need the HMS ("hw") GmsCore build. */
fun isHuaweiDevice(): Boolean {
    val maker = (android.os.Build.MANUFACTURER ?: "").lowercase()
    val brand = (android.os.Build.BRAND ?: "").lowercase()
    return "huawei" in maker || "huawei" in brand
}

/** Pull a human version like "v20.51.39" out of an asset name, if present. */
fun versionOf(name: String): String? =
    Regex("""v\d[\w.]*""").find(name)?.value
