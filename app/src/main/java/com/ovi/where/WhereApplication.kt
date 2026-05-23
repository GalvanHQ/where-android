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
import kotlinx.coroutines.SupervisorJob
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
     * Sets `isOnline = true` when the app foregrounds and `isOnline = false` when
     * it fully backgrounds (all Activities stopped). Uses `onStart`/`onStop` on the
     * *process* lifecycle so the state is accurate even across Activity recreations.
     */
    inner class OnlineStatusObserver : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) = setOnlineStatus(true)
        override fun onStop(owner: LifecycleOwner)  = setOnlineStatus(false)
    }

    private fun setOnlineStatus(isOnline: Boolean) {
        appScope.launch {
            try {
                val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
                FirebaseFirestore.getInstance()
                    .collection(AppConstants.FIRESTORE_COLLECTION_USERS)
                    .document(uid)
                    .update(
                        "isOnline", isOnline,
                        "lastSeen", System.currentTimeMillis()
                    )
                    .await()
            } catch (e: Exception) {
                Timber.w(e, "Failed to update online status")
            }
        }
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
