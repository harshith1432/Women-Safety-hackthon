package com.sheshield.ai.utils

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.util.concurrent.atomic.AtomicInteger

object AppLifecycleTracker : Application.ActivityLifecycleCallbacks {

    private val activeActivities = AtomicInteger(0)

    fun isAppInForeground(): Boolean = activeActivities.get() > 0
    fun isAppInBackground(): Boolean = !isAppInForeground()

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        // Not used
    }

    override fun onActivityStarted(activity: Activity) {
        activeActivities.incrementAndGet()
    }

    override fun onActivityResumed(activity: Activity) {
        // Not used
    }

    override fun onActivityPaused(activity: Activity) {
        // Not used
    }

    override fun onActivityStopped(activity: Activity) {
        activeActivities.decrementAndGet()
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        // Not used
    }

    override fun onActivityDestroyed(activity: Activity) {
        // Not used
    }
}
