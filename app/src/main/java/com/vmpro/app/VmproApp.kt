package com.vmpro.app

import android.app.Application
import android.content.Context
import com.vmpro.app.analytics.Analytics

class VmproApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Analytics.init(this)

        val prefs = getSharedPreferences("vmpro_prefs", Context.MODE_PRIVATE)
        val returning = prefs.getBoolean("opened_before", false)
        if (!returning) prefs.edit().putBoolean("opened_before", true).apply()
        Analytics.appOpen(isReturning = returning)
    }
}
