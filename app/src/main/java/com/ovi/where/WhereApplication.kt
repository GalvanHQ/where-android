package com.ovi.where

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.ovi.where.core.constants.AppConstants
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber

@HiltAndroidApp
class WhereApplication : Application() {

    private val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        // Timber, Firebase, and Coil are initialized via App Startup (InitializationProvider).
        // This fallback ensures Timber is available if App Startup hasn't run yet.
        if (Timber.treeCount == 0 && BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        // Fetch and save FCM token on app start
        fetchAndSaveFcmToken()
        // Track online status using the process lifecycle
        ProcessLifecycleOwner.get().lifecycle.addObserver(OnlineStatusObserver())
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
