package com.ovi.where.startup

import android.content.Context
import androidx.startup.Initializer
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.ovi.where.core.crash.WhereCrashHandler
import timber.log.Timber

/**
 * App Startup [Initializer] for Firebase.
 *
 * Initializes Firebase, sets up Crashlytics, and installs the custom
 * [WhereCrashHandler] for attaching user ID and screen route on crashes.
 *
 * Has no dependencies — this is the first initializer to run.
 */
class FirebaseInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        try {
            // Firebase is auto-initialized via google-services plugin,
            // but we ensure it's ready and configure Crashlytics
            FirebaseApp.initializeApp(context)

            // Enable Crashlytics collection (respects debug/release config)
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)

            // Install custom uncaught exception handler
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler(WhereCrashHandler(defaultHandler))
        } catch (e: Exception) {
            // Firebase/Crashlytics unavailable — log and continue
            Timber.w(e, "FirebaseInitializer: Failed to initialize Firebase/Crashlytics")
        }
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        // No dependencies — Firebase initializes first
        return emptyList()
    }
}
