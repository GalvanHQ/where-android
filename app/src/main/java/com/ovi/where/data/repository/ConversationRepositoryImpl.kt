package com.ovi.where.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.ovi.where.core.common.Resource
import com.ovi.where.core.constants.AppConstants
import com.ovi.where.data.local.dao.ConversationDao
import com.ovi.where.data.local.entity.ConversationEntity
import com.ovi.where.data.local.entity.toDomain
import com.ovi.where.data.local.entity.toEntity
import com.ovi.where.data.remote.chat.ChatApiClient
import com.ovi.where.data.remote.chat.ConversationDto
import com.ovi.where.data.remote.chat.CreateDirectConversationRequest
import com.ovi.where.data.remote.chat.CreateGroupConversationRequest
import com.ovi.where.domain.model.Conversation
import com.ovi.where.domain.model.ConversationType
import com.ovi.where.domain.repository.ConversationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

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
    private val conversationDao: ConversationDao
) : ConversationRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var firestoreListener: ListenerRegistration? = null

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

            // Persist to Room before Firestore listener starts
            conversationDao.upsertAll(entities)
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to fetch initial conversations")
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
                            lastSyncTimestamp = System.currentTimeMillis()
                        )
                    } catch (_: Exception) { null }
                } ?: emptyList()

                // Write to Room immediately so UI flow emits within 500ms (Requirement 12.3)
                repositoryScope.launch {
                    conversationDao.upsertAll(entities)
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

    private fun serializeMemberIds(memberIds: List<String>): String {
        if (memberIds.isEmpty()) return "[]"
        return memberIds.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
    }

    private fun ConversationDto.toDomain() = Conversation(
        id = id, type = if (type == "group") ConversationType.GROUP else ConversationType.DIRECT,
        participantIds = participantIds, groupId = groupId, name = name, photoUrl = photoUrl,
        lastMessageText = lastMessageText, lastMessageSenderId = lastMessageSenderId,
        lastMessageTimestamp = lastMessageTimestamp, createdAt = createdAt
    )

    companion object {
        /** Timeout for foreground unread-count sync (Requirement 12.5) */
        const val FOREGROUND_SYNC_TIMEOUT_MS = 10_000L
    }
}
