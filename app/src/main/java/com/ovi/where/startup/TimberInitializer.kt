package com.ovi.where.startup

import android.content.Context
import androidx.startup.Initializer
import com.ovi.where.BuildConfig
import com.ovi.where.core.crash.CrashReportingTree
import timber.log.Timber

/**
 * App Startup [Initializer] for Timber logging.
 *
 * Plants the appropriate Timber tree based on build type:
 * - Debug: [Timber.DebugTree] for logcat output
 * - Release: [CrashReportingTree] for forwarding errors to Crashlytics
 *
 * Depends on [FirebaseInitializer] so that Crashlytics is available
 * when the [CrashReportingTree] is planted.
 */
class TimberInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(CrashReportingTree())
        }
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        // Timber depends on Firebase so CrashReportingTree can use Crashlytics
        return listOf(FirebaseInitializer::class.java)
    }
}
