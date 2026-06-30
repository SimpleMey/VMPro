package com.vmpro.app.analytics

import android.content.Context
import com.aptabase.Aptabase
import com.vmpro.app.BuildConfig

/**
 * Single funnel for all analytics. Swap the provider here without touching call sites.
 *
 * Privacy: only coarse, non-identifying events are sent (which tab is viewed, which file
 * is downloaded). No personal data, no device identifiers beyond what the Aptabase SDK
 * needs for anonymous session counting.
 *
 * Setup: create a free app at https://aptabase.com, copy its App Key from the
 * "Instructions" tab, and put it in local.properties as `aptabase.key=A-XX-...`.
 * The key is injected at build time via BuildConfig (kept out of the public repo).
 * When the key is blank the SDK is never started and the app works normally.
 */
object Analytics {

    private val appKey: String = BuildConfig.APTABASE_KEY

    fun init(context: Context) {
        if (appKey.isBlank()) return
        runCatching { Aptabase.instance.initialize(context.applicationContext, appKey) }
    }

    fun event(name: String, props: Map<String, Any> = emptyMap()) {
        runCatching { Aptabase.instance.trackEvent(name, props) }
    }

    // --- Typed helpers keep event names consistent across the app ---

    fun appOpen() = event("app_open")

    fun tabView(tab: String) = event("tab_view", mapOf("tab" to tab))

    fun downloadStarted(file: String, kind: String) =
        event("download_started", mapOf("file" to file, "kind" to kind))

    fun downloadCompleted(file: String) = event("download_completed", mapOf("file" to file))

    fun downloadFailed(file: String) = event("download_failed", mapOf("file" to file))

    fun installClicked(file: String) = event("install_clicked", mapOf("file" to file))
}
