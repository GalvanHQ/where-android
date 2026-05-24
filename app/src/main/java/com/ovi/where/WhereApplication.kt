package com.ovi.where

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.ovi.where.core.constants.AppConstants
import com.ovi.where.data.remote.chat.ChatProcessLifecycleObserver
import com.ovi.where.data.sync.BackgroundSyncLifecycleObserver
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class WhereApplication : Application() {

    private val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Inject
    lateinit var chatProcessLifecycleObserver: ChatProcessLifecycleObserver

    @Inject
    lateinit var backgroundSyncLifecycleObserver: BackgroundSyncLifecycleObserver

    @Inject
    lateinit var notificationRepository: com.ovi.where.data.repository.NotificationRepository

    override fun onCreate() {
        super.onCreate()
        // Timber, Firebase, and Coil are initialized via App Startup (InitializationProvider).
        // This fallback ensures Timber is available if App Startup hasn't run yet.
        if (Timber.treeCount == 0 && BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        // Fetch and save FCM token on app start
        fetchAndSaveFcmToken()

        // Re-save the FCM token whenever auth state flips to "signed in".
        // Without this, fresh sign-ups never get their token persisted to
        // Firestore (the call above runs before currentUser is non-null,
        // and onNewToken only fires when the device's token actually
        // changes — which it doesn't on a re-login from the same install).
        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            if (auth.currentUser != null) {
                fetchAndSaveFcmToken()
                // Cross-device inbox starts replicating as soon as we know
                // the uid. Idempotent — calling startSync twice for the
                // same uid is a no-op.
                notificationRepository.startSync()
            } else {
                notificationRepository.stopSync()
            }
        }
        // Track online status using the process lifecycle
        ProcessLifecycleOwner.get().lifecycle.addObserver(OnlineStatusObserver())
        // Requirement 21.4, 21.5: Manage socket and Firestore listener on app foreground/background
        ProcessLifecycleOwner.get().lifecycle.addObserver(chatProcessLifecycleObserver)
        // Requirement 22.1: Schedule background sync when app is in background > 5 minutes
        ProcessLifecycleOwner.get().lifecycle.addObserver(backgroundSyncLifecycleObserver)

        // Best-effort prune of stale notification inbox rows. The repo's
        // 30-day retention keeps the table from growing unbounded across
        // long-lived installs without a recurring WorkManager job.
        appScope.launch {
            try {
                notificationRepository.prune()
            } catch (e: Exception) {
                Timber.w(e, "Failed to prune notification inbox")
            }
        }
        // Schedule a daily prune so long-resident installs (tablets, kiosk
        // mode) don't rely on cold starts to enforce retention.
        com.ovi.where.data.sync.NotificationInboxPruneWorker.schedule(this)
    }

    /**
     * Tracks whether the user is currently in foreground use vs idle.
     *
     * Goal: write to Firestore as little as possible while still keeping
     * the green dot in friends' UIs accurate.
     *
     * Three guardrails layered together kill the "every screen-off, every
     * screen-on, every shade peek" write storm the previous implementation
     * suffered from:
     *
     *   1. **No-op de-dupe**. We track the last-pushed value and skip the
     *      write when the desired state matches what's already on the
     *      server doc. Prevents redundant `true` → `true` writes during
     *      Activity recreations or notification glances that already saw
     *      a true write recently.
     *
     *   2. **Offline debounce**. `onStop` doesn't write `false` immediately.
     *      It schedules a coroutine that waits [OFFLINE_DEBOUNCE_MS] before
     *      writing. If the user comes back inside that window (most "the
     *      screen turned off for a few seconds" cases), the pending write
     *      is cancelled and we never touch Firestore. WhatsApp / Messenger
     *      use a similar grace period for the same reason.
     *
     *   3. **Heartbeat throttle**. `lastSeen` is bumped at most every
     *      [HEARTBEAT_THROTTLE_MS] while online, so a user who keeps the
     *      app foregrounded for hours doesn't generate a write every time
     *      the lifecycle recomposes.
     */
    inner class OnlineStatusObserver : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            // Cancel a pending offline write — user came back inside the
            // grace window. Either skip the online write entirely (state
            // already true on the server) or push it through.
            pendingOfflineJob?.cancel()
            pendingOfflineJob = null
            scheduleOnlineWrite()
        }

        override fun onStop(owner: LifecycleOwner) {
            scheduleOfflineWrite()
        }
    }

    /**
     * Scheduling helper for the foregrounded path. Honors the heartbeat
     * throttle so we don't write on every Activity recreation.
     */
    private fun scheduleOnlineWrite() {
        val now = System.currentTimeMillis()
        // Already known online and the last write is still fresh — skip
        // the round trip entirely. Friends' UIs already show the green
        // dot, and `lastSeen` will be re-bumped on the next foreground
        // entry after the throttle expires.
        if (lastPushedOnline == true && now - lastOnlineWriteAt < HEARTBEAT_THROTTLE_MS) {
            return
        }
        appScope.launch { writeOnlineStatus(true) }
    }

    /**
     * Scheduling helper for the backgrounded path. Defers the actual
     * write so transient backgrounding (screen off for a few seconds,
     * notification shade peek) doesn't generate a Firestore write.
     */
    private fun scheduleOfflineWrite() {
        // Already offline on the server — skip.
        if (lastPushedOnline == false) return
        pendingOfflineJob?.cancel()
        pendingOfflineJob = appScope.launch {
            delay(OFFLINE_DEBOUNCE_MS)
            writeOnlineStatus(false)
        }
    }

    private suspend fun writeOnlineStatus(isOnline: Boolean) {
        try {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            FirebaseFirestore.getInstance()
                .collection(AppConstants.FIRESTORE_COLLECTION_USERS)
                .document(uid)
                .update(
                    "isOnline", isOnline,
                    "lastSeen", System.currentTimeMillis()
                )
                .await()
            lastPushedOnline = isOnline
            if (isOnline) lastOnlineWriteAt = System.currentTimeMillis()
        } catch (e: Exception) {
            Timber.w(e, "Failed to update online status")
        }
    }

    @Volatile
    private var lastPushedOnline: Boolean? = null

    @Volatile
    private var lastOnlineWriteAt: Long = 0L

    @Volatile
    private var pendingOfflineJob: Job? = null

    private companion object {
        /**
         * How long we wait before writing `isOnline = false` after the
         * process backgrounds. Covers screen-off, notification shade,
         * brief tab-outs without flapping the friends' UIs.
         */
        private const val OFFLINE_DEBOUNCE_MS = 15_000L

        /**
         * Minimum time between consecutive `isOnline = true` writes
         * while the user is active. Friends still see "online" without
         * us paying for a write per Activity restart / cold app open.
         */
        private const val HEARTBEAT_THROTTLE_MS = 30_000L
    }

    private fun fetchAndSaveFcmToken() {
        appScope.launch {
            try {
                val token = FirebaseMessaging.getInstance().token.await()
                val uid = FirebaseAuth.getInstance().currentUser?.uid
                if (uid != null && token != null) {
                    FirebaseFirestore.getInstance()
                        .collection(AppConstants.FIRESTORE_COLLECTION_USERS)
                        .document(uid)
                        .update("fcmToken", token)
                        .await()
                    // FCM token saved successfully
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to fetch/save FCM token")
            }
        }
    }
}
