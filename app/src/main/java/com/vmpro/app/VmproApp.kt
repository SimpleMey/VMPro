package com.vmpro.app

import android.app.Application
import com.vmpro.app.analytics.Analytics

class VmproApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Analytics.init(this)
        Analytics.appOpen()
    }
}
