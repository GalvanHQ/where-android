package com.ovi.where.core.crash

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.security.MessageDigest

/**
 * Custom [Thread.UncaughtExceptionHandler] that attaches the current user ID (anonymized)
 * and the active screen route as custom keys to Firebase Crashlytics before delegating
 * to the default handler.
 *
 * All Crashlytics calls are guarded with try/catch so the app doesn't crash
 * if Crashlytics is unavailable or fails to initialize.
 */
class WhereCrashHandler(
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val crashlytics = FirebaseCrashlytics.getInstance()

            // Attach anonymized user ID
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            val anonymizedId = if (uid != null) anonymize(uid) else "anonymous"
            crashlytics.setCustomKey("user_id", anonymizedId)

            // Attach active screen route
            val activeRoute = ActiveScreenTracker.getActiveRoute()
            crashlytics.setCustomKey("active_screen", activeRoute)

            // Record the exception explicitly so keys are attached
            crashlytics.recordException(throwable)
        } catch (_: Exception) {
            // Crashlytics unavailable — proceed to default handler without interference
        }

        // Delegate to the default handler (which will terminate the process)
        defaultHandler?.uncaughtException(thread, throwable)
    }

    /**
     * Anonymizes a user ID by hashing it with SHA-256 and taking the first 16 hex characters.
     */
    private fun anonymize(uid: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(uid.toByteArray())
            hash.take(8).joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            "hash_error"
        }
    }
}
