package com.ovi.where.core.crash

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import timber.log.Timber

/**
 * A [Timber.Tree] that forwards error-level and WTF-level logs to Firebase Crashlytics.
 *
 * All Crashlytics calls are guarded with try/catch so the app doesn't crash
 * if Crashlytics is unavailable or fails to initialize.
 */
class CrashReportingTree : Timber.Tree() {

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // Only forward error and WTF level logs to Crashlytics
        if (priority < Log.ERROR) return

        try {
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.log("${tag ?: "NoTag"}: $message")
            if (t != null) {
                crashlytics.recordException(t)
            }
        } catch (_: Exception) {
            // Crashlytics unavailable or not initialized — silently ignore
        }
    }
}
