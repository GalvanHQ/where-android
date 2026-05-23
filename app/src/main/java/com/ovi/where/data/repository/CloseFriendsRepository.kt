package com.ovi.where.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the "close friends" set — a per-user list of friend uids whose
 * notifications bypass quiet hours and post on a higher-importance channel.
 *
 * Stored as a single doc at `users/{uid}/preferences/closeFriends` with a
 * map field `members: Map<uid, Long>` (timestamp added). The map shape
 * makes membership checks O(1) on the client + lets us add metadata later
 * (per-friend overrides, snooze, etc.) without a schema change.
 *
 * Cross-device: written through Firestore, no Room cache needed since the
 * set is small (typical user has <10 close friends) and read on demand
 * by [NotificationHelper] when a chat push lands.
 */
@Singleton
class CloseFriendsRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {

    /**
     * Snapshot listener over the close-friends doc. Emits an empty set when
     * unauthenticated or the doc doesn't exist yet.
     */
    fun observe(): Flow<Set<String>> = callbackFlow {
        val uid = firebaseAuth.currentUser?.uid
        if (uid == null) {
            trySend(emptySet())
            close()
            return@callbackFlow
        }
        val ref = firestore
            .collection("users").document(uid)
            .collection("preferences").document(DOC_ID)
        val reg = ref.addSnapshotListener { snap, _ ->
            @Suppress("UNCHECKED_CAST")
            val members = (snap?.get("members") as? Map<String, *>)?.keys?.toSet().orEmpty()
            trySend(members)
        }
        awaitClose { reg.remove() }
    }

    /**
     * Suspend-style read. Returns the current set or empty when missing.
     * Used by [com.ovi.where.core.notification.NotificationHelper] on the
     * push hot-path so we don't pay the snapshot-listener allocation cost
     * for one-shot membership checks.
     */
    suspend fun isCloseFriend(friendUid: String): Boolean {
        if (friendUid.isBlank()) return false
        val uid = firebaseAuth.currentUser?.uid ?: return false
        return try {
            val doc = firestore
                .collection("users").document(uid)
                .collection("preferences").document(DOC_ID)
                .get()
                .await()
            @Suppress("UNCHECKED_CAST")
            val members = (doc.get("members") as? Map<String, *>)?.keys.orEmpty()
            friendUid in members
        } catch (_: Exception) {
            false
        }
    }

    suspend fun add(friendUid: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        firestore
            .collection("users").document(uid)
            .collection("preferences").document(DOC_ID)
            .set(
                mapOf("members" to mapOf(friendUid to System.currentTimeMillis())),
                SetOptions.merge()
            )
            .await()
    }

    suspend fun remove(friendUid: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        firestore
            .collection("users").document(uid)
            .collection("preferences").document(DOC_ID)
            .update("members.$friendUid", com.google.firebase.firestore.FieldValue.delete())
            .await()
    }

    /** Suspends and returns the current set. */
    suspend fun current(): Set<String> = observe().first()

    companion object {
        private const val DOC_ID = "closeFriends"
    }
}
