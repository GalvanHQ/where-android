package com.ovi.where.server.service

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseToken
import com.google.firebase.cloud.FirestoreClient
import com.ovi.where.server.model.ConversationDto
import com.ovi.where.server.model.MessageDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

object FirebaseAdminService {

    private lateinit var db: Firestore

    fun init() {
        if (FirebaseApp.getApps().isNotEmpty()) return

        // Loads GOOGLE_APPLICATION_CREDENTIALS env var automatically
        // or falls back to the serviceAccount file in the classpath
        val credentials = try {
            GoogleCredentials.getApplicationDefault()
        } catch (e: Exception) {
            // Fallback for local dev: load from file
            val stream = FirebaseAdminService::class.java.classLoader
                .getResourceAsStream("serviceAccount.json")
            requireNotNull(stream) {
                "Firebase credentials not found. Set GOOGLE_APPLICATION_CREDENTIALS env var " +
                        "or place serviceAccount.json in server/src/main/resources/"
            }
            GoogleCredentials.fromStream(stream)
        }

        val options = FirebaseOptions.builder()
            .setCredentials(credentials)
            .build()

        FirebaseApp.initializeApp(options)
        db = FirestoreClient.getFirestore()
    }

    // ── Auth ─────────────────────────────────────────────────────────────────

    fun verifyToken(idToken: String): FirebaseToken? = try {
        FirebaseAuth.getInstance().verifyIdToken(idToken)
    } catch (e: Exception) {
        null
    }

    // ── Conversations ─────────────────────────────────────────────────────────

    suspend fun getConversationsForUser(userId: String): List<ConversationDto> =
        withContext(Dispatchers.IO) {
            db.collection("conversations")
                .whereArrayContains("participantIds", userId)
                .orderBy("lastMessageTimestamp", com.google.cloud.firestore.Query.Direction.DESCENDING)
                .get()
                .get()
                .documents
                .mapNotNull { it.toConversationDto(userId) }
        }

    suspend fun getOrCreateDirectConversation(
        user1Id: String,
        user2Id: String
    ): ConversationDto = withContext(Dispatchers.IO) {
        // Check if conversation already exists
        val existing = db.collection("conversations")
            .whereEqualTo("type", "direct")
            .whereArrayContains("participantIds", user1Id)
            .get().get()
            .documents
            .firstOrNull { doc ->
                val ids = doc.get("participantIds") as? List<*>
                ids != null && ids.contains(user2Id) && ids.size == 2
            }

        if (existing != null) {
            return@withContext existing.toConversationDto(user1Id)
                ?: throw IllegalStateException("Conversation mapping failed")
        }

        // Fetch user names
        val user1Doc = db.collection("users").document(user1Id).get().get()
        val user2Doc = db.collection("users").document(user2Id).get().get()
        val user2Name = user2Doc.getString("displayName") ?: "Unknown"
        val user2Photo = user2Doc.getString("photoUrl")

        val convId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val data = mapOf(
            "id" to convId,
            "type" to "direct",
            "participantIds" to listOf(user1Id, user2Id),
            "name" to user2Name,
            "photoUrl" to user2Photo,
            "lastMessageText" to "",
            "lastMessageSenderId" to "",
            "lastMessageTimestamp" to now,
            "unreadCounts" to mapOf(user1Id to 0, user2Id to 0),
            "createdAt" to now
        )
        db.collection("conversations").document(convId).set(data).get()

        ConversationDto(
            id = convId,
            type = "direct",
            participantIds = listOf(user1Id, user2Id),
            name = user2Name,
            photoUrl = user2Photo,
            lastMessageTimestamp = now,
            createdAt = now
        )
    }

    suspend fun createGroupConversation(
        groupId: String,
        name: String,
        memberIds: List<String>
    ): ConversationDto = withContext(Dispatchers.IO) {
        // Check if conversation already exists for this group
        val existing = db.collection("conversations")
            .whereEqualTo("groupId", groupId)
            .get().get()
            .documents
            .firstOrNull()

        if (existing != null) {
            return@withContext existing.toConversationDto(memberIds.firstOrNull() ?: "")
                ?: throw IllegalStateException("Group conversation mapping failed")
        }

        val convId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val unreadCounts = memberIds.associateWith { 0 }
        val data = mapOf(
            "id" to convId,
            "type" to "group",
            "participantIds" to memberIds,
            "groupId" to groupId,
            "name" to name,
            "photoUrl" to null,
            "lastMessageText" to "",
            "lastMessageSenderId" to "",
            "lastMessageTimestamp" to now,
            "unreadCounts" to unreadCounts,
            "createdAt" to now
        )
        db.collection("conversations").document(convId).set(data).get()

        // Also update the group doc with conversationId
        db.collection("groups").document(groupId)
            .update("conversationId", convId).get()

        ConversationDto(
            id = convId,
            type = "group",
            participantIds = memberIds,
            groupId = groupId,
            name = name,
            lastMessageTimestamp = now,
            createdAt = now
        )
    }

