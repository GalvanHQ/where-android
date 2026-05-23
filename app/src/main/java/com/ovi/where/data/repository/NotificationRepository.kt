package com.ovi.where.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.ovi.where.core.notification.NotificationData
import com.ovi.where.core.notification.NotificationType
import com.ovi.where.data.local.dao.NotificationDao
import com.ovi.where.data.local.entity.NotificationEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-app notification inbox — hybrid Firestore single-doc aggregate + Room cache.
 *
 * **Source of truth**: the single document at
 * `users/{uid}/inbox/notifications`. Its `entries` map holds every
 * notification keyed by deterministic id.
 *
 * Why a single document?
 *  • **1 read per inbox open**, regardless of how many notifications
 *    are in there — a per-doc model would charge 50–200 reads per open.
 *  • Mirrors the existing friend-requests inbox pattern in this codebase
 *    (`users/{uid}/inbox/friendRequests`) — same shape, same access path.
 *  • Cross-device read state syncs through dotted-path field updates
 *    (`entries.${id}.isRead = true`) so devices don't fight over the
 *    whole-doc map.
 *
 * **Cache**: Room at `notifications` table.
 *  • Powers offline reads + the bell badge (no network required to
 *    render the inbox after first sync).
 *  • Mirrored from the Firestore listener — every snapshot rebuilds the
 *    Room cache to match the canonical map.
 *
 * Lifecycle: [startSync] is called by [com.ovi.where.WhereApplication]
 * when the user is authenticated, [stopSync] on sign-out.
 */
