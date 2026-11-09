package com.vmpro.app.util

import android.content.Context

/**
 * The user's own VirusTotal API key, set in Settings and stored per device. Each user brings
 * their own free key, so there is no single shared rate limit across everyone. Empty when unset.
 */
object VtPrefs {
    private const val FILE = "vmpro_settings"
    private const val KEY = "virustotal_api_key"
    private const val SHIZUKU = "shizuku_install"

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun key(context: Context): String = prefs(context).getString(KEY, "").orEmpty()

    fun setKey(context: Context, value: String) {
        prefs(context).edit().putString(KEY, value.trim()).apply()
    }

    /** User chose to install silently via Shizuku (used only when Shizuku is actually available). */
    fun shizukuInstall(context: Context): Boolean = prefs(context).getBoolean(SHIZUKU, false)

    fun setShizukuInstall(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(SHIZUKU, on).apply()
    }
}
