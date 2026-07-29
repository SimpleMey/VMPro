package com.vmpro.app.analytics

import android.content.Context
import android.util.Log
import com.posthog.PostHog
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig
import com.vmpro.app.BuildConfig

/**
 * Single funnel for all analytics (PostHog). Swap the provider here without touching call sites.
 *
 * Privacy/volume choices:
 * - No session replay, no screen autocapture, no deep-link capture — only the coarse,
 *   non-identifying events below (which tab, which file). No PII.
 * - PostHog's lifecycle capture provides "Application Opened" (drives active-user counts),
 *   so we don't send a separate app_open event.
 * - Disabled entirely in debug builds so development/testing never consumes the quota.
 *
 * Setup: create a project at https://posthog.com, copy the Project API Key (starts with
 * "phc_"), and put it in local.properties as `posthog.key=phc_...` (and `posthog.host=` if
 * you use EU / self-hosted). Injected at build time via BuildConfig; blank key = disabled.
 */
object Analytics {

    private val apiKey: String = BuildConfig.POSTHOG_KEY
    private val host: String = BuildConfig.POSTHOG_HOST.ifBlank { "https://us.i.posthog.com" }

    fun init(context: Context) {
        if (BuildConfig.DEBUG) return          // don't spend quota on dev/test builds
        if (apiKey.isBlank()) return
        runCatching {
            val config = PostHogAndroidConfig(apiKey = apiKey, host = host).apply {
                // We send our own "app_open" (with is_returning) instead, so active users
                // and new-vs-returning both come from one clean event.
                captureApplicationLifecycleEvents = false
                captureScreenViews = false
                captureDeepLinks = false
                sessionReplay = false
            }
            PostHogAndroid.setup(context.applicationContext, config)
        }.onFailure { Log.w("Analytics", "PostHog init failed", it) }
    }

    /** Fire once per launch. [isReturning] = false on the very first launch, true after. */
    fun appOpen(isReturning: Boolean) = event("app_open", mapOf("is_returning" to isReturning))

    fun event(name: String, props: Map<String, Any> = emptyMap()) {
        runCatching { PostHog.capture(event = name, properties = props) }
            .onFailure { Log.w("Analytics", "event '$name' failed", it) }
    }

    // --- Typed helpers keep event names consistent across the app ---

    fun tabView(tab: String) = event("tab_view", mapOf("tab" to tab))

    fun downloadStarted(file: String, kind: String) =
        event("download_started", mapOf("file" to file, "kind" to kind))

    fun downloadCompleted(file: String) = event("download_completed", mapOf("file" to file))

    fun downloadFailed(file: String) = event("download_failed", mapOf("file" to file))

    fun installClicked(file: String) = event("install_clicked", mapOf("file" to file))

    fun updateBannerClicked() = event("update_banner_clicked")

    fun appShared() = event("app_shared")
}
