package com.ovi.where.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.ovi.where.core.common.Resource
import com.ovi.where.core.constants.AppConstants
import com.ovi.where.data.local.CacheStalenessChecker
import com.ovi.where.data.local.dao.ConversationDao
import com.ovi.where.data.local.entity.ConversationEntity
import com.ovi.where.data.local.entity.MessageEntity
import com.ovi.where.data.local.entity.toDomain
import com.ovi.where.data.local.entity.toEntity
import com.ovi.where.data.remote.chat.ChatApiClient
import com.ovi.where.data.remote.chat.ConversationDto
import com.ovi.where.data.remote.chat.CreateDirectConversationRequest
import com.ovi.where.data.remote.chat.CreateGroupConversationRequest
import com.ovi.where.data.util.networkBoundResource
import com.ovi.where.domain.model.Conversation
import com.ovi.where.domain.model.ConversationType
import com.ovi.where.domain.repository.ConversationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import com.ovi.where.data.util.Resource as DataResource

/**
 * ConversationRepository implementation using Room as the single source of truth.
 *
 * Architecture:
 * - UI observes Room database flows only (Requirement 12.1)
 * - Firestore listener is scoped to metadata fields only (Requirement 12.2)
 * - No Firestore listener on messages subcollection (Requirement 12.2)
 * - On Firestore snapshot: write to Room, UI flow emits within 500ms (Requirement 12.3)
 * - Messages are never read from Firestore (Requirement 12.4)
 * - On foreground: sync unread counts via single REST call with 10s timeout (Requirement 12.5)
 * - On sync failure/timeout: retain Room-cached counts, emit recoverable error (Requirement 12.6)
 * - On first launch (no Room records): fetch from REST before Firestore listener (Requirement 12.7)
 */
