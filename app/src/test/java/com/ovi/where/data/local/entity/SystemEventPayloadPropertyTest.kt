package com.ovi.where.data.local.entity

import com.ovi.where.domain.model.Message
import com.ovi.where.domain.model.MessageStatus
import com.ovi.where.domain.model.MessageType
import com.ovi.where.domain.model.SystemEventType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropertyTesting
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/**
 * Feature: group-system-messages
 * Property 1: Payload round-trip lossless
 *
 * Validates: Requirements 1.1, 2.4
 */
class SystemEventPayloadPropertyTest : StringSpec({

    PropertyTesting.defaultIterationCount = 200

    val payloadArb: Arb<Map<String, String>> = Arb.map(
        keyArb = Arb.string(minSize = 1, maxSize = 16),
        valueArb = Arb.string(minSize = 0, maxSize = 64),
        minSize = 0,
        maxSize = 8
    )

    "system message round-trip preserves payload, eventType, and targetUserId" {
        checkAll(
            Arb.enum<SystemEventType>(),
            payloadArb,
            Arb.string(minSize = 1, maxSize = 32)
        ) { eventType, payload, targetUid ->

            val original = Message(
                id = "sys_1",
                conversationId = "conv_1",
                senderId = "actor_1",
                senderName = "Ovi",
                text = "fallback English",
                type = MessageType.SYSTEM,
                timestamp = 1_000L,
                status = MessageStatus.SENT,
                systemEventType = eventType,
                systemEventPayload = payload,
                targetUserId = targetUid
            )

            val roundTripped = original.toEntity().toDomain()

            roundTripped.systemEventType shouldBe eventType
            roundTripped.systemEventPayload shouldBe payload
            roundTripped.targetUserId shouldBe targetUid
            roundTripped.type shouldBe MessageType.SYSTEM
            roundTripped.text shouldBe original.text
        }
    }

    "non-system messages keep null systemEventType after round-trip" {
        val msg = Message(
            id = "txt_1",
            conversationId = "conv_1",
            senderId = "user_1",
            senderName = "Ovi",
            text = "hello",
            type = MessageType.TEXT,
            timestamp = 1_000L,
            status = MessageStatus.SENT
        )

        val rt = msg.toEntity().toDomain()
        rt.systemEventType shouldBe null
        rt.systemEventPayload shouldBe emptyMap()
        rt.targetUserId shouldBe null
    }

    "malformed payload JSON is treated as empty without dropping the row" {
        val entity = MessageEntity(
            id = "sys_1",
            conversationId = "conv_1",
            senderId = "actor_1",
            senderName = "Ovi",
            senderPhotoUrl = null,
            text = "fallback",
            type = MessageType.SYSTEM.name,
            timestamp = 1_000L,
            status = MessageStatus.SENT.name,
            latitude = null, longitude = null,
            imageUrl = null, thumbnailUrl = null,
            replyToId = null, replyToText = null, replyToSenderName = null,
            reactionsJson = "{}",
            readByJson = "[]",
            systemEventType = SystemEventType.GROUP_RENAMED.name,
            systemEventPayload = "{not valid json",
            targetUserId = null
        )

        val msg = entity.toDomain()
        msg.systemEventType shouldBe SystemEventType.GROUP_RENAMED
        msg.systemEventPayload shouldBe emptyMap()
    }
})
