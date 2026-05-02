package com.sheshield.ai

import android.app.Application
import com.sheshield.ai.utils.AppLifecycleTracker

class SheShieldApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(AppLifecycleTracker)
    }
}