    suspend fun markConversationRead(
        conversationId: String,
        userId: String
    ) = withContext(Dispatchers.IO) {
        db.collection("conversations").document(conversationId)
            .update("unreadCounts.$userId", 0)
            .get()
    }

    // ── Messages ──────────────────────────────────────────────────────────────

    suspend fun getMessages(
        conversationId: String,
        limit: Int = 50
    ): List<MessageDto> = withContext(Dispatchers.IO) {
        db.collection("conversations")
            .document(conversationId)
            .collection("messages")
            .orderBy("timestamp", com.google.cloud.firestore.Query.Direction.DESCENDING)
            .limit(limit)
            .get().get()
            .documents
            .mapNotNull { it.toMessageDto() }
            .reversed()
    }

    suspend fun saveMessage(message: MessageDto): MessageDto = withContext(Dispatchers.IO) {
        val msgId = message.id.ifEmpty { UUID.randomUUID().toString() }
        val msg = message.copy(id = msgId)

        // Save to messages subcollection
        db.collection("conversations")
            .document(msg.conversationId)
            .collection("messages")
            .document(msgId)
            .set(msg.toFirestoreMap())
            .get()

        // Update conversation's lastMessage + increment unread for other participants
        val convDoc = db.collection("conversations")
            .document(msg.conversationId).get().get()
        val participantIds = convDoc.get("participantIds") as? List<*> ?: emptyList<String>()

        val updates = mutableMapOf<String, Any>(
            "lastMessageText" to (if (msg.messageType == "LOCATION") "📍 Location" else msg.text),
            "lastMessageSenderId" to msg.senderId,
            "lastMessageTimestamp" to msg.timestamp
        )
        // Increment unread count for all participants except the sender
        participantIds.filterIsInstance<String>()
            .filter { it != msg.senderId }
            .forEach { uid ->
                val current = (convDoc.get("unreadCounts.$uid") as? Long)?.toInt() ?: 0
                updates["unreadCounts.$uid"] = current + 1
            }

        db.collection("conversations").document(msg.conversationId)
            .update(updates).get()

        msg
    }

    suspend fun isParticipant(conversationId: String, userId: String): Boolean =
        withContext(Dispatchers.IO) {
            val doc = db.collection("conversations").document(conversationId).get().get()
            val ids = doc.get("participantIds") as? List<*>
            ids?.contains(userId) == true
        }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun DocumentSnapshot.toConversationDto(currentUserId: String): ConversationDto? {
        return try {
            val unreadMap = get("unreadCounts") as? Map<*, *>
            val unread = (unreadMap?.get(currentUserId) as? Long)?.toInt() ?: 0
            ConversationDto(
                id = id,
                type = getString("type") ?: "direct",
                participantIds = (get("participantIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                groupId = getString("groupId"),
                name = getString("name") ?: "",
                photoUrl = getString("photoUrl"),
                lastMessageText = getString("lastMessageText") ?: "",
                lastMessageSenderId = getString("lastMessageSenderId") ?: "",
                lastMessageTimestamp = getLong("lastMessageTimestamp") ?: 0L,
                unreadCount = unread,
                createdAt = getLong("createdAt") ?: 0L
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun DocumentSnapshot.toMessageDto(): MessageDto? {
        return try {
            MessageDto(
                id = id,
                conversationId = getString("conversationId") ?: "",
                senderId = getString("senderId") ?: "",
                senderName = getString("senderName") ?: "",
                senderPhotoUrl = getString("senderPhotoUrl"),
                text = getString("text") ?: "",
                messageType = getString("messageType") ?: "TEXT",
                latitude = getDouble("latitude"),
                longitude = getDouble("longitude"),
                timestamp = getLong("timestamp") ?: 0L,
                readBy = (get("readBy") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun MessageDto.toFirestoreMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "conversationId" to conversationId,
        "senderId" to senderId,
        "senderName" to senderName,
        "senderPhotoUrl" to senderPhotoUrl,
        "text" to text,
        "messageType" to messageType,
        "latitude" to latitude,
        "longitude" to longitude,
        "timestamp" to timestamp,
        "readBy" to readBy
    )
}
