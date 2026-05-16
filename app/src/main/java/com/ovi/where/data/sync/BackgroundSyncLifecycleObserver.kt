package com.ovi.where.data.sync

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Observes the process lifecycle to schedule/cancel the periodic background sync worker.
 *
 * Scheduling logic:
 * - When the app goes to background: starts a 5-minute timer
 * - After 5 minutes in background: schedules the periodic sync worker (Req 22.1)
 * - When the app returns to foreground: cancels the timer and the periodic sync worker
 *
 * This ensures the worker is only active when the app has been in background for > 5 minutes,
 * avoiding unnecessary work for brief app switches.
 *
 * Requirements: 22.1, 22.4
 */
@Singleton
class BackgroundSyncLifecycleObserver @Inject constructor(
    @ApplicationContext private val context: Context
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var scheduleJob: Job? = null

    companion object {
        /** Delay before scheduling the periodic sync (Req 22.1: background > 5 minutes) */
        private const val BACKGROUND_THRESHOLD_MS = 5 * 60 * 1000L // 5 minutes
    }

    /**
     * Called when the app returns to foreground.
     * Cancels any pending schedule timer and the periodic sync worker.
     */
    override fun onStart(owner: LifecycleOwner) {
        Timber.d("BackgroundSyncLifecycleObserver: app returning to foreground, cancelling sync schedule")

        // Cancel the 5-minute timer if it hasn't fired yet
        scheduleJob?.cancel()
        scheduleJob = null

        // Cancel the periodic sync worker (no longer needed in foreground)
        BackgroundSyncScheduler.cancel(context)
    }

    /**
     * Called when the app goes to background (all activities stopped).
     * Starts a 5-minute timer; if the app remains in background, schedules the periodic sync.
     */
    override fun onStop(owner: LifecycleOwner) {
        Timber.d("BackgroundSyncLifecycleObserver: app going to background, starting 5-minute timer")

        scheduleJob = scope.launch {
            delay(BACKGROUND_THRESHOLD_MS)
            Timber.d("BackgroundSyncLifecycleObserver: 5 minutes elapsed, scheduling periodic sync")
            BackgroundSyncScheduler.schedule(context)
        }
    }
}
