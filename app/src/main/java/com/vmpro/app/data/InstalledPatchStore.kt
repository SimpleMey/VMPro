package com.vmpro.app.data

import android.content.Context

/**
 * Remembers the ReVanced patch version VMPro installed for each package.
 *
 * Android's [android.content.pm.PackageManager] only reports an app's *base* version
 * (e.g. YouTube 19.47.53); the ReVanced patch version lives only in the GitHub release
 * body and is not recoverable from an installed package. Without remembering it, VMPro
 * can't tell that a new *patch-only* build (same base version, newer patches) is an update.
 *
 * Stored as "<baseVersion>|<patchVersion>" keyed by package name. The base version is kept
 * so a recorded patch is trusted only while the installed base version still matches — this
 * guards against the app being replaced by a build installed outside VMPro.
 */
object InstalledPatchStore {
    private const val PREFS = "vmpro_patches"

    /** Record the patch version installed for [packageName]. No-op if any field is blank. */
    fun record(context: Context, packageName: String, baseVersion: String?, patch: String?) {
        if (packageName.isBlank() || baseVersion.isNullOrBlank() || patch.isNullOrBlank()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(packageName, "$baseVersion|$patch").apply()
    }

    /** Every recorded entry (package -> "base|patch"). */
    fun all(context: Context): Map<String, String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all
            .mapNotNull { (k, v) -> (v as? String)?.let { k to it } }
            .toMap()
}
