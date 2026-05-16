package com.ovi.where.data.local.entity

import com.ovi.where.domain.model.Conversation
import com.ovi.where.domain.model.ConversationType
import com.ovi.where.domain.model.MessageStatus
import com.ovi.where.domain.model.MessageType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

private val json = Json { ignoreUnknownKeys = true }

fun ConversationEntity.toDomain(): Conversation {
    return Conversation(
        id = id,
        name = name,
        type = parseConversationType(type),
        participantIds = parseMemberIdsJson(memberIdsJson),
        groupId = groupId,
        photoUrl = photoUrl,
        lastMessageText = lastMessageText,
        lastMessageSenderId = lastMessageSenderId,
        lastMessageTimestamp = lastMessageTimestamp,
        lastMessageType = parseMessageType(lastMessageType),
        lastMessageStatus = parseMessageStatus(lastMessageStatus),
        unreadCounts = mapOf(id to unreadCount),
        mutedBy = parseMemberIdsJson(mutedByJson),
        pinnedBy = parseMemberIdsJson(pinnedByJson)
    )
}

fun Conversation.toEntity(): ConversationEntity {
    return ConversationEntity(
        id = id,
        name = name,
        type = type.name,
        photoUrl = photoUrl,
        groupId = groupId,
        lastMessageText = lastMessageText,
        lastMessageTimestamp = lastMessageTimestamp,
        lastMessageSenderId = lastMessageSenderId,
        lastMessageType = lastMessageType.name,
        lastMessageStatus = lastMessageStatus.name,
        unreadCount = unreadCounts.values.firstOrNull() ?: 0,
        memberIdsJson = serializeMemberIds(participantIds),
        mutedByJson = serializeMemberIds(mutedBy),
        pinnedByJson = serializeMemberIds(pinnedBy),
        lastSyncTimestamp = System.currentTimeMillis(),
        documentUpdateTime = 0L
    )
}

private fun parseConversationType(value: String): ConversationType {
    return try {
        ConversationType.valueOf(value)
    } catch (_: IllegalArgumentException) {
        ConversationType.DIRECT
    }
}

private fun parseMessageType(value: String?): MessageType {
    if (value.isNullOrBlank()) return MessageType.TEXT
    return try {
        MessageType.valueOf(value)
    } catch (_: IllegalArgumentException) {
        MessageType.TEXT
    }
}

private fun parseMessageStatus(value: String?): MessageStatus {
    if (value.isNullOrBlank()) return MessageStatus.SENT
    return try {
        MessageStatus.valueOf(value)
    } catch (_: IllegalArgumentException) {
        MessageStatus.SENT
    }
}

private fun parseMemberIdsJson(jsonString: String): List<String> {
    if (jsonString.isBlank() || jsonString == "[]") return emptyList()
    return try {
        val jsonArray = json.parseToJsonElement(jsonString).jsonArray
        jsonArray.map { it.jsonPrimitive.content }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun serializeMemberIds(memberIds: List<String>): String {
    if (memberIds.isEmpty()) return "[]"
    val jsonArray = JsonArray(memberIds.map { JsonPrimitive(it) })
    return jsonArray.toString()
}