@Singleton
class ConversationRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val conversationDao: ConversationDao,
    private val messageDao: com.ovi.where.data.local.dao.MessageDao,
    private val cacheStalenessChecker: CacheStalenessChecker
) : ConversationRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var firestoreListener: ListenerRegistration? = null

    /**
     * Guards batch fetch execution to ensure at most one batch query set per foreground sync cycle.
     * Reset to false when the app returns to foreground.
     */
    @Volatile
    private var batchFetchExecutedThisCycle: Boolean = false

    private val currentUid: String?
        get() = firebaseAuth.currentUser?.uid

    private suspend fun getIdToken(): String {
        return firebaseAuth.currentUser?.getIdToken(false)?.await()?.token ?: ""
    }

    /**
     * Exposes conversation list via Room database flow only.
     * Room is the single source of truth for the UI (Requirement 12.1).
     *
     * The Firestore listener runs in the background and writes updates to Room,
     * which then triggers this flow to emit updated data to the UI.
     */
    override fun observeConversations(): Flow<List<Conversation>> {
        startFirestoreListenerIfNeeded()
        return conversationDao.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Observes a single conversation from Room database.
     * Room is the single source of truth (Requirement 12.1).
     */
    override fun observeConversation(conversationId: String): Flow<Conversation?> {
        startFirestoreListenerIfNeeded()
        return conversationDao.observeById(conversationId).map { entity ->
            entity?.toDomain()
        }
    }

    override suspend fun getOrCreateDirectConversation(otherUserId: String): Resource<Conversation> {
        return try {
            val token = getIdToken()
            val dto = ChatApiClient.apiService.getOrCreateDirectConversation(
                "Bearer $token",
                CreateDirectConversationRequest(otherUserId)
            )
            val conversation = dto.toDomain()
            // Persist to Room so it appears in the conversation list immediately
            conversationDao.upsertAll(listOf(conversation.toEntity()))
            Resource.Success(conversation)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to get or create conversation")
        }
    }

    override suspend fun createGroupConversation(
        groupId: String, name: String, memberIds: List<String>
    ): Resource<Conversation> {
        return try {
            val token = getIdToken()
            val dto = ChatApiClient.apiService.createGroupConversation(
                "Bearer $token",
                CreateGroupConversationRequest(groupId, name, memberIds)
            )
            val conversation = dto.toDomain()
            // Persist to Room so it appears in the conversation list immediately
            conversationDao.upsertAll(listOf(conversation.toEntity()))
            Resource.Success(conversation)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to create group conversation")
        }
    }

    override suspend fun markAsRead(conversationId: String, userId: String): Resource<Unit> {
        return try {
            val token = getIdToken()
            ChatApiClient.apiService.markAsRead("Bearer $token", conversationId)
            // Update Room immediately for responsive UI
            conversationDao.updateUnreadCount(conversationId, 0)
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to mark as read")
        }
    }

    override suspend fun updateLastMessage(
        conversationId: String, text: String, senderId: String
    ): Resource<Unit> = Resource.Success(Unit) // Updated server-side via message save

    /**
     * Syncs unread counts from the server via a single REST API call (Requirement 12.5).
     * Completes within a timeout of 10 seconds.
     *
     * On failure/timeout: retains existing Room-cached unread counts and emits
     * a recoverable error without blocking the conversation list UI (Requirement 12.6).
     */
    override suspend fun syncUnreadCounts(): Resource<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getIdToken()
            val unreadCounts = withTimeout(FOREGROUND_SYNC_TIMEOUT_MS) {
                ChatApiClient.apiService.getUnreadCounts("Bearer $token")
            }
            // Update each conversation's unread count in Room
            for (dto in unreadCounts) {
                conversationDao.updateUnreadCount(dto.conversationId, dto.unreadCount)
            }
            Resource.Success(Unit)
        } catch (e: Exception) {
            // On failure/timeout: retain Room-cached counts, emit recoverable error (Requirement 12.6)
            Resource.Error(e.message ?: "Failed to sync unread counts")
        }
    }

    /**
     * Fetches the initial conversation list from REST and persists to Room
     * before the Firestore listener starts incremental updates (Requirement 12.7).
     *
     * Only fetches if Room has no conversation records (first launch scenario).
     */
    override suspend fun fetchInitialConversationsIfNeeded(): Resource<Unit> = withContext(Dispatchers.IO) {
        try {
            val existingConversations = conversationDao.getAll()
            if (existingConversations.isNotEmpty()) {
                // Room already has data, no need to fetch initial list
                return@withContext Resource.Success(Unit)
            }

            val token = getIdToken()
            val conversations = ChatApiClient.apiService.getConversations("Bearer $token")
            val uid = currentUid ?: return@withContext Resource.Error("User not authenticated")

            val entities = conversations.map { dto ->
                ConversationEntity(
                    id = dto.id,
                    name = dto.name,
                    type = if (dto.type == "group") ConversationType.GROUP.name
                           else ConversationType.DIRECT.name,
                    photoUrl = dto.photoUrl,
                    groupId = dto.groupId,
                    lastMessageText = dto.lastMessageText,
                    lastMessageTimestamp = dto.lastMessageTimestamp,
                    lastMessageSenderId = dto.lastMessageSenderId,
                    unreadCount = dto.unreadCount,
                    memberIdsJson = serializeMemberIds(dto.participantIds),
                    lastSyncTimestamp = System.currentTimeMillis()
                )
            }

            // Persist to Room before Firestore listener starts.
            // Preserve existing mutedBy/pinnedBy since the REST DTO doesn't carry them.
            val mergedEntities = entities.map { incoming ->
                val existing = conversationDao.getById(incoming.id)
                if (existing != null) {
                    incoming.copy(
                        mutedByJson = existing.mutedByJson,
                        pinnedByJson = existing.pinnedByJson
                    )
                } else incoming
            }
            conversationDao.upsertAll(mergedEntities)
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to fetch initial conversations")
        }
    }

    /**
     * Deletes a conversation from the local Room database.
     * The conversation will reappear on next Firestore sync if it still exists server-side.
     */
    override suspend fun deleteConversation(conversationId: String): Resource<Unit> {
        return try {
            conversationDao.deleteById(conversationId)
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to delete conversation")
        }
    }

    /**
     * Starts the Firestore snapshot listener if not already running.
     *
     * The listener is scoped to conversation metadata fields only (Requirement 12.2):
     * - name, participantIds, photoUrl, type, groupId
     * - lastMessageText, lastMessageSenderId, lastMessageTimestamp
     * - unreadCounts
     *
     * No listener is opened on the messages subcollection (Requirement 12.2).
     * No listener is opened for presence or typing state (Requirement 6.2) —
     * these ephemeral states are handled exclusively via Socket.IO through ChatSocketIoClient.
     *
     * On snapshot: writes to Room so the UI flow emits within 500ms (Requirement 12.3).
     */
    private fun startFirestoreListenerIfNeeded() {
        if (firestoreListener != null) return
        val uid = currentUid ?: return

        firestoreListener = firestore.collection(AppConstants.FIRESTORE_COLLECTION_CONVERSATIONS)
            .whereArrayContains("participantIds", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val entities = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        // Filter out conversations soft-deleted by the current user (Req 1.7, 2.7)
                        val deletedBy = (doc.get("deletedBy") as? List<*>)
                            ?.filterIsInstance<String>() ?: emptyList()
                        if (uid in deletedBy) return@mapNotNull null

                        // Filter out conversations archived by the current user (Req 1.6, 2.6)
                        val archivedBy = (doc.get("archivedBy") as? List<*>)
                            ?.filterIsInstance<String>() ?: emptyList()
                        if (uid in archivedBy) return@mapNotNull null

                        val unreadMap = doc.get("unreadCounts") as? Map<*, *>
                        val myUnread = (unreadMap?.get(uid) as? Long)?.toInt() ?: 0
                        val participantIds = (doc.get("participantIds") as? List<*>)
                            ?.filterIsInstance<String>() ?: emptyList()
                        val docUpdateTime = doc.getDate("updateTime")?.time
                            ?: doc.getLong("lastMessageTimestamp")
                            ?: System.currentTimeMillis()

                        ConversationEntity(
                            id = doc.id,
                            name = doc.getString("name") ?: "",
                            type = if (doc.getString("type") == "group") ConversationType.GROUP.name
                                   else ConversationType.DIRECT.name,
                            photoUrl = doc.getString("photoUrl"),
                            groupId = doc.getString("groupId"),
                            lastMessageText = doc.getString("lastMessageText") ?: "",
                            lastMessageTimestamp = doc.getLong("lastMessageTimestamp") ?: 0L,
                            lastMessageSenderId = doc.getString("lastMessageSenderId") ?: "",
                            unreadCount = myUnread,
                            memberIdsJson = serializeMemberIds(participantIds),
                            mutedByJson = serializeMemberIds(
                                (doc.get("mutedBy") as? List<*>)
                                    ?.filterIsInstance<String>() ?: emptyList()
                            ),
                            pinnedByJson = serializeMemberIds(
                                (doc.get("pinnedBy") as? List<*>)
                                    ?.filterIsInstance<String>() ?: emptyList()
                            ),
                            lastSyncTimestamp = System.currentTimeMillis(),
                            documentUpdateTime = docUpdateTime,
                            participantNamesJson = serializeFirestoreMap(
                                doc.get("participantNames") as? Map<*, *>
                            ),
                            participantPhotosJson = serializeFirestoreMap(
                                doc.get("participantPhotos") as? Map<*, *>
                            ),
                            themeColor = doc.getString("themeColor"),
                            emojiShortcut = doc.getString("emojiShortcut"),
                            nicknamesJson = serializeFirestoreMap(
                                doc.get("nicknames") as? Map<*, *>
                            )
                        )
                    } catch (_: Exception) { null }
                } ?: emptyList()

                // Write to Room immediately so UI flow emits within 500ms (Requirement 12.3)
                repositoryScope.launch {
                    // Smart upsert: preserve locally-set customization fields
                    // (themeColor, emojiShortcut, nicknamesJson, photoUrl) when Firestore
                    // sends null for them (race condition with local writes).
                    val mergedEntities = entities.map { incoming ->
                        val existing = conversationDao.getById(incoming.id)
                        if (existing != null) {
                            incoming.copy(
                                themeColor = incoming.themeColor ?: existing.themeColor,
                                emojiShortcut = incoming.emojiShortcut ?: existing.emojiShortcut,
                                nicknamesJson = incoming.nicknamesJson ?: existing.nicknamesJson,
                                photoUrl = incoming.photoUrl ?: existing.photoUrl
                            )
                        } else {
                            incoming
                        }
                    }
                    conversationDao.upsertAll(mergedEntities)

                    // Parse recentMessages from each conversation doc and upsert to messages table.
                    // This gives the client messages for free (0 extra Firestore reads).
                    snapshot?.documents?.forEach { doc ->
                        try {
                            val recentMessages = doc.get("recentMessages") as? List<*> ?: return@forEach
                            val convId = doc.id
                            val messageEntities = recentMessages.mapNotNull { raw ->
                                val map = raw as? Map<*, *> ?: return@mapNotNull null
                                parseRecentMessageToEntity(convId, map)
                            }
                            if (messageEntities.isNotEmpty()) {
                                // Field-level merge: never overwrite richer local data with
                                // incomplete Firestore embedded messages. Only insert truly new
                                // messages; for existing ones, merge specific fields.
                                for (entity in messageEntities) {
                                    val existing = messageDao.getById(entity.id)
                                    if (existing == null) {
                                        // Truly new message — insert
                                        messageDao.insert(entity)
                                    } else {
                                        // Existing message — fill in missing imageUrl
                                        if (existing.imageUrl.isNullOrBlank() && !entity.imageUrl.isNullOrBlank()) {
                                            messageDao.updateImageUrl(entity.id, entity.imageUrl!!)
                                        }

                                        // readBy is monotonic (only grows). Union local with
                                        // remote so reconnecting clients see read receipts that
                                        // arrived while they were offline.
                                        val mergedReadBy = unionJsonStringArrays(
                                            existing.readByJson,
                                            entity.readByJson
                                        )
                                        if (mergedReadBy != existing.readByJson) {
                                            messageDao.updateReadBy(entity.id, mergedReadBy)
                                        }

                                        // Reactions: server is authoritative for the persisted
                                        // state (it does the transactional add/remove). Apply
                                        // remote reactions if they differ — covers offline/reopen
                                        // case where socket events were missed.
                                        if (entity.reactionsJson != "{}"
                                            && entity.reactionsJson != existing.reactionsJson
                                        ) {
                                            messageDao.updateReactions(entity.id, entity.reactionsJson)
                                        }

                                        // Never overwrite reply text/senderName — set at send time,
                                        // immutable, and always richer locally.
                                    }
                                }
                            }
                        } catch (_: Exception) { /* non-critical */ }
                    }

                    // For group conversations with no photoUrl, fetch from group document
                    val groupConvsWithoutPhoto = entities.filter { entity ->
                        entity.type == ConversationType.GROUP.name
                            && entity.photoUrl.isNullOrBlank()
                            && !entity.groupId.isNullOrBlank()
                    }
                    for (entity in groupConvsWithoutPhoto) {
                        try {
                            val groupDoc = firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)
                                .document(entity.groupId!!)
                                .get()
                                .await()
                            val avatarUrl = groupDoc.getString("avatarUrl")
                            if (!avatarUrl.isNullOrBlank()) {
                                conversationDao.updatePhotoUrl(entity.id, avatarUrl)
                            }
                        } catch (_: Exception) { /* non-critical */ }
                    }

                    // Remove any conversations that are now soft-deleted by this user (Req 1.7, 2.7)
                    val deletedIds = snapshot?.documents?.filter { doc ->
                        val deletedBy = (doc.get("deletedBy") as? List<*>)
                            ?.filterIsInstance<String>() ?: emptyList()
                        uid in deletedBy
                    }?.map { it.id } ?: emptyList()
                    for (deletedId in deletedIds) {
                        conversationDao.deleteById(deletedId)
                    }

                    // Remove any conversations that are now archived by this user (Req 1.6, 2.6)
                    val archivedIds = snapshot?.documents?.filter { doc ->
                        val archivedBy = (doc.get("archivedBy") as? List<*>)
                            ?.filterIsInstance<String>() ?: emptyList()
                        uid in archivedBy
                    }?.map { it.id } ?: emptyList()
                    for (archivedId in archivedIds) {
                        conversationDao.deleteById(archivedId)
                    }
                }
            }
    }

    /**
     * Stops the Firestore listener. Call when the repository is no longer needed.
     */
    fun stopFirestoreListener() {
        firestoreListener?.remove()
        firestoreListener = null
    }

    /**
     * Restarts the Firestore snapshot listener. Call when the app returns to foreground
     * to resume receiving real-time conversation updates.
     *
     * Requirement 21.5: On foreground (onStart), re-register Firestore listener within 2s.
     */
    fun restartFirestoreListener() {
        startFirestoreListenerIfNeeded()
    }

    /**
     * Resets the batch fetch cycle guard. Call when the app returns to foreground
     * to allow a new batch query set to execute.
     */
    fun resetBatchFetchCycle() {
        batchFetchExecutedThisCycle = false
    }

    /**
     * Fetches conversations from Firestore in batches of 30 IDs using `whereIn` queries.
     *
     * Behavior:
     * - Chunks conversation IDs into groups of at most 30 (Firestore `whereIn` limit) (Req 5.1, 5.2)
     * - Executes at most one batch query set per foreground sync cycle (Req 5.1)
     * - Skips Firestore re-read for conversations whose lastSyncTimestamp is less than 5 minutes old (Req 7.4)
     * - Stores document updateTime per conversation in Room (Req 5.7)
     * - Skips Room write for documents whose stored documentUpdateTime matches incoming snapshot version (Req 5.7)
     */
    override suspend fun batchFetchConversations(conversationIds: List<String>): Resource<Unit> =
        withContext(Dispatchers.IO) {
            // Guard: at most one batch query set per foreground sync cycle
            if (batchFetchExecutedThisCycle) {
                return@withContext Resource.Success(Unit)
            }
            batchFetchExecutedThisCycle = true

            val uid = currentUid ?: return@withContext Resource.Error("User not authenticated")

            try {
                val now = System.currentTimeMillis()

                // Filter out conversations that are still fresh (lastSyncTimestamp < 5 minutes old)
                val staleIds = conversationIds.filter { id ->
                    val lastSync = conversationDao.getLastSyncTimestamp(id)
                    lastSync == null || (now - lastSync) >= STALENESS_THRESHOLD_MS
                }

                if (staleIds.isEmpty()) {
                    return@withContext Resource.Success(Unit)
                }

                // Chunk IDs into groups of 30 for whereIn queries (Firestore limit)
                val chunks = staleIds.chunked(BATCH_CHUNK_SIZE)

                for (chunk in chunks) {
                    val snapshot = firestore.collection(AppConstants.FIRESTORE_COLLECTION_CONVERSATIONS)
                        .whereIn(FieldPath.documentId(), chunk)
                        .get()
                        .await()

                    val entitiesToWrite = snapshot.documents.mapNotNull { doc ->
                        try {
                            // Filter out conversations soft-deleted by the current user (Req 1.7, 2.7)
                            val deletedBy = (doc.get("deletedBy") as? List<*>)
                                ?.filterIsInstance<String>() ?: emptyList()
                            if (uid in deletedBy) return@mapNotNull null

                            // Filter out conversations archived by the current user (Req 1.6, 2.6)
                            val archivedBy = (doc.get("archivedBy") as? List<*>)
                                ?.filterIsInstance<String>() ?: emptyList()
                            if (uid in archivedBy) return@mapNotNull null

                            // Get the document's updateTime from Firestore metadata
                            val incomingUpdateTime = doc.getDate("updateTime")?.time
                                ?: doc.getLong("lastMessageTimestamp")
                                ?: System.currentTimeMillis()

                            // Version-based skip: compare with stored documentUpdateTime
                            val storedUpdateTime = conversationDao.getDocumentUpdateTime(doc.id)
                            if (storedUpdateTime != null && storedUpdateTime == incomingUpdateTime) {
                                // Skip Room write — stored version matches incoming snapshot version
                                return@mapNotNull null
                            }

                            val unreadMap = doc.get("unreadCounts") as? Map<*, *>
                            val myUnread = (unreadMap?.get(uid) as? Long)?.toInt() ?: 0
                            val participantIds = (doc.get("participantIds") as? List<*>)
                                ?.filterIsInstance<String>() ?: emptyList()

                            ConversationEntity(
                                id = doc.id,
                                name = doc.getString("name") ?: "",
                                type = if (doc.getString("type") == "group") ConversationType.GROUP.name
                                       else ConversationType.DIRECT.name,
                                photoUrl = doc.getString("photoUrl"),
                                groupId = doc.getString("groupId"),
                                lastMessageText = doc.getString("lastMessageText") ?: "",
                                lastMessageTimestamp = doc.getLong("lastMessageTimestamp") ?: 0L,
                                lastMessageSenderId = doc.getString("lastMessageSenderId") ?: "",
                                unreadCount = myUnread,
                                memberIdsJson = serializeMemberIds(participantIds),
                                mutedByJson = serializeMemberIds(
                                    (doc.get("mutedBy") as? List<*>)
                                        ?.filterIsInstance<String>() ?: emptyList()
                                ),
                                pinnedByJson = serializeMemberIds(
                                    (doc.get("pinnedBy") as? List<*>)
                                        ?.filterIsInstance<String>() ?: emptyList()
                                ),
                                lastSyncTimestamp = now,
                                documentUpdateTime = incomingUpdateTime,
                                participantNamesJson = serializeFirestoreMap(
                                    doc.get("participantNames") as? Map<*, *>
                                ),
                                participantPhotosJson = serializeFirestoreMap(
                                    doc.get("participantPhotos") as? Map<*, *>
                                ),
                                themeColor = doc.getString("themeColor"),
                                emojiShortcut = doc.getString("emojiShortcut"),
                                nicknamesJson = serializeFirestoreMap(
                                    doc.get("nicknames") as? Map<*, *>
                                )
                            )
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to parse conversation document: ${doc.id}")
                            null
                        }
                    }

                    if (entitiesToWrite.isNotEmpty()) {
                        conversationDao.upsertAll(entitiesToWrite)
                    }
                }

                Resource.Success(Unit)
            } catch (e: Exception) {
                Timber.w(e, "Failed to batch fetch conversations")
                Resource.Error(e.message ?: "Failed to batch fetch conversations")
            }
        }

    private fun serializeMemberIds(memberIds: List<String>): String {
        if (memberIds.isEmpty()) return "[]"
        return memberIds.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
    }

    /**
     * Converts a Firestore map (Map<*, *>) to a JSON object string for storage in Room.
     * Returns null if the map is null or empty.
     */
    private fun serializeFirestoreMap(map: Map<*, *>?): String? {
        if (map.isNullOrEmpty()) return null
        val entries = map.entries.mapNotNull { (key, value) ->
            val k = key as? String ?: return@mapNotNull null
            val v = value as? String
            if (v != null) "\"$k\":\"$v\"" else "\"$k\":null"
        }
        if (entries.isEmpty()) return null
        return "{${entries.joinToString(",")}}"
    }

    private fun ConversationDto.toDomain() = Conversation(
        id = id, type = if (type == "group") ConversationType.GROUP else ConversationType.DIRECT,
        participantIds = participantIds, groupId = groupId, name = name, photoUrl = photoUrl,
        lastMessageText = lastMessageText, lastMessageSenderId = lastMessageSenderId,
        lastMessageTimestamp = lastMessageTimestamp, createdAt = createdAt
    )

    /**
     * Computes the union of two JSON arrays of quoted strings.
     * Used to merge `readBy` lists which are monotonic — once a user has read a
     * message, they remain in the list forever. Order is not significant.
     *
     * Input format: `["userA","userB"]`. Tolerant of whitespace and empty input.
     */
    private fun unionJsonStringArrays(a: String, b: String): String {
        fun parse(s: String): List<String> {
            if (s.isBlank() || s == "[]") return emptyList()
            return s.removeSurrounding("[", "]")
                .split(",")
                .map { it.trim().removeSurrounding("\"") }
                .filter { it.isNotEmpty() }
        }
        val merged = (parse(a) + parse(b)).distinct()
        if (merged.isEmpty()) return "[]"
        return merged.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
    }

    /**
     * Parses a single message map from the Firestore `recentMessages` array
     * into a [MessageEntity] for Room storage.
     */
    private fun parseRecentMessageToEntity(conversationId: String, map: Map<*, *>): MessageEntity? {
        val id = map["id"] as? String ?: return null
        val senderId = map["senderId"] as? String ?: return null
        val timestamp = (map["timestamp"] as? Long)
            ?: (map["timestamp"] as? Double)?.toLong()
            ?: return null

        val readBy = (map["readBy"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        val readByJson = if (readBy.isEmpty()) "[]"
            else readBy.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }

        val reactions = map["reactions"] as? Map<*, *>
        val reactionsJson = if (reactions.isNullOrEmpty()) "{}"
        else {
            val entries = reactions.entries.mapNotNull { (k, v) ->
                val emoji = k as? String ?: return@mapNotNull null
                val users = (v as? List<*>)?.filterIsInstance<String>() ?: return@mapNotNull null
                val usersArr = users.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
                "\"$emoji\":$usersArr"
            }
            "{${entries.joinToString(",")}}"
        }

        return MessageEntity(
            id = id,
            conversationId = conversationId,
            senderId = senderId,
            senderName = map["senderName"] as? String ?: "",
            senderPhotoUrl = map["senderPhotoUrl"] as? String,
            text = map["text"] as? String ?: "",
            type = map["messageType"] as? String ?: "TEXT",
            timestamp = timestamp,
            status = "SENT",
            latitude = (map["latitude"] as? Double),
            longitude = (map["longitude"] as? Double),
            imageUrl = map["imageUrl"] as? String,
            thumbnailUrl = map["thumbnailUrl"] as? String,
            replyToId = map["replyToId"] as? String,
            replyToText = map["replyToText"] as? String,
            replyToSenderName = map["replyToSenderName"] as? String,
            reactionsJson = reactionsJson,
            readByJson = readByJson,
            voiceUrl = map["voiceUrl"] as? String,
            voiceDurationMs = (map["voiceDurationMs"] as? Long)
                ?: (map["voiceDurationMs"] as? Double)?.toLong()
        )
    }

    // ─── NetworkBoundResource Pattern (Requirements 11.1, 11.2, 11.3, 11.4, 11.6) ───

    /**
     * Returns conversations using the NetworkBoundResource pattern:
     * 1. Query Room for cached conversations
     * 2. Check staleness via CacheStalenessChecker (5-minute threshold)
     * 3. Fetch from network if stale
     * 4. Save to Room on success
     * 5. Serve stale cache on failure, retry after ≤60 seconds
     *
     * This method should only be called from ViewModel init blocks or explicit user actions,
     * NOT from Compose recomposition (Requirement 11.1).
     */
    override fun getConversationsResource(): Flow<DataResource<List<Conversation>>> {
        return networkBoundResource(
            query = {
                conversationDao.observeAll().map { entities ->
                    entities.map { it.toDomain() }
                }
            },
            fetch = {
                val token = getIdToken()
                ChatApiClient.apiService.getConversations("Bearer $token")
            },
            saveFetchResult = { conversations ->
                val uid = currentUid ?: return@networkBoundResource
                val entities = conversations.map { dto ->
                    ConversationEntity(
                        id = dto.id,
                        name = dto.name,
                        type = if (dto.type == "group") ConversationType.GROUP.name
                               else ConversationType.DIRECT.name,
                        photoUrl = dto.photoUrl,
                        groupId = dto.groupId,
                        lastMessageText = dto.lastMessageText,
                        lastMessageTimestamp = dto.lastMessageTimestamp,
                        lastMessageSenderId = dto.lastMessageSenderId,
                        unreadCount = dto.unreadCount,
                        memberIdsJson = serializeMemberIds(dto.participantIds),
                        lastSyncTimestamp = System.currentTimeMillis()
                    )
                }
                // Preserve local mutedBy/pinnedBy — REST DTO doesn't carry them.
                val mergedEntities = entities.map { incoming ->
                    val existing = conversationDao.getById(incoming.id)
                    if (existing != null) {
                        incoming.copy(
                            mutedByJson = existing.mutedByJson,
                            pinnedByJson = existing.pinnedByJson
                        )
                    } else incoming
                }
                conversationDao.upsertAll(mergedEntities)
                // Update cache metadata with current timestamp (and ETag if available)
                cacheStalenessChecker.updateMetadata(CACHE_KEY_CONVERSATIONS)
            },
            shouldFetch = { cachedData ->
                // Only fetch if cache is stale (>5 minutes old) or empty
                cachedData.isNullOrEmpty() || cacheStalenessChecker.shouldFetch(CACHE_KEY_CONVERSATIONS)
            },
            onFetchFailed = { throwable ->
                Timber.w(throwable, "Failed to fetch conversations from network, serving stale cache")
                // Schedule retry after ≤60 seconds (Requirement 11.6).
                // We record the failure time and only invalidate if no successful fetch
                // has occurred in the meantime (avoids race with concurrent success).
                val failedAt = System.currentTimeMillis()
                repositoryScope.launch {
                    delay(RETRY_DELAY_MS)
                    val metadata = cacheStalenessChecker.getETag(CACHE_KEY_CONVERSATIONS)
                    // Only invalidate if metadata hasn't been refreshed since the failure
                    if (cacheStalenessChecker.shouldFetch(CACHE_KEY_CONVERSATIONS, failedAt + RETRY_DELAY_MS)) {
                        cacheStalenessChecker.updateMetadata(
                            CACHE_KEY_CONVERSATIONS,
                            currentTimeMs = failedAt - CacheStalenessChecker.STALENESS_THRESHOLD_MS - 1
                        )
                    }
                }
            }
        )
    }

    /**
     * Forces a refresh of conversations from the network, bypassing staleness check.
     * Used for explicit user actions like pull-to-refresh (Requirement 11.1).
     */
    override fun refreshConversations(): Flow<DataResource<List<Conversation>>> {
        return networkBoundResource(
            query = {
                conversationDao.observeAll().map { entities ->
                    entities.map { it.toDomain() }
                }
            },
            fetch = {
                val token = getIdToken()
                ChatApiClient.apiService.getConversations("Bearer $token")
            },
            saveFetchResult = { conversations ->
                val uid = currentUid ?: return@networkBoundResource
                val entities = conversations.map { dto ->
                    ConversationEntity(
                        id = dto.id,
                        name = dto.name,
                        type = if (dto.type == "group") ConversationType.GROUP.name
                               else ConversationType.DIRECT.name,
                        photoUrl = dto.photoUrl,
                        groupId = dto.groupId,
                        lastMessageText = dto.lastMessageText,
                        lastMessageTimestamp = dto.lastMessageTimestamp,
                        lastMessageSenderId = dto.lastMessageSenderId,
                        unreadCount = dto.unreadCount,
                        memberIdsJson = serializeMemberIds(dto.participantIds),
                        lastSyncTimestamp = System.currentTimeMillis()
                    )
                }
                // Preserve local mutedBy/pinnedBy — REST DTO doesn't carry them.
                val mergedEntities = entities.map { incoming ->
                    val existing = conversationDao.getById(incoming.id)
                    if (existing != null) {
                        incoming.copy(
                            mutedByJson = existing.mutedByJson,
                            pinnedByJson = existing.pinnedByJson
                        )
                    } else incoming
                }
                conversationDao.upsertAll(mergedEntities)
                cacheStalenessChecker.updateMetadata(CACHE_KEY_CONVERSATIONS)
            },
            shouldFetch = { true }, // Always fetch on explicit refresh
            onFetchFailed = { throwable ->
                Timber.w(throwable, "Failed to refresh conversations from network")
                // On explicit refresh failure, just log — the user can pull-to-refresh again.
                // No delayed invalidation needed since this was already a forced refresh.
            }
        )
    }

    // ─── Pin / Mute / Archive (Requirement 24.3, 24.4, 24.5) ───────────────

    /**
     * Pins a conversation for the current user.
     * Enforces a maximum of 3 pinned conversations (Req 24.4).
     *
     * Pin count is checked against the local cached conversation list instead of a
     * Firestore query to avoid PERMISSION_DENIED errors. The Firestore read rule
     * requires `request.auth.uid in resource.data.participantIds`, but a
     * `whereArrayContains("pinnedBy", uid)` query cannot satisfy that constraint
     * because Firestore evaluates rules per-document and the query filter is on a
     * different field.
     */
    override suspend fun pinConversation(conversationId: String): Resource<Unit> =
        withContext(Dispatchers.IO) {
            val uid = currentUid ?: return@withContext Resource.Error("User not authenticated")
            try {
                // Check current pin count from local cache to avoid Firestore query
                // that would violate security rules (participantIds filter required for reads)
                val pinnedCount = conversationDao.getPinnedCountForUser(uid)

                if (pinnedCount >= MAX_PINNED_CONVERSATIONS) {
                    return@withContext Resource.Error("Maximum of $MAX_PINNED_CONVERSATIONS pinned conversations reached")
                }

                firestore.collection(AppConstants.FIRESTORE_COLLECTION_CONVERSATIONS)
                    .document(conversationId)
                    .update("pinnedBy", com.google.firebase.firestore.FieldValue.arrayUnion(uid))
                    .await()

                Resource.Success(Unit)
            } catch (e: Exception) {
                Timber.w(e, "Failed to pin conversation: $conversationId")
                Resource.Error(e.message ?: "Failed to pin conversation")
            }
        }

    /**
     * Unpins a conversation for the current user.
     */
    override suspend fun unpinConversation(conversationId: String): Resource<Unit> =
        withContext(Dispatchers.IO) {
            val uid = currentUid ?: return@withContext Resource.Error("User not authenticated")
            try {
                firestore.collection(AppConstants.FIRESTORE_COLLECTION_CONVERSATIONS)
                    .document(conversationId)
                    .update("pinnedBy", com.google.firebase.firestore.FieldValue.arrayRemove(uid))
                    .await()

                Resource.Success(Unit)
            } catch (e: Exception) {
                Timber.w(e, "Failed to unpin conversation: $conversationId")
                Resource.Error(e.message ?: "Failed to unpin conversation")
            }
        }

    /**
     * Mutes a conversation for the current user (Req 24.5).
     */
    override suspend fun muteConversation(conversationId: String): Resource<Unit> =
        withContext(Dispatchers.IO) {
            val uid = currentUid ?: return@withContext Resource.Error("User not authenticated")
            try {
                firestore.collection(AppConstants.FIRESTORE_COLLECTION_CONVERSATIONS)
                    .document(conversationId)
                    .update("mutedBy", com.google.firebase.firestore.FieldValue.arrayUnion(uid))
                    .await()

                Resource.Success(Unit)
            } catch (e: Exception) {
                Timber.w(e, "Failed to mute conversation: $conversationId")
                Resource.Error(e.message ?: "Failed to mute conversation")
            }
        }

    /**
     * Unmutes a conversation for the current user.
     */
    override suspend fun unmuteConversation(conversationId: String): Resource<Unit> =
        withContext(Dispatchers.IO) {
            val uid = currentUid ?: return@withContext Resource.Error("User not authenticated")
            try {
                firestore.collection(AppConstants.FIRESTORE_COLLECTION_CONVERSATIONS)
                    .document(conversationId)
                    .update("mutedBy", com.google.firebase.firestore.FieldValue.arrayRemove(uid))
                    .await()

                Resource.Success(Unit)
            } catch (e: Exception) {
                Timber.w(e, "Failed to unmute conversation: $conversationId")
                Resource.Error(e.message ?: "Failed to unmute conversation")
            }
        }

    /**
     * Archives a conversation for the current user (Req 1.6, 2.6).
     * Adds the current user's ID to the Firestore `archivedBy` array field,
     * and also removes from local Room for immediate UI feedback.
     *
     * The conversation will not reappear on sync because the Firestore listener
     * filters out conversations where the current user is in `archivedBy`.
     */
    override suspend fun archiveConversation(conversationId: String): Resource<Unit> =
        withContext(Dispatchers.IO) {
            val uid = currentUid ?: return@withContext Resource.Error("User not authenticated")
            try {
                // Mark as archived on server
                firestore.collection(AppConstants.FIRESTORE_COLLECTION_CONVERSATIONS)
                    .document(conversationId)
                    .update("archivedBy", com.google.firebase.firestore.FieldValue.arrayUnion(uid))
                    .await()

                // Remove from local cache
                conversationDao.deleteById(conversationId)

                Resource.Success(Unit)
            } catch (e: Exception) {
                Timber.w(e, "Failed to archive conversation: $conversationId")
                Resource.Error(e.message ?: "Failed to archive conversation")
            }
        }

    /**
     * Soft-deletes a conversation for the current user (Req 1.7, 2.7).
     * Adds the current user's ID to the Firestore `deletedBy` array field (soft delete per user),
     * and also removes from local Room for immediate UI feedback.
     *
     * The conversation will not reappear on sync because the Firestore listener
     * filters out conversations where the current user is in `deletedBy`.
     */
    override suspend fun softDeleteConversation(conversationId: String): Resource<Unit> =
        withContext(Dispatchers.IO) {
            val uid = currentUid ?: return@withContext Resource.Error("User not authenticated")
            try {
                // Mark as deleted for this user on Firestore (soft delete)
                firestore.collection(AppConstants.FIRESTORE_COLLECTION_CONVERSATIONS)
                    .document(conversationId)
                    .update("deletedBy", com.google.firebase.firestore.FieldValue.arrayUnion(uid))
                    .await()

                // Remove from local Room for immediate UI feedback
                conversationDao.deleteById(conversationId)

                Resource.Success(Unit)
            } catch (e: Exception) {
                Timber.w(e, "Failed to soft-delete conversation: $conversationId")
                Resource.Error(e.message ?: "Failed to delete conversation")
            }
        }

    override suspend fun updateThemeColor(conversationId: String, color: String?): Resource<Unit> =
        withContext(Dispatchers.IO) {
            try {
                // Write to Room first (SSOT — immediate UI update)
                conversationDao.updateThemeColor(conversationId, color)
                // Then persist to Firestore
                firestore.collection(AppConstants.FIRESTORE_COLLECTION_CONVERSATIONS)
                    .document(conversationId)
                    .update("themeColor", color)
                    .await()
                Resource.Success(Unit)
            } catch (e: Exception) {
                Timber.w(e, "Failed to update theme color: $conversationId")
                Resource.Error(e.message ?: "Failed to update theme color")
            }
        }

    override suspend fun updateEmojiShortcut(conversationId: String, emoji: String?): Resource<Unit> =
        withContext(Dispatchers.IO) {
            try {
                // Write to Room first (SSOT — immediate UI update)
                conversationDao.updateEmojiShortcut(conversationId, emoji)
                // Then persist to Firestore
                firestore.collection(AppConstants.FIRESTORE_COLLECTION_CONVERSATIONS)
                    .document(conversationId)
                    .update("emojiShortcut", emoji)
                    .await()
                Resource.Success(Unit)
            } catch (e: Exception) {
                Timber.w(e, "Failed to update emoji shortcut: $conversationId")
                Resource.Error(e.message ?: "Failed to update emoji shortcut")
            }
        }

    override suspend fun updateNicknames(conversationId: String, nicknames: Map<String, String>): Resource<Unit> =
        withContext(Dispatchers.IO) {
            try {
                // Serialize for Room
                val nicknamesJson = if (nicknames.isEmpty()) null
                else kotlinx.serialization.json.JsonObject(
                    nicknames.mapValues { (_, v) -> kotlinx.serialization.json.JsonPrimitive(v) }
                ).toString()
                // Write to Room first (SSOT — immediate UI update)
                conversationDao.updateNicknames(conversationId, nicknamesJson)
                // Then persist to Firestore
                firestore.collection(AppConstants.FIRESTORE_COLLECTION_CONVERSATIONS)
                    .document(conversationId)
                    .update("nicknames", nicknames)
                    .await()
                Resource.Success(Unit)
            } catch (e: Exception) {
                Timber.w(e, "Failed to update nicknames: $conversationId")
                Resource.Error(e.message ?: "Failed to update nicknames")
            }
        }

    /**
     * Resolves the conversationId for a given groupId.
     * First checks Room (fast), then queries Firestore directly as fallback.
     */
    override suspend fun getConversationIdByGroupId(groupId: String): String? =
        withContext(Dispatchers.IO) {
            // 1. Try Room first (fast path)
            val cached = conversationDao.getByGroupId(groupId)
            if (cached != null) return@withContext cached.id

            // 2. Fallback: query Firestore directly
            try {
                val snapshot = firestore.collection(AppConstants.FIRESTORE_COLLECTION_CONVERSATIONS)
                    .whereEqualTo("groupId", groupId)
                    .limit(1)
                    .get()
                    .await()
                val doc = snapshot.documents.firstOrNull()
                doc?.id
            } catch (e: Exception) {
                Timber.w(e, "Failed to resolve conversationId for groupId: $groupId")
                null
            }
        }

    /**
     * Updates the photo URL for a conversation.
     * Writes to Room first (SSOT — immediate UI update) then Firestore.
     */
    override suspend fun updateConversationPhotoUrl(conversationId: String, photoUrl: String?): Resource<Unit> =
        withContext(Dispatchers.IO) {
            try {
                // Write to Room first (SSOT — immediate UI update)
                conversationDao.updatePhotoUrl(conversationId, photoUrl)
                // Then persist to Firestore
                firestore.collection(AppConstants.FIRESTORE_COLLECTION_CONVERSATIONS)
                    .document(conversationId)
                    .update("photoUrl", photoUrl)
                    .await()
                Resource.Success(Unit)
            } catch (e: Exception) {
                Timber.w(e, "Failed to update conversation photo: $conversationId")
                Resource.Error(e.message ?: "Failed to update conversation photo")
            }
        }

    /**
     * Updates the photo URL for a conversation identified by groupId.
     * Updates Room immediately by groupId (no conversationId needed).
     * Then resolves the conversationId and updates Firestore.
     */
    override suspend fun updateConversationPhotoUrlByGroupId(groupId: String, photoUrl: String?): Resource<Unit> =
        withContext(Dispatchers.IO) {
            try {
                // Update Room directly by groupId (immediate UI update)
                Timber.d("ConvRepo: updatePhotoUrlByGroupId groupId=$groupId, photoUrl=$photoUrl")
                conversationDao.updatePhotoUrlByGroupId(groupId, photoUrl)

                // Verify the update took effect
                val entity = conversationDao.getByGroupId(groupId)
                Timber.d("ConvRepo: after update, entity=${entity?.id}, entity.photoUrl=${entity?.photoUrl}, entity.groupId=${entity?.groupId}")

                // Resolve conversationId for Firestore update
                val convId = getConversationIdByGroupId(groupId)
                Timber.d("ConvRepo: resolved convId=$convId for Firestore update")
                if (convId != null) {
                    firestore.collection(AppConstants.FIRESTORE_COLLECTION_CONVERSATIONS)
                        .document(convId)
                        .update("photoUrl", photoUrl)
                        .await()
                    Timber.d("ConvRepo: Firestore photoUrl updated for convId=$convId")
                }
                Resource.Success(Unit)
            } catch (e: Exception) {
                Timber.w(e, "Failed to update conversation photo by groupId: $groupId")
                Resource.Error(e.message ?: "Failed to update conversation photo")
            }
        }

    companion object {
        /** Timeout for foreground unread-count sync (Requirement 12.5) */
        const val FOREGROUND_SYNC_TIMEOUT_MS = 10_000L

        /** Cache key for conversations list */
        private const val CACHE_KEY_CONVERSATIONS = "conversations_list"

        /** Maximum delay before retrying a failed fetch of stale data (Requirement 11.6) */
        private const val RETRY_DELAY_MS = 60_000L

        /** Maximum number of IDs per Firestore whereIn query (Firestore limit) (Requirement 5.1) */
        const val BATCH_CHUNK_SIZE = 30

        /** Staleness threshold: skip Firestore re-read if lastSyncTimestamp < 5 minutes old (Requirement 7.4) */
        const val STALENESS_THRESHOLD_MS = 5 * 60 * 1000L // 5 minutes

        /** Maximum number of pinned conversations per user (Requirement 24.4) */
        const val MAX_PINNED_CONVERSATIONS = 3
    }
}
