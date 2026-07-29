package com.vmpro.app.ui

import android.content.Context

/**
 * Tracks whether the user has dismissed the "share VMPro" exit prompt for the current app
 * version. The dismissal is keyed to the version name, so a new release re-arms the prompt
 * automatically — the user is nudged once per update, then left alone once they act on it.
 */
object SharePrompt {
    private const val PREFS = "vmpro_prefs"
    private const val KEY = "share_prompt_version"

    /** True once the user tapped "Share" or "Already shared" for [version]. */
    fun isDoneFor(context: Context, version: String): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) == version

    fun markDone(context: Context, version: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, version).apply()
    }
}
