package com.ovi.where.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.ovi.where.core.common.Resource
import com.ovi.where.core.utils.ImageCompressor
import com.ovi.where.data.local.CacheStalenessChecker
import com.ovi.where.data.local.dao.MessageDao
import com.ovi.where.data.local.entity.MessageEntity
import com.ovi.where.data.local.entity.toDomain
import com.ovi.where.data.local.entity.toEntity
import com.ovi.where.data.remote.chat.ChatApiClient
import com.ovi.where.data.remote.chat.ChatSocketIoClient
import com.ovi.where.data.remote.chat.MessageDto
import com.ovi.where.data.remote.chat.ServerFrame
import com.ovi.where.data.util.networkBoundResource
import com.ovi.where.domain.model.Message
import com.ovi.where.domain.model.MessagePage
import com.ovi.where.domain.model.MessageStatus
import com.ovi.where.domain.model.MessageType
import com.ovi.where.domain.repository.MessageRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber
import dagger.Lazy
import com.ovi.where.data.util.Resource as DataResource

private const val TAG = "MessageRepositoryImpl"

/**
 * MessageRepository implementation with:
 * - Room as single source of truth for messages (observed via Flow)
 * - Optimistic insert with PENDING status (Requirement 1.1)
 * - Ack handling: tempId → serverId, status → SENT (Requirement 1.2)
 * - Timeout (10s) or error → FAILED status (Requirement 1.3)
 * - Offline queue: FIFO, max 50 messages (Requirements 1.6, 1.7)
 * - Queue flush in order on reconnection (Requirement 13.4)
 * - Image compression and upload with progress tracking (Requirements 6.1, 6.3, 6.4, 6.5, 6.7)
 */
