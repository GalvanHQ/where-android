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

                // Identify legacy non-important entries and queue them for
                // server-side purge. Older builds persisted every type to
                // this doc; we now only mirror "important" types client-
                // side, but the server doc may still hold rows from before
                // the cutover (chat messages, mentions, member join/leave,
                // live-location start/stop, meetup cleared, location
                // updates, GENERAL). Purge them once on a server snapshot
                // so every account's inbox doc converges to important-only.
                //
                // Type normalization: server stores the FCM lowercase
                // string ("friend_request"), client enum is UPPER_SNAKE_CASE
                // ("FRIEND_REQUEST"). [resolveStoredType] handles both
                // shapes so we don't accidentally classify a valid
                // friend-request row as legacy and purge it server-side.
                val legacyEntryIdsToPurge = entriesMap.values.mapNotNull { entry ->
                    val parsedType = resolveStoredType(entry["type"] as? String)
                    val id = entry["id"] as? String
                    if (!parsedType.isInboxImportant && !id.isNullOrBlank()) id else null
                }

                val parsed = entriesMap.values.mapNotNull { entry ->
                    runCatching { entry.toEntity() }.getOrNull()
                }
                    // Strip non-important entries from what we mirror into
                    // Room. Only action-required and high-signal events
                    // belong in the in-app inbox (see
                    // [NotificationType.isInboxImportant]). This filter is
                    // also a defensive backstop for legacy entries that may
                    // already exist on the server doc — older builds did
                    // persist them and we don't want them resurrecting
                    // after an account swap or reinstall.
                    .filter { resolveStoredType(it.type).isInboxImportant }
                val freshIds = parsed.map { it.id }.toSet()

                // ── Cache-snapshot guard ──
                // Firestore emits a cache snapshot before the server one when
                // the doc has been seen before. Cache snapshots are not
                // authoritative about *deletions* — they reflect whatever
                // shred of state the local persistence layer happens to hold,
                // which on cold starts can be empty or partial.
                //
                // If we naively reconciled "anything in Room not in the snap
                // gets deleted", a single cache miss would wipe the whole
                // inbox until the server snap arrived a few hundred ms later.
                // That's the "notification badge disappeared briefly" bug.
                //
                // Rule: only run destructive reconcile (delete-not-present)
                // on server snapshots. Cache snapshots can still fill the
                // cache (insert), but never erase.
                val isFromCache = snap.metadata.isFromCache

                syncScope.launch {
                    // Diff against current Room state so we only upsert
                    // rows that actually changed. Without this, every
                    // snapshot (cache + server) would re-INSERT every
                    // entry, causing redundant Flow emissions on the bound
                    // observers (uiState in NotificationsViewModel + the
                    // bell badge in MainScaffold). Inbox is capped at 200
                    // entries so the snapshot read is constant-time.
                    val current = notificationDao.snapshotAll().associateBy { it.id }

                    if (!isFromCache) {
                        // Authoritative server snapshot — Room mirrors the doc.
                        for (localId in current.keys) {
                            if (localId !in freshIds) notificationDao.delete(localId)
                        }
                        // One-shot purge of legacy non-important entries
                        // on the canonical doc. Runs only after the server
                        // confirms what's actually there.
                        if (legacyEntryIdsToPurge.isNotEmpty()) {
                            purgeLegacyEntries(uid, legacyEntryIdsToPurge)
                        }
                    }
                    // Upsert only the rows that actually changed. Cache
                    // snapshots are valid for *adding* known-good entries
                    // to Room, just not for proving anything's gone — but
                    // even then we skip rows that are byte-equal to the
                    // current cache to avoid the no-op Room write.
                    for (entry in parsed) {
                        if (current[entry.id] != entry) notificationDao.insert(entry)
                    }
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
     * One-shot purge of legacy non-important entries from the canonical
     * inbox doc. Builds a single dotted-path update that deletes every
     * provided id at once, so the cost is one round trip regardless of
     * how many rows we're scrubbing.
     *
     * Called from the snapshot listener after a server snapshot reports
     * any rows of types that aren't [NotificationType.isInboxImportant].
     * Once the server prunes them, subsequent snapshots won't trigger
     * this path again (the input list is empty), so this is naturally
     * self-healing.
     *
     * Safe to call when the user signs out and back in: it gates on the
     * uid the snapshot was for, so a stale callback never targets the
     * wrong account.
     */
    private suspend fun purgeLegacyEntries(uid: String, ids: List<String>) {
        if (ids.isEmpty()) return
        try {
            val updates = mutableMapOf<String, Any>()
            ids.forEach { id ->
                updates["entries.$id"] = FieldValue.delete()
            }
            updates["updatedAt"] = System.currentTimeMillis()
            firestore.document(inboxPath(uid)).update(updates).await()
            // Mirror to Room so the bell unread count drops immediately
            // even before the next snapshot arrives.
            ids.forEach { notificationDao.delete(it) }
            Timber.i("Purged ${ids.size} legacy non-important entries from inbox doc")
        } catch (e: Exception) {
            Timber.w(e, "Failed to purge legacy non-important entries")
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
        // Normalize type to the enum name so Room rows are consistent
        // regardless of which producer wrote the source entry. The Cloud
        // Function writes lowercase FCM strings ("friend_request"); the
        // local FCM fast-path writes UPPER_SNAKE_CASE ("FRIEND_REQUEST").
        // Storing both shapes side-by-side made `NotificationType.valueOf`
        // throw on the lowercase one, silently classifying real friend
        // requests as GENERAL and routing them to the legacy purge path.
        val storedType = resolveStoredType(this["type"] as? String).name
        return NotificationEntity(
            id = id,
            type = storedType,
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

    /**
     * Maps a stored `type` string to a [NotificationType], handling both
     * representations the doc can hold:
     *   • FCM lowercase ("friend_request") — Cloud Functions producer.
     *   • Enum UPPER_SNAKE_CASE ("FRIEND_REQUEST") — local FCM fast path
     *     and any pre-cutover client write.
     *
     * Defaults to [NotificationType.GENERAL] when neither shape resolves,
     * which routes legacy / unknown rows into the purge path correctly.
     */
    private fun resolveStoredType(raw: String?): NotificationType {
        if (raw.isNullOrBlank()) return NotificationType.GENERAL
        // Try the enum-name path first — faster and matches the local
        // FCM service's writes.
        runCatching { return NotificationType.valueOf(raw) }
        // Fall back to the FCM-string mapper, which handles
        // "friend_request" / "friend_accepted" / etc.
        return NotificationType.fromFcmType(raw)
    }

    @Volatile
    private var currentSyncUid: String? = null

    companion object {
        /** 30 days. Used by Room prune; server-side prune mirrors this. */
        const val DEFAULT_RETENTION_MS: Long = 30L * 24 * 60 * 60 * 1000
    }
}
