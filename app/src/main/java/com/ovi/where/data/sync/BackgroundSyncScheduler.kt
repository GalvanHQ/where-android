package com.ovi.where.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Manages scheduling and cancellation of the periodic background sync worker.
 *
 * Scheduling rules:
 * - Periodic interval: 15 minutes (Req 22.1)
 * - Network connectivity constraint required (Req 22.5)
 * - Exponential backoff: 1 minute initial, 30 minute max (Req 22.3)
 * - Scheduled when app goes to background for > 5 minutes
 * - Cancelled on auth error or when user re-enters foreground
 *
 * Requirements: 22.1, 22.3, 22.5, 22.7
 */
object BackgroundSyncScheduler {

    /** Minimum interval for periodic sync (Req 22.1) */
    private const val SYNC_INTERVAL_MINUTES = 15L

    /** Initial backoff delay for retries (Req 22.3) */
    private const val BACKOFF_INITIAL_MINUTES = 1L

    /**
     * Schedules the periodic background sync worker.
     * Called when the app has been in background for > 5 minutes.
     *
     * Uses KEEP policy so that if the work is already scheduled, it won't be replaced.
     * This prevents resetting the periodic timer on repeated calls.
     */
    fun schedule(context: Context) {
        Timber.d("BackgroundSyncScheduler: scheduling periodic sync (${SYNC_INTERVAL_MINUTES}min interval)")

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED) // Req 22.5
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<BackgroundSyncWorker>(
            SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                BACKOFF_INITIAL_MINUTES,
                TimeUnit.MINUTES
            )
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                BackgroundSyncWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )

        Timber.d("BackgroundSyncScheduler: periodic sync scheduled successfully")
    }

    /**
     * Cancels the periodic background sync worker.
     * Called when:
     * - App returns to foreground
     * - Auth error is received (Req 22.7)
     * - User signs out
     */
    fun cancel(context: Context) {
        Timber.d("BackgroundSyncScheduler: cancelling periodic sync")
        WorkManager.getInstance(context)
            .cancelUniqueWork(BackgroundSyncWorker.WORK_NAME)
    }
}
