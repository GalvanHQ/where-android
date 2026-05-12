package com.ovi.where.data.local.entity

import com.ovi.where.domain.model.Message
import com.ovi.where.domain.model.MessageStatus
import com.ovi.where.domain.model.MessageType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val json = Json { ignoreUnknownKeys = true }

fun MessageEntity.toDomain(): Message {
    return Message(
        id = id,
        conversationId = conversationId,
        senderId = senderId,
        senderName = senderName,
        senderPhotoUrl = senderPhotoUrl,
        text = text,
        type = parseMessageType(type),
        timestamp = timestamp,
        status = parseMessageStatus(status),
        latitude = latitude,
        longitude = longitude,
        imageUrl = imageUrl,
        thumbnailUrl = thumbnailUrl,
        replyToId = replyToId,
        replyToText = replyToText,
        replyToSenderName = replyToSenderName,
        reactions = parseReactionsJson(reactionsJson),
        readBy = parseReadByJson(readByJson)
    )
}

fun Message.toEntity(): MessageEntity {
    return MessageEntity(
        id = id,
        conversationId = conversationId,
        senderId = senderId,
        senderName = senderName,
        senderPhotoUrl = senderPhotoUrl,
        text = text,
        type = type.name,
        timestamp = timestamp,
        status = status.name,
        latitude = latitude,
        longitude = longitude,
        imageUrl = imageUrl,
        thumbnailUrl = thumbnailUrl,
        replyToId = replyToId,
        replyToText = replyToText,
        replyToSenderName = replyToSenderName,
        reactionsJson = serializeReactions(reactions),
        readByJson = serializeReadBy(readBy)
    )
}

private fun parseMessageType(value: String): MessageType {
    return try {
        MessageType.valueOf(value)
    } catch (_: IllegalArgumentException) {
        MessageType.TEXT
    }
}

private fun parseMessageStatus(value: String): MessageStatus {
    return try {
        MessageStatus.valueOf(value)
    } catch (_: IllegalArgumentException) {
        MessageStatus.SENT
    }
}

private fun parseReactionsJson(jsonString: String): Map<String, List<String>> {
    if (jsonString.isBlank() || jsonString == "{}") return emptyMap()
    return try {
        val jsonObject = json.parseToJsonElement(jsonString).jsonObject
        jsonObject.mapValues { (_, value) ->
            value.jsonArray.map { it.jsonPrimitive.content }
        }
    } catch (_: Exception) {
        emptyMap()
    }
}

private fun parseReadByJson(jsonString: String): List<String> {
    if (jsonString.isBlank() || jsonString == "[]") return emptyList()
    return try {
        val jsonArray = json.parseToJsonElement(jsonString).jsonArray
        jsonArray.map { it.jsonPrimitive.content }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun serializeReactions(reactions: Map<String, List<String>>): String {
    if (reactions.isEmpty()) return "{}"
    val jsonObject = JsonObject(
        reactions.mapValues { (_, userIds) ->
            JsonArray(userIds.map { JsonPrimitive(it) })
        }
    )
    return jsonObject.toString()
}

private fun serializeReadBy(readBy: List<String>): String {
    if (readBy.isEmpty()) return "[]"
    val jsonArray = JsonArray(readBy.map { JsonPrimitive(it) })
    return jsonArray.toString()
}