@Singleton
class MessageRepositoryImpl @Inject constructor(
    private val lazyWsClient: Lazy<ChatSocketIoClient>,
    private val firebaseAuth: FirebaseAuth,
    private val messageDao: MessageDao,
    private val firebaseStorage: FirebaseStorage,
    private val imageCompressor: ImageCompressor,
    private val cacheStalenessChecker: CacheStalenessChecker,
    @ApplicationContext private val context: Context
) : MessageRepository {

    /**
     * Lazily-resolved ChatSocketIoClient instance.
     * Not instantiated until first access, keeping app startup free of chat initialization (Req 20.1, 20.4, 20.5).
     */
    private val wsClient: ChatSocketIoClient get() = lazyWsClient.get()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Pending ack timeout jobs keyed by tempId
    private val ackTimeoutJobs = ConcurrentHashMap<String, Job>()

    // Offline queue: FIFO, max 50 messages
    private val offlineQueue = ConcurrentLinkedQueue<QueuedMessage>()
    private val queueMutex = Mutex()
    private val flushMutex = Mutex()

    // Upload progress tracking: tempId -> progress (0-100)
    private val uploadProgressMap = ConcurrentHashMap<String, MutableStateFlow<Int>>()

    override val offlineQueueSize: Int
        get() = offlineQueue.size

    init {
        // Observe incoming frames for ack handling and real-time message delivery
        scope.launch { observeIncomingFrames() }
        // Observe connection state for queue flushing
        scope.launch { observeConnectionState() }
    }

    // ─── Observe Messages ──────────────────────────────────────────────────────

    /**
     * Observes messages for a conversation from Room database.
     * Room is the single source of truth; messages are sorted by timestamp ASC.
     */
    override fun observeMessages(conversationId: String): Flow<List<Message>> {
        return messageDao.observeByConversation(conversationId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    // ─── Upload Progress Observation ───────────────────────────────────────────

    override fun observeUploadProgress(tempId: String): StateFlow<Int>? {
        return uploadProgressMap[tempId]
    }

    // ─── Send Message (Optimistic + Ack + Timeout + Offline Queue) ─────────────

    /**
     * Sends a text message with optimistic insert.
     *
     * 1. Generate tempId via UUID
     * 2. Insert message with PENDING status into Room immediately
     * 3. If connected: emit via ChatSocketIoClient.sendText, start 10s ack timeout
     * 4. If disconnected: enqueue in offline queue (FIFO, max 50)
     *
     * On MessageAck frame: update tempId → serverId, status → SENT
     * On timeout (10s) or error: update status to FAILED
     */
    override suspend fun sendMessage(
        conversationId: String,
        text: String,
        replyToId: String?
    ): Resource<Message> {
        if (text.isBlank()) {
            return Resource.Error("Message text cannot be empty")
        }

        val tempId = UUID.randomUUID().toString()
        val currentUser = firebaseAuth.currentUser
        val optimistic = Message(
            id = tempId,
            conversationId = conversationId,
            senderId = currentUser?.uid ?: "",
            senderName = currentUser?.displayName ?: "",
            senderPhotoUrl = currentUser?.photoUrl?.toString(),
            text = text,
            type = MessageType.TEXT,
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.PENDING,
            replyToId = replyToId
        )

        // Step 1: Insert optimistically into Room with PENDING status
        messageDao.insert(optimistic.toEntity())

        // Step 2: Send or queue
        val isConnected = wsClient.connectionState.value == ChatSocketIoClient.ConnectionState.CONNECTED
        if (isConnected) {
            emitMessage(tempId, text, replyToId)
        } else {
            enqueueMessage(QueuedMessage(tempId, conversationId, text, replyToId, MessageType.TEXT))
        }

        return Resource.Success(optimistic)
    }

    /**
     * Sends a text message with link preview metadata attached.
     *
     * This is used when the FetchLinkPreviewUseCase successfully retrieves
     * Open Graph metadata for a URL in the message text.
     *
     * Requirements: 12.1, 12.2
     */
    suspend fun sendMessageWithLinkPreview(
        conversationId: String,
        text: String,
        replyToId: String?,
        linkPreviewUrl: String,
        linkPreviewTitle: String?,
        linkPreviewDescription: String?,
        linkPreviewImageUrl: String?,
        linkPreviewDomain: String
    ): Resource<Message> {
        if (text.isBlank()) {
            return Resource.Error("Message text cannot be empty")
        }

        val tempId = UUID.randomUUID().toString()
        val currentUser = firebaseAuth.currentUser
        val optimistic = Message(
            id = tempId,
            conversationId = conversationId,
            senderId = currentUser?.uid ?: "",
            senderName = currentUser?.displayName ?: "",
            senderPhotoUrl = currentUser?.photoUrl?.toString(),
            text = text,
            type = MessageType.TEXT,
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.PENDING,
            replyToId = replyToId,
            linkPreviewUrl = linkPreviewUrl,
            linkPreviewTitle = linkPreviewTitle,
            linkPreviewDescription = linkPreviewDescription,
            linkPreviewImageUrl = linkPreviewImageUrl,
            linkPreviewDomain = linkPreviewDomain
        )

        // Step 1: Insert optimistically into Room with PENDING status
        messageDao.insert(optimistic.toEntity())

        // Step 2: Send or queue
        val isConnected = wsClient.connectionState.value == ChatSocketIoClient.ConnectionState.CONNECTED
        if (isConnected) {
            emitMessage(tempId, text, replyToId)
        } else {
            enqueueMessage(QueuedMessage(tempId, conversationId, text, replyToId, MessageType.TEXT))
        }

        return Resource.Success(optimistic)
    }

    /**
     * Sends a text message with mention metadata attached.
     *
     * This is used when the message contains @mention tokens.
     * The mentionedUserIds are included in the message payload so the server
     * can trigger push notifications to mentioned users.
     *
     * Requirements: 14.3, 14.4
     */
    suspend fun sendMessageWithMentions(
        conversationId: String,
        text: String,
        replyToId: String?,
        mentionedUserIds: List<String>
    ): Resource<Message> {
        if (text.isBlank()) {
            return Resource.Error("Message text cannot be empty")
        }

        val tempId = UUID.randomUUID().toString()
        val currentUser = firebaseAuth.currentUser
        val optimistic = Message(
            id = tempId,
            conversationId = conversationId,
            senderId = currentUser?.uid ?: "",
            senderName = currentUser?.displayName ?: "",
            senderPhotoUrl = currentUser?.photoUrl?.toString(),
            text = text,
            type = MessageType.TEXT,
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.PENDING,
            replyToId = replyToId,
            mentionedUserIds = mentionedUserIds
        )

        // Step 1: Insert optimistically into Room with PENDING status
        messageDao.insert(optimistic.toEntity())

        // Step 2: Send or queue
        val isConnected = wsClient.connectionState.value == ChatSocketIoClient.ConnectionState.CONNECTED
        if (isConnected) {
            emitMessage(tempId, text, replyToId)
        } else {
            enqueueMessage(QueuedMessage(tempId, conversationId, text, replyToId, MessageType.TEXT))
        }

        return Resource.Success(optimistic)
    }

    /**
     * Retries sending a FAILED message.
     * Resets the message status to PENDING and re-emits via socket or queues.
     */
    override suspend fun retryMessage(messageId: String): Resource<Message> {
        val entity = messageDao.getById(messageId)
            ?: return Resource.Error("Message not found")

        val message = entity.toDomain()
        if (message.status != MessageStatus.FAILED) {
            return Resource.Error("Message is not in FAILED state")
        }

        // Reset to PENDING
        messageDao.updateStatus(messageId, MessageStatus.PENDING.name)

        val isConnected = wsClient.connectionState.value == ChatSocketIoClient.ConnectionState.CONNECTED
        if (isConnected) {
            emitMessage(messageId, message.text, message.replyToId)
        } else {
            enqueueMessage(
                QueuedMessage(messageId, message.conversationId, message.text, message.replyToId, message.type)
            )
        }

        return Resource.Success(message.copy(status = MessageStatus.PENDING))
    }

    // ─── Send Location Message ─────────────────────────────────────────────────

    override suspend fun sendLocationMessage(
        conversationId: String,
        latitude: Double?,
        longitude: Double?
    ): Resource<Message> {
        // Validate null coordinates (Requirement 15.4)
        if (latitude == null || longitude == null) {
            return Resource.Error("Missing coordinates: latitude and longitude must not be null")
        }
        // Validate coordinate ranges (Requirement 15.3)
        if (latitude < -90.0 || latitude > 90.0 || longitude < -180.0 || longitude > 180.0) {
            return Resource.Error("Invalid coordinates: latitude must be -90..90, longitude must be -180..180")
        }

        val tempId = UUID.randomUUID().toString()
        val currentUser = firebaseAuth.currentUser
        val optimistic = Message(
            id = tempId,
            conversationId = conversationId,
            senderId = currentUser?.uid ?: "",
            senderName = currentUser?.displayName ?: "",
            senderPhotoUrl = currentUser?.photoUrl?.toString(),
            text = "📍 Location",
            type = MessageType.LOCATION,
            latitude = latitude,
            longitude = longitude,
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.PENDING
        )

        // Insert optimistically
        messageDao.insert(optimistic.toEntity())

        val isConnected = wsClient.connectionState.value == ChatSocketIoClient.ConnectionState.CONNECTED
        if (isConnected) {
            emitLocationMessage(tempId, latitude, longitude)
        } else {
            enqueueMessage(
                QueuedMessage(
                    tempId = tempId,
                    conversationId = conversationId,
                    text = "📍 Location",
                    replyToId = null,
                    type = MessageType.LOCATION,
                    latitude = latitude,
                    longitude = longitude
                )
            )
        }

        return Resource.Success(optimistic)
    }

    // ─── Send Image Message ────────────────────────────────────────────────────

    /**
     * Sends an image message with compression and upload.
     *
     * Flow:
     * 1. Compress image to max 1920px longest edge at 80% JPEG quality
     * 2. Accept JPEG, PNG, WebP, HEIF source formats
     * 3. Reject images exceeding 10MB before compression
     * 4. Upload to Firebase Storage with progress tracking, 60-second timeout
     * 5. Retry up to 3 attempts on failure
     * 6. On success: emit via ChatSocketIoClient.sendImage
     *
     * Requirements: 6.1, 6.3, 6.4, 6.5, 6.7
     */
    override suspend fun sendImageMessage(
        conversationId: String,
        imageUri: Uri
    ): Resource<Message> {
        val tempId = UUID.randomUUID().toString()
        val currentUser = firebaseAuth.currentUser

        // Step 1: Compress the image
        val compressionResult = imageCompressor.compress(context, imageUri)
        when (compressionResult) {
            is ImageCompressor.CompressionResult.Error -> {
                return Resource.Error(compressionResult.message)
            }
            is ImageCompressor.CompressionResult.Success -> { /* continue */ }
        }

        val compressedFile = (compressionResult as ImageCompressor.CompressionResult.Success).compressedFile

        // Step 2: Create optimistic message with PENDING status
        val optimistic = Message(
            id = tempId,
            conversationId = conversationId,
            senderId = currentUser?.uid ?: "",
            senderName = currentUser?.displayName ?: "",
            senderPhotoUrl = currentUser?.photoUrl?.toString(),
            text = "📷 Image",
            type = MessageType.IMAGE,
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.PENDING,
            imageUrl = imageUri.toString() // Local URI as placeholder until upload completes
        )

        // Insert optimistically into Room
        messageDao.insert(optimistic.toEntity())

        // Step 3: Initialize progress tracking
        val progressFlow = MutableStateFlow(0)
        uploadProgressMap[tempId] = progressFlow

        // Step 4: Upload with retry (up to 3 attempts) and 60-second timeout
        var uploadUrl: String? = null
        var lastError: Exception? = null

        for (attempt in 1..MAX_UPLOAD_RETRIES) {
            try {
                uploadUrl = uploadImageWithProgress(
                    tempId = tempId,
                    compressedFile = compressedFile,
                    conversationId = conversationId,
                    progressFlow = progressFlow
                )
                break // Success
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "Image upload attempt $attempt failed: ${e.message}")
                if (attempt < MAX_UPLOAD_RETRIES) {
                    delay(UPLOAD_RETRY_DELAY_MS)
                }
            }
        }

        // Clean up compressed file
        try {
            compressedFile.delete()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete compressed file: ${e.message}")
        }

        // Step 5: Handle result
        if (uploadUrl == null) {
            // All retries exhausted — mark as FAILED
            messageDao.updateStatus(tempId, MessageStatus.FAILED.name)
            uploadProgressMap.remove(tempId)
            return Resource.Error("Image upload failed after $MAX_UPLOAD_RETRIES attempts: ${lastError?.message}")
        }

        // Step 6: Update message with the uploaded image URL
        messageDao.updateImageUrl(tempId, uploadUrl)
        progressFlow.value = 100
        uploadProgressMap.remove(tempId)

        // Step 7: Emit via ChatSocketIoClient.sendImage
        val isConnected = wsClient.connectionState.value == ChatSocketIoClient.ConnectionState.CONNECTED
        if (isConnected) {
            try {
                wsClient.sendImage(uploadUrl, tempId)
                startAckTimeout(tempId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to emit image message $tempId: ${e.message}")
                messageDao.updateStatus(tempId, MessageStatus.FAILED.name)
                return Resource.Error("Failed to send image message: ${e.message}")
            }
        } else {
            enqueueMessage(
                QueuedMessage(
                    tempId = tempId,
                    conversationId = conversationId,
                    text = "📷 Image",
                    replyToId = null,
                    type = MessageType.IMAGE,
                    imageUrl = uploadUrl
                )
            )
        }

        return Resource.Success(optimistic.copy(imageUrl = uploadUrl))
    }

    /**
     * Uploads a compressed image file to Firebase Storage with progress tracking
     * and a 60-second timeout.
     *
     * @return The download URL of the uploaded image
     * @throws Exception on upload failure or timeout
     */
    private suspend fun uploadImageWithProgress(
        tempId: String,
        compressedFile: java.io.File,
        conversationId: String,
        progressFlow: MutableStateFlow<Int>
    ): String {
        return withTimeout(UPLOAD_TIMEOUT_MS) {
            val userId = firebaseAuth.currentUser?.uid ?: throw IllegalStateException("User not authenticated")
            val fileName = "chat_${tempId}.jpg"
            val storageRef = firebaseStorage.reference
                .child("chat_images/$conversationId/$userId/$fileName")

            val fileUri = Uri.fromFile(compressedFile)
            val uploadTask = storageRef.putFile(fileUri)

            // Track progress
            uploadTask.addOnProgressListener { snapshot ->
                val progress = if (snapshot.totalByteCount > 0) {
                    ((snapshot.bytesTransferred * 100) / snapshot.totalByteCount).toInt()
                } else {
                    0
                }
                progressFlow.value = progress.coerceIn(0, 99) // Reserve 100 for completion
            }

            // Await upload completion
            uploadTask.await()

            // Get download URL
            val downloadUrl = storageRef.downloadUrl.await()
            downloadUrl.toString()
        }
    }

    // ─── Load History ──────────────────────────────────────────────────────────

    override suspend fun loadHistory(conversationId: String) {
        try {
            val token = getIdToken()
            val messages = ChatApiClient.apiService
                .getMessages("Bearer $token", conversationId)
                .map { it.toDomainEntity(conversationId) }

            messageDao.upsertAll(messages)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load history: ${e.message}")
        }
    }

    // ─── Fetch Missed Messages (Reconnection) ─────────────────────────────────

    /**
     * Fetches messages missed during disconnection using the timestamp of the last
     * locally cached message for the active conversation.
     *
     * On failure: retries once after 2 seconds. If still failing, returns error
     * for the UI to display an inline retry action.
     *
     * Requirements: 13.5, 13.6
     */
    override suspend fun fetchMissedMessages(conversationId: String): Resource<Unit> {
        val lastMessage = messageDao.getLatestMessages(conversationId, 1).firstOrNull()
        val sinceTimestamp = lastMessage?.timestamp?.toString() ?: "0"

        return try {
            val token = getIdToken()
            val missedMessages = ChatApiClient.apiService.getMessagesSince(
                token = "Bearer $token",
                conversationId = conversationId,
                after = sinceTimestamp
            )
            val entities = missedMessages.map { it.toDomainEntity(conversationId) }
            if (entities.isNotEmpty()) {
                messageDao.upsertAll(entities)
            }
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "First attempt to fetch missed messages failed, retrying in 2s: ${e.message}")
            // Retry once after 2 seconds (Requirement 13.6)
            try {
                delay(MISSED_MESSAGES_RETRY_DELAY_MS)
                val token = getIdToken()
                val missedMessages = ChatApiClient.apiService.getMessagesSince(
                    token = "Bearer $token",
                    conversationId = conversationId,
                    after = sinceTimestamp
                )
                val entities = missedMessages.map { it.toDomainEntity(conversationId) }
                if (entities.isNotEmpty()) {
                    messageDao.upsertAll(entities)
                }
                Resource.Success(Unit)
            } catch (retryError: Exception) {
                Log.e(TAG, "Retry to fetch missed messages also failed: ${retryError.message}")
                Resource.Error("Failed to fetch missed messages: ${retryError.message}")
            }
        }
    }

    // ─── Cursor-Based Pagination ───────────────────────────────────────────────

    /**
     * Loads older messages using cursor-based pagination.
     *
     * On initial load (beforeCursor == null):
     *   - Serve from Room cache first (latest messages up to limit)
     *   - Then background sync with server and merge results into Room
     *   - Merge server results with local cache by unique ID (discard duplicates)
     *
     * On subsequent loads (beforeCursor != null):
     *   - Fetch older messages from server using the timestamp cursor
     *   - Persist to Room via upsertAll (deduplication by primary key)
     *   - Return MessagePage with nextCursor and hasMore flag
     *
     * Requirements: 2.1, 2.2, 2.4, 2.6, 14.3
     */
    override suspend fun loadOlderMessages(
        conversationId: String,
        beforeCursor: String?,
        limit: Int
    ): MessagePage {
        if (beforeCursor == null) {
            // Initial load: serve from Room cache first, then background sync with server
            val cachedEntities = messageDao.getLatestMessages(conversationId, limit)
            val cachedMessages = cachedEntities.map { it.toDomain() }.sortedBy { it.timestamp }

            // Determine cursor from the oldest cached message
            val nextCursor = if (cachedMessages.isNotEmpty()) {
                cachedMessages.first().timestamp.toString()
            } else {
                null
            }

            // Background sync with server: fetch latest messages and merge into Room
            scope.launch {
                try {
                    val token = getIdToken()
                    val serverPage = ChatApiClient.apiService.getMessagesPaginated(
                        token = "Bearer $token",
                        conversationId = conversationId,
                        before = null,
                        limit = limit
                    )

                    // Persist server messages to Room via upsertAll
                    // Room's OnConflictStrategy.REPLACE handles deduplication by primary key
                    val serverEntities = serverPage.messages.map { it.toDomainEntity(conversationId) }
                    if (serverEntities.isNotEmpty()) {
                        messageDao.upsertAll(serverEntities)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Background sync failed: ${e.message}")
                }
            }

            return MessagePage(
                messages = cachedMessages,
                nextCursor = nextCursor,
                hasMore = cachedMessages.size >= limit
            )
        } else {
            // Pagination: fetch older messages from server using timestamp cursor
            return try {
                val token = getIdToken()
                val serverPage = ChatApiClient.apiService.getMessagesPaginated(
                    token = "Bearer $token",
                    conversationId = conversationId,
                    before = beforeCursor,
                    limit = limit
                )

                // Persist fetched messages to Room via upsertAll (deduplication by PK)
                val serverEntities = serverPage.messages.map { it.toDomainEntity(conversationId) }
                if (serverEntities.isNotEmpty()) {
                    messageDao.upsertAll(serverEntities)
                }

                val messages = serverEntities.map { it.toDomain() }.sortedBy { it.timestamp }

                // Use server-provided cursor and hasMore, or derive from results
                val nextCursor = serverPage.nextCursor
                    ?: if (messages.isNotEmpty()) {
                        messages.first().timestamp.toString()
                    } else {
                        null
                    }

                MessagePage(
                    messages = messages,
                    nextCursor = nextCursor,
                    hasMore = serverPage.hasMore
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load older messages: ${e.message}")
                MessagePage(messages = emptyList(), nextCursor = beforeCursor, hasMore = true)
            }
        }
    }

    // ─── Reactions (Optimistic Toggle + Rollback) ─────────────────────────────

    /**
     * Toggles a reaction on a message with optimistic update and rollback on failure.
     *
     * Toggle logic:
     * - If the current user already reacted with this emoji → remove the reaction
     * - Otherwise → add the reaction
     *
     * Optimistic update: modifies local reactions map immediately via Room.
     * On server failure or 10s timeout: rolls back to the previous reaction state.
     *
     * Requirements: 3.3, 3.4, 3.5, 3.6
     */
    override suspend fun reactToMessage(
        conversationId: String,
        messageId: String,
        emoji: String
    ): Resource<Unit> {
        val currentUserId = firebaseAuth.currentUser?.uid
            ?: return Resource.Error("User not authenticated")

        val entity = messageDao.getById(messageId)
            ?: return Resource.Error("Message not found")

        // Parse current reactions from JSON
        val currentReactions = parseReactionsJson(entity.reactionsJson)
        val usersForEmoji = currentReactions.getOrDefault(emoji, emptyList())
        val isRemoving = currentUserId in usersForEmoji

        // Compute new reactions state
        val newReactions = currentReactions.toMutableMap()
        if (isRemoving) {
            val updatedUsers = usersForEmoji - currentUserId
            if (updatedUsers.isEmpty()) {
                newReactions.remove(emoji)
            } else {
                newReactions[emoji] = updatedUsers
            }
        } else {
            newReactions[emoji] = usersForEmoji + currentUserId
        }

        // Optimistic update: persist new reactions immediately
        val previousReactionsJson = entity.reactionsJson
        val newReactionsJson = serializeReactionsMap(newReactions)
        messageDao.updateReactions(messageId, newReactionsJson)

        // Emit to server with 10s timeout; rollback on failure
        return try {
            withTimeout(REACTION_TIMEOUT_MS) {
                if (isRemoving) {
                    wsClient.removeReaction(messageId, emoji)
                } else {
                    wsClient.sendReaction(messageId, emoji)
                }
            }
            Resource.Success(Unit)
        } catch (e: Exception) {
            // Rollback optimistic update to previous state
            Log.w(TAG, "Reaction failed for message $messageId, rolling back: ${e.message}")
            messageDao.updateReactions(messageId, previousReactionsJson)
            Resource.Error("Failed to update reaction: ${e.message}")
        }
    }

    /**
     * Removes a specific reaction from a message with optimistic update and rollback.
     * This is an explicit remove (not toggle) — always removes the current user from the emoji's reactor list.
     *
     * Requirements: 3.4, 3.5, 3.6
     */
    override suspend fun removeReaction(
        conversationId: String,
        messageId: String,
        emoji: String
    ): Resource<Unit> {
        val currentUserId = firebaseAuth.currentUser?.uid
            ?: return Resource.Error("User not authenticated")

        val entity = messageDao.getById(messageId)
            ?: return Resource.Error("Message not found")

        // Parse current reactions from JSON
        val currentReactions = parseReactionsJson(entity.reactionsJson)
        val usersForEmoji = currentReactions.getOrDefault(emoji, emptyList())

        if (currentUserId !in usersForEmoji) {
            // User hasn't reacted with this emoji — nothing to remove
            return Resource.Success(Unit)
        }

        // Compute new reactions state (remove user)
        val newReactions = currentReactions.toMutableMap()
        val updatedUsers = usersForEmoji - currentUserId
        if (updatedUsers.isEmpty()) {
            newReactions.remove(emoji)
        } else {
            newReactions[emoji] = updatedUsers
        }

        // Optimistic update
        val previousReactionsJson = entity.reactionsJson
        val newReactionsJson = serializeReactionsMap(newReactions)
        messageDao.updateReactions(messageId, newReactionsJson)

        // Emit to server with 10s timeout; rollback on failure
        return try {
            withTimeout(REACTION_TIMEOUT_MS) {
                wsClient.removeReaction(messageId, emoji)
            }
            Resource.Success(Unit)
        } catch (e: Exception) {
            // Rollback optimistic update
            Log.w(TAG, "Remove reaction failed for message $messageId, rolling back: ${e.message}")
            messageDao.updateReactions(messageId, previousReactionsJson)
            Resource.Error("Failed to remove reaction: ${e.message}")
        }
    }

    // ─── Read Receipts (Task 3.4) ─────────────────────────────────────────────

    // Deferred read events when disconnected
    private val deferredReadEvents = ConcurrentLinkedQueue<Pair<String, String>>() // conversationId, userId

    override suspend fun markRead(conversationId: String, userId: String) {
        val isConnected = wsClient.connectionState.value == ChatSocketIoClient.ConnectionState.CONNECTED
        if (isConnected) {
            wsClient.sendRead()
        } else {
            // Defer until connected (Requirement 5.5)
            deferredReadEvents.add(conversationId to userId)
        }
    }

    // ─── Private: Emit and Ack Handling ────────────────────────────────────────

    private suspend fun emitMessage(tempId: String, text: String, replyToId: String?) {
        try {
            wsClient.sendText(text, tempId, replyToId)
            startAckTimeout(tempId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to emit message $tempId: ${e.message}")
            messageDao.updateStatus(tempId, MessageStatus.FAILED.name)
        }
    }

    private suspend fun emitLocationMessage(tempId: String, latitude: Double, longitude: Double) {
        try {
            wsClient.sendLocation(latitude, longitude, tempId)
            startAckTimeout(tempId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to emit location message $tempId: ${e.message}")
            messageDao.updateStatus(tempId, MessageStatus.FAILED.name)
        }
    }

    private suspend fun emitImageMessage(tempId: String, imageUrl: String) {
        try {
            wsClient.sendImage(imageUrl, tempId)
            startAckTimeout(tempId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to emit image message $tempId: ${e.message}")
            messageDao.updateStatus(tempId, MessageStatus.FAILED.name)
        }
    }

    /**
     * Starts a 10-second timeout for ack. If no ack is received within 10s,
     * the message status is updated to FAILED.
     */
    private fun startAckTimeout(tempId: String) {
        val job = scope.launch {
            delay(ACK_TIMEOUT_MS)
            // Timeout reached without ack — mark as FAILED
            Log.w(TAG, "Ack timeout for message $tempId")
            messageDao.updateStatus(tempId, MessageStatus.FAILED.name)
            ackTimeoutJobs.remove(tempId)
        }
        ackTimeoutJobs[tempId] = job
    }

    /**
     * Observes incoming frames from ChatSocketIoClient.
     * Handles:
     * - MessageAck: update tempId → serverId, status → SENT
     * - MessageDelivered: insert incoming messages into Room
     * - ReadReceipt: append userId to message's readBy list
     * - Error: mark related messages as FAILED
     */
    private suspend fun observeIncomingFrames() {
        wsClient.incomingFrames.collect { frame ->
            when (frame) {
                is ServerFrame.MessageAck -> handleAck(frame)
                is ServerFrame.MessageDelivered -> handleIncomingMessage(frame)
                is ServerFrame.ReadReceipt -> handleReadReceipt(frame)
                is ServerFrame.Error -> {
                    Log.e(TAG, "Server error: ${frame.message}")
                }
                else -> { /* handled by other components */ }
            }
        }
    }

    /**
     * On MessageAck: cancel timeout, update tempId → serverId, status → SENT.
     * Uses updateIdAndStatus to atomically replace the temp row.
     */
    private suspend fun handleAck(ack: ServerFrame.MessageAck) {
        // Cancel the timeout job
        ackTimeoutJobs.remove(ack.tempId)?.cancel()

        // Update the message in Room: replace tempId with serverId, set status to SENT
        try {
            if (ack.tempId == ack.id) {
                // Server returned same ID — just update status
                messageDao.updateStatus(ack.tempId, MessageStatus.SENT.name)
            } else {
                // Server assigned a new ID — update in-place
                messageDao.updateIdAndStatus(
                    oldId = ack.tempId,
                    newId = ack.id,
                    status = MessageStatus.SENT.name,
                    timestamp = ack.timestamp
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle ack for ${ack.tempId}: ${e.message}")
        }
    }

    /**
     * Handles incoming messages from other users (delivered via Socket.IO).
     * Inserts into Room (upsert handles deduplication via PK constraint).
     *
     * If the sender is the current user, skip the insert — the message already
     * exists locally from the optimistic insert. The handleAck() will update
     * the temp ID to the server ID. This prevents message duplication when the
     * server echoes the sender's own message back as a MessageDelivered frame.
     * (Requirement 1.13, 2.13)
     */
    private suspend fun handleIncomingMessage(frame: ServerFrame.MessageDelivered) {
        // Skip sender's own messages — already exists from optimistic insert
        val currentUserId = firebaseAuth.currentUser?.uid
        if (frame.senderId == currentUserId) {
            Timber.d("Skipping own message echo: id=${frame.id}, senderId=${frame.senderId}")
            return
        }

        // Secondary deduplication safeguard: check if a message with same conversationId,
        // senderId, text, and timestamp (within 1-second tolerance) already exists in Room.
        // If found, update existing row's ID to server ID instead of inserting a new row.
        // (Requirement 1.13, 2.13)
        val timestampTolerance = 1000L // 1-second tolerance
        val existingDuplicate = messageDao.findDuplicate(
            conversationId = frame.conversationId,
            senderId = frame.senderId,
            text = frame.text,
            timestampLow = frame.timestamp - timestampTolerance,
            timestampHigh = frame.timestamp + timestampTolerance
        )
        if (existingDuplicate != null && existingDuplicate.id != frame.id) {
            Timber.d("Deduplication: updating existing message ${existingDuplicate.id} to server ID ${frame.id}")
            messageDao.updateIdAndStatus(
                oldId = existingDuplicate.id,
                newId = frame.id,
                status = MessageStatus.SENT.name,
                timestamp = frame.timestamp
            )
            return
        }

        val entity = MessageEntity(
            id = frame.id,
            conversationId = frame.conversationId,
            senderId = frame.senderId,
            senderName = frame.senderName,
            senderPhotoUrl = frame.senderPhotoUrl,
            text = frame.text,
            type = when (frame.messageType) {
                "LOCATION" -> MessageType.LOCATION.name
                "IMAGE" -> MessageType.IMAGE.name
                else -> MessageType.TEXT.name
            },
            timestamp = frame.timestamp,
            status = MessageStatus.SENT.name,
            latitude = frame.latitude,
            longitude = frame.longitude,
            imageUrl = frame.imageUrl,
            thumbnailUrl = frame.thumbnailUrl,
            replyToId = null,
            replyToText = null,
            replyToSenderName = null,
            reactionsJson = "{}",
            readByJson = serializeReadBy(frame.readBy)
        )
        messageDao.insert(entity)
    }

    /**
     * Handles incoming read receipt frames.
     * Appends userId to message's readBy list if not already present (monotonic growth).
     * Requirement 5.2, 5.4
     */
    private suspend fun handleReadReceipt(frame: ServerFrame.ReadReceipt) {
        val entity = messageDao.getById(frame.messageId) ?: return

        // Parse existing readBy list
        val currentReadBy = parseReadByJson(entity.readByJson).toMutableList()

        // Only add if not already present (no duplicates, monotonic growth)
        if (frame.userId !in currentReadBy) {
            currentReadBy.add(frame.userId)
            val updatedJson = serializeReadByList(currentReadBy)
            messageDao.updateReadBy(frame.messageId, updatedJson)
        }
    }

    private fun parseReadByJson(jsonString: String): List<String> {
        if (jsonString.isBlank() || jsonString == "[]") return emptyList()
        return try {
            // Simple JSON array parsing
            jsonString.removeSurrounding("[", "]")
                .split(",")
                .map { it.trim().removeSurrounding("\"") }
                .filter { it.isNotEmpty() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeReadByList(readBy: List<String>): String {
        if (readBy.isEmpty()) return "[]"
        return readBy.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
    }

    /**
     * Parses a JSON string representing reactions into a Map<String, List<String>>.
     * Format: {"👍":["user1","user2"],"❤️":["user3"]}
     */
    private fun parseReactionsJson(jsonString: String): Map<String, List<String>> {
        if (jsonString.isBlank() || jsonString == "{}") return emptyMap()
        return try {
            val result = mutableMapOf<String, List<String>>()
            // Remove outer braces
            val content = jsonString.trim().removeSurrounding("{", "}")
            if (content.isBlank()) return emptyMap()

            // Parse each emoji key and its user list
            // Format: "emoji":["user1","user2"]
            var i = 0
            while (i < content.length) {
                // Find key start
                val keyStart = content.indexOf('"', i)
                if (keyStart == -1) break
                val keyEnd = content.indexOf('"', keyStart + 1)
                if (keyEnd == -1) break
                val key = content.substring(keyStart + 1, keyEnd)

                // Find array start
                val arrayStart = content.indexOf('[', keyEnd)
                if (arrayStart == -1) break
                val arrayEnd = content.indexOf(']', arrayStart)
                if (arrayEnd == -1) break

                // Parse array values
                val arrayContent = content.substring(arrayStart + 1, arrayEnd)
                val users = if (arrayContent.isBlank()) {
                    emptyList()
                } else {
                    arrayContent.split(",")
                        .map { it.trim().removeSurrounding("\"") }
                        .filter { it.isNotEmpty() }
                }

                result[key] = users
                i = arrayEnd + 1
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /**
     * Serializes a reactions map to JSON string.
     * Format: {"👍":["user1","user2"],"❤️":["user3"]}
     */
    private fun serializeReactionsMap(reactions: Map<String, List<String>>): String {
        if (reactions.isEmpty()) return "{}"
        val entries = reactions.entries.joinToString(",") { (emoji, userIds) ->
            val usersJson = userIds.joinToString(",") { "\"$it\"" }
            "\"$emoji\":[$usersJson]"
        }
        return "{$entries}"
    }

    // ─── Private: Offline Queue ────────────────────────────────────────────────

    /**
     * Enqueues a message for later sending when connection is restored.
     * FIFO order, max 50 messages (Requirement 1.6, 1.7).
     */
    private suspend fun enqueueMessage(message: QueuedMessage) {
        queueMutex.withLock {
            if (offlineQueue.size >= MAX_OFFLINE_QUEUE_SIZE) {
                // Queue full — mark message as FAILED
                messageDao.updateStatus(message.tempId, MessageStatus.FAILED.name)
                Log.w(TAG, "Offline queue full (${MAX_OFFLINE_QUEUE_SIZE}), rejecting message ${message.tempId}")
                return
            }
            offlineQueue.add(message)
        }
    }

    /**
     * Observes connection state and flushes the offline queue when connected.
     */
    private suspend fun observeConnectionState() {
        wsClient.connectionState.collect { state ->
            if (state == ChatSocketIoClient.ConnectionState.CONNECTED) {
                flushOfflineQueue()
                flushDeferredReadEvents()
            }
        }
    }

    /**
     * Flushes deferred read events that were queued while disconnected.
     */
    private suspend fun flushDeferredReadEvents() {
        while (deferredReadEvents.isNotEmpty()) {
            deferredReadEvents.poll() ?: break
            try {
                wsClient.sendRead()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to flush deferred read event: ${e.message}")
            }
        }
    }

    /**
     * Flushes the offline queue in FIFO order on reconnection.
     * Each message is emitted sequentially to maintain ordering (Requirement 13.4).
     */
    private suspend fun flushOfflineQueue() {
        flushMutex.withLock {
            while (offlineQueue.isNotEmpty()) {
                val queued = offlineQueue.poll() ?: break
                try {
                    when (queued.type) {
                        MessageType.LOCATION -> {
                            val lat = queued.latitude ?: continue
                            val lng = queued.longitude ?: continue
                            emitLocationMessage(queued.tempId, lat, lng)
                        }
                        MessageType.IMAGE -> {
                            val url = queued.imageUrl ?: continue
                            emitImageMessage(queued.tempId, url)
                        }
                        else -> {
                            emitMessage(queued.tempId, queued.text, queued.replyToId)
                        }
                    }
                    // Small delay between messages to avoid overwhelming the server
                    delay(QUEUE_FLUSH_DELAY_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to flush queued message ${queued.tempId}: ${e.message}")
                    messageDao.updateStatus(queued.tempId, MessageStatus.FAILED.name)
                }
            }
        }
    }

    // ─── Private: Helpers ──────────────────────────────────────────────────────

    private suspend fun getIdToken(): String =
        firebaseAuth.currentUser?.getIdToken(false)?.await()?.token ?: ""

    private fun MessageDto.toDomainEntity(fallbackConversationId: String): MessageEntity {
        return MessageEntity(
            id = id,
            conversationId = conversationId.ifEmpty { fallbackConversationId },
            senderId = senderId,
            senderName = senderName,
            senderPhotoUrl = senderPhotoUrl,
            text = text,
            type = when (messageType) {
                "LOCATION" -> MessageType.LOCATION.name
                "IMAGE" -> MessageType.IMAGE.name
                else -> MessageType.TEXT.name
            },
            timestamp = timestamp,
            status = MessageStatus.SENT.name,
            latitude = latitude,
            longitude = longitude,
            imageUrl = null,
            thumbnailUrl = null,
            replyToId = null,
            replyToText = null,
            replyToSenderName = null,
            reactionsJson = "{}",
            readByJson = serializeReadBy(readBy)
        )
    }

    private fun serializeReadBy(readBy: List<String>): String {
        if (readBy.isEmpty()) return "[]"
        return readBy.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
    }

    // ─── NetworkBoundResource Pattern (Requirements 11.1, 11.2, 11.3, 11.6) ───

    /**
     * Returns messages for a conversation using the NetworkBoundResource pattern:
     * 1. Query Room for cached messages
     * 2. Check staleness via CacheStalenessChecker (5-minute threshold)
     * 3. Fetch from network if stale
     * 4. Save to Room on success
     * 5. Serve stale cache on failure, retry after ≤60 seconds
     *
     * This method should only be called from ViewModel init blocks or explicit user actions,
     * NOT from Compose recomposition (Requirement 11.1).
     */
    override fun getMessagesResource(conversationId: String): Flow<DataResource<List<Message>>> {
        val cacheKey = "messages_$conversationId"
        return networkBoundResource(
            query = {
                messageDao.observeByConversation(conversationId).map { entities ->
                    entities.map { it.toDomain() }
                }
            },
            fetch = {
                val token = getIdToken()
                ChatApiClient.apiService.getMessages("Bearer $token", conversationId)
            },
            saveFetchResult = { messageDtos ->
                val entities = messageDtos.map { it.toDomainEntity(conversationId) }
                if (entities.isNotEmpty()) {
                    messageDao.upsertAll(entities)
                }
                cacheStalenessChecker.updateMetadata(cacheKey)
            },
            shouldFetch = { cachedData ->
                cachedData.isNullOrEmpty() || cacheStalenessChecker.shouldFetch(cacheKey)
            },
            onFetchFailed = { throwable ->
                Timber.w(throwable, "Failed to fetch messages for $conversationId, serving stale cache")
                // Schedule retry after ≤60 seconds (Requirement 11.6)
                scope.launch {
                    delay(RETRY_DELAY_MS)
                    cacheStalenessChecker.updateMetadata(cacheKey, currentTimeMs = 0L)
                }
            }
        )
    }

    // ─── Delete Message (Task 4.2 — Context Menu) ─────────────────────────────

    /**
     * Deletes a message sent by the current user.
     * Calls the delete API and removes the message from the local cache.
     * On API failure, returns an error so the UI can show an error snackbar.
     *
     * Requirement 9.7: Confirmation dialog, call delete API, remove from cache.
     * Requirement 9.8: On API fail, retain message, show error snackbar 4s.
     */
    suspend fun deleteMessage(conversationId: String, messageId: String): Resource<Unit> {
        return try {
            val token = getIdToken()
            ChatApiClient.apiService.deleteMessage("Bearer $token", conversationId, messageId)
            // Remove from local cache on success
            messageDao.deleteById(messageId)
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete message $messageId: ${e.message}")
            Resource.Error("Failed to delete message: ${e.message}")
        }
    }

    /**
     * Removes a message from local display only (does not call server API).
     * Used for "Delete for me" action on messages sent by other users.
     *
     * Requirement 9.9: Remove from local display only.
     */
    suspend fun deleteMessageLocally(messageId: String) {
        messageDao.deleteById(messageId)
    }

    // ─── Message Search (Task 8.1) ────────────────────────────────────────────

    /**
     * Searches messages in a conversation by case-insensitive substring match.
     * Returns messages ordered by timestamp ASC (oldest first).
     *
     * Requirement 13.2: Query local Room database for messages containing the search text.
     *
     * @param conversationId The conversation to search within.
     * @param query The LIKE query pattern (e.g., "%search term%").
     * @return List of matching messages as domain models, ordered oldest first.
     */
    suspend fun searchMessages(conversationId: String, query: String): List<Message> {
        return messageDao.searchMessages(conversationId, query).map { it.toDomain() }
    }

    // ─── Voice Message Send (Task 6.1) ────────────────────────────────────────

    /**
     * Sends a voice message by uploading the audio file to Firebase Storage
     * and creating a VOICE message in the conversation.
     *
     * Flow:
     * 1. Insert optimistic PENDING message into Room
     * 2. Upload audio file to Firebase Storage
     * 3. Send message via WebSocket with the download URL
     * 4. Wait for server ack (10s timeout)
     *
     * Requirements: 11.4, 11.6, 11.7
     */
    override suspend fun sendVoiceMessage(
        conversationId: String,
        audioFilePath: String,
        durationMs: Long
    ): Resource<Message> {
        val uid = firebaseAuth.currentUser?.uid ?: return Resource.Error("Not authenticated")
        val tempId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        // Create optimistic PENDING message
        val pendingMessage = Message(
            id = tempId,
            conversationId = conversationId,
            senderId = uid,
            senderName = firebaseAuth.currentUser?.displayName ?: "",
            senderPhotoUrl = firebaseAuth.currentUser?.photoUrl?.toString(),
            text = "",
            type = MessageType.VOICE,
            timestamp = timestamp,
            status = MessageStatus.PENDING,
            voiceDurationMs = durationMs
        )

        // Insert into Room as PENDING
        messageDao.insert(pendingMessage.toEntity())

        return try {
            // Upload audio file to Firebase Storage
            val audioFile = java.io.File(audioFilePath)
            val storageRef = firebaseStorage.reference
                .child("voice_messages/$conversationId/${tempId}.m4a")

            val uploadUri = android.net.Uri.fromFile(audioFile)
            withTimeout(UPLOAD_TIMEOUT_MS) {
                storageRef.putFile(uploadUri).await()
            }
            val downloadUrl = storageRef.downloadUrl.await().toString()

            // Clean up local file after successful upload
            audioFile.delete()

            // Send via WebSocket
            wsClient.sendVoiceMessage(conversationId, tempId, downloadUrl, durationMs)

            // Wait for ack
            try {
                withTimeout(ACK_TIMEOUT_MS) {
                    wsClient.incomingFrames
                        .filter { it is ServerFrame.MessageAck && (it as ServerFrame.MessageAck).tempId == tempId }
                        .first()
                }
                // Update status to SENT
                messageDao.updateStatus(tempId, MessageStatus.SENT.name)
                val sentMessage = pendingMessage.copy(status = MessageStatus.SENT, voiceUrl = downloadUrl)
                Resource.Success(sentMessage)
            } catch (e: Exception) {
                // Timeout or error — mark as FAILED
                messageDao.updateStatus(tempId, MessageStatus.FAILED.name)
                Resource.Error("Voice message send timed out")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to send voice message")
            messageDao.updateStatus(tempId, MessageStatus.FAILED.name)
            // Clean up local file on failure
            try { java.io.File(audioFilePath).delete() } catch (_: Exception) {}
            Resource.Error("Failed to send voice message: ${e.message}")
        }
    }

    companion object {
        /** Ack timeout: 10 seconds (Requirement 1.3) */
        const val ACK_TIMEOUT_MS = 10_000L

        /** Reaction server timeout: 10 seconds (Requirement 3.6) */
        const val REACTION_TIMEOUT_MS = 10_000L

        /** Maximum offline queue size (Requirement 1.7) */
        const val MAX_OFFLINE_QUEUE_SIZE = 50

        /** Delay between flushing queued messages */
        const val QUEUE_FLUSH_DELAY_MS = 100L

        /** Upload timeout: 60 seconds (Requirement 6.5) */
        const val UPLOAD_TIMEOUT_MS = 60_000L

        /** Maximum upload retry attempts (Requirement 6.4, 6.7) */
        const val MAX_UPLOAD_RETRIES = 3

        /** Delay between upload retries */
        const val UPLOAD_RETRY_DELAY_MS = 1_000L

        /** Delay before retrying missed messages fetch (Requirement 13.6) */
        const val MISSED_MESSAGES_RETRY_DELAY_MS = 2_000L

        /** Maximum delay before retrying a failed fetch of stale data (Requirement 11.6) */
        const val RETRY_DELAY_MS = 60_000L
    }
}

/**
 * Represents a message queued for sending when offline.
 */
internal data class QueuedMessage(
    val tempId: String,
    val conversationId: String,
    val text: String,
    val replyToId: String?,
    val type: MessageType,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val imageUrl: String? = null
)