@Singleton
class NotificationRepository @Inject constructor(
    private val notificationDao: NotificationDao,
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth
) {

    private val syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var snapshotRegistration: ListenerRegistration? = null

    /** Newest-first stream of all notifications in the inbox. */
    fun observeAll(): Flow<List<NotificationEntity>> = notificationDao.observeAll()

    /** Reactive count of unread notifications — drives the bell badge. */
    fun observeUnreadCount(): Flow<Int> = notificationDao.observeUnreadCount()

    /**
     * Starts the Firestore snapshot listener for the current user.
     * Idempotent — calling it twice with the same uid is a no-op.
     * Switching uid (sign in / sign out / account swap) tears down the
     * old listener first.
     *
     * Each snapshot is a single document. We rebuild the Room cache from
     * its `entries` map: any entry not in the map gets deleted locally,
     * any entry whose hash differs gets upserted. This way the local
     * unread badge reflects cross-device reads without any extra logic.
     */
    fun startSync() {
        val uid = firebaseAuth.currentUser?.uid ?: run {
            Timber.i("NotificationRepository.startSync: no user, skipping")
            return
        }
        if (currentSyncUid == uid) return
        stopSync()
        currentSyncUid = uid

        snapshotRegistration = firestore
            .document(inboxPath(uid))
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Timber.w(err, "NotificationRepository: snapshot listener error")
                    return@addSnapshotListener
                }
                if (snap == null) return@addSnapshotListener

                @Suppress("UNCHECKED_CAST")
                val entriesMap = (snap.get("entries") as? Map<String, Map<String, Any?>>)
                    ?: emptyMap()

                val parsed = entriesMap.values.mapNotNull { entry ->
                    runCatching { entry.toEntity() }.getOrNull()
                }
                val freshIds = parsed.map { it.id }.toSet()

                syncScope.launch {
                    // Reconcile Room with the canonical map. We delete
                    // anything Room has that the doc doesn't, then upsert
                    // the rest — no migrations, no schema dance.
                    val localIds = notificationDao.observeAllIds()
                    for (localId in localIds) {
                        if (localId !in freshIds) notificationDao.delete(localId)
                    }
                    parsed.forEach { notificationDao.insert(it) }
                }
            }
    }

    /** Stops the snapshot listener and clears the per-uid bookkeeping. */
    fun stopSync() {
        snapshotRegistration?.remove()
        snapshotRegistration = null
        currentSyncUid = null
    }

    /**
     * Local-only fast path used by [com.ovi.where.data.remote.FcmMessagingService]
     * to surface an inbox row instantly when an FCM payload arrives — even
     * before the Firestore snapshot lands. Once Firestore catches up, the
     * deterministic id collides and the snapshot's value wins.
     *
     * We do NOT write back to Firestore here. Cloud Functions / the socket
     * server already did, and a client write would race the snapshot read.
     */
    suspend fun add(
        type: NotificationType,
        data: NotificationData,
        deepLinkRoute: String?,
        timestamp: Long = System.currentTimeMillis()
    ) {
        val targetId = data.conversationId
            ?: data.groupId
            ?: data.userId
            ?: data.targetId
            ?: timestamp.toString()
        val id = "${type.name}_${targetId}_$timestamp"
        notificationDao.insert(
            NotificationEntity(
                id = id,
                type = type.name,
                title = data.title,
                body = data.body,
                timestamp = timestamp,
                isRead = false,
                deepLinkRoute = deepLinkRoute,
                conversationId = data.conversationId,
                groupId = data.groupId,
                userId = data.userId,
                destinationName = data.destinationName
            )
        )
    }

    /**
     * Marks one entry as read.
     *
     * Cost: a single field update via dotted path — no transaction, no map
     * rewrite. Two devices flagging different entries simultaneously
     * succeed independently because the writes touch different fields.
     */
    suspend fun markAsRead(id: String) {
        notificationDao.markAsRead(id)
        val uid = firebaseAuth.currentUser?.uid ?: return
        try {
            firestore.document(inboxPath(uid)).update(
                mapOf(
                    "entries.$id.isRead" to true,
                    "entries.$id.readAt" to System.currentTimeMillis(),
                    "updatedAt" to System.currentTimeMillis()
                )
            ).await()
        } catch (e: Exception) {
            Timber.w(e, "markAsRead failed for $id")
        }
    }

    /**
     * Marks every unread entry as read in a single doc write. We compute
     * the dotted-path update map locally so the operation is one round trip
     * even for 100+ unread entries.
     */
    suspend fun markAllAsRead() {
        notificationDao.markAllAsRead()
        val uid = firebaseAuth.currentUser?.uid ?: return
        try {
            val ref = firestore.document(inboxPath(uid))
            val snap = ref.get().await()
            if (!snap.exists()) return
            @Suppress("UNCHECKED_CAST")
            val entries = (snap.get("entries") as? Map<String, Map<String, Any?>>) ?: return
            if (entries.isEmpty()) return

            val updates = mutableMapOf<String, Any>()
            val now = System.currentTimeMillis()
            entries.forEach { (entryId, entry) ->
                val isRead = entry["isRead"] as? Boolean ?: false
                if (!isRead) {
                    updates["entries.$entryId.isRead"] = true
                    updates["entries.$entryId.readAt"] = now
                }
            }
            if (updates.isEmpty()) return
            updates["updatedAt"] = now
            ref.update(updates).await()
        } catch (e: Exception) {
            Timber.w(e, "markAllAsRead failed")
        }
    }

    /** Removes a single entry from both Room and the inbox map. */
    suspend fun delete(id: String) {
        notificationDao.delete(id)
        val uid = firebaseAuth.currentUser?.uid ?: return
        try {
            firestore.document(inboxPath(uid)).update(
                mapOf(
                    "entries.$id" to FieldValue.delete(),
                    "updatedAt" to System.currentTimeMillis()
                )
            ).await()
        } catch (e: Exception) {
            Timber.w(e, "delete failed for $id")
        }
    }

    /**
     * Wipes every entry. Single doc write — `entries` reset to empty map.
     * Preserves the doc itself so the snapshot listener stays attached.
     */
    suspend fun clearAll() {
        notificationDao.clearAll()
        val uid = firebaseAuth.currentUser?.uid ?: return
        try {
            firestore.document(inboxPath(uid)).set(
                mapOf(
                    "entries" to emptyMap<String, Any>(),
                    "updatedAt" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            ).await()
        } catch (e: Exception) {
            Timber.w(e, "clearAll failed")
        }
    }

    /** Removes notifications older than [retentionMs] from the local cache. */
    suspend fun prune(retentionMs: Long = DEFAULT_RETENTION_MS) {
        notificationDao.pruneOlderThan(System.currentTimeMillis() - retentionMs)
    }

    private fun inboxPath(uid: String) = "users/$uid/inbox/notifications"

    /**
     * Maps a single map entry from the Firestore inbox doc into the Room
     * entity shape. Field names mirror what the Cloud Functions write so
     * this is a 1:1 translation.
     */
    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.toEntity(): NotificationEntity {
        val id = (this["id"] as? String).orEmpty()
        require(id.isNotBlank()) { "inbox entry missing id" }
        return NotificationEntity(
            id = id,
            type = (this["type"] as? String) ?: NotificationType.GENERAL.name,
            title = (this["title"] as? String).orEmpty(),
            body = (this["body"] as? String).orEmpty(),
            timestamp = (this["timestamp"] as? Number)?.toLong() ?: 0L,
            isRead = (this["isRead"] as? Boolean) ?: false,
            deepLinkRoute = this["deepLinkRoute"] as? String,
            conversationId = this["conversationId"] as? String,
            groupId = this["groupId"] as? String,
            userId = this["userId"] as? String,
            destinationName = this["destinationName"] as? String
        )
    }

    @Volatile
    private var currentSyncUid: String? = null

    companion object {
        /** 30 days. Used by Room prune; server-side prune mirrors this. */
        const val DEFAULT_RETENTION_MS: Long = 30L * 24 * 60 * 60 * 1000
    }
}
