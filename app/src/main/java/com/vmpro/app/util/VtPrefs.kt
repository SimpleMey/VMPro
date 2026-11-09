package com.vmpro.app.util

import android.content.Context

/**
 * The user's own VirusTotal API key, set in Settings and stored per device. Each user brings
 * their own free key, so there is no single shared rate limit across everyone. Empty when unset.
 */
object VtPrefs {
    private const val FILE = "vmpro_settings"
    private const val KEY = "virustotal_api_key"

    fun key(context: Context): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()

    fun setKey(context: Context, value: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY, value.trim()).apply()
    }
}
