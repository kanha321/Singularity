package com.kanhaji.singularity.service.bridge

import android.app.Activity
import android.app.Application
import android.os.Bundle

object AppLifecycleTracker : Application.ActivityLifecycleCallbacks {
    @Volatile
    var isForeground: Boolean = false
        private set

    override fun onActivityResumed(activity: Activity) {
        if (activity.javaClass.simpleName == "MainActivity") {
            isForeground = true
        }
    }

    override fun onActivityPaused(activity: Activity) {
        if (activity.javaClass.simpleName == "MainActivity") {
            isForeground = false
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}

