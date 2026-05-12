package com.ovi.where.data.local.entity

import com.ovi.where.domain.model.Conversation
import com.ovi.where.domain.model.ConversationType
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
        unreadCounts = mapOf(id to unreadCount)
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
        unreadCount = unreadCounts.values.firstOrNull() ?: 0,
        memberIdsJson = serializeMemberIds(participantIds),
        lastSyncTimestamp = System.currentTimeMillis()
    )
}

private fun parseConversationType(value: String): ConversationType {
    return try {
        ConversationType.valueOf(value)
    } catch (_: IllegalArgumentException) {
        ConversationType.DIRECT
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
