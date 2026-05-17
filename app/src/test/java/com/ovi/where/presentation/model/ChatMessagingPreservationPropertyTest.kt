package com.ovi.where.presentation.model

import com.ovi.where.domain.model.Conversation
import com.ovi.where.domain.model.ConversationType
import com.ovi.where.domain.model.MessageStatus
import com.ovi.where.domain.model.MessageType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.property.Arb
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import java.util.Calendar

/**
 * Preservation Property Tests - Existing Chat Behavior Preservation
 *
 * **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 3.9, 3.10**
 *
 * These tests capture the BASELINE behavior of the UNFIXED code for non-buggy inputs.
 * They MUST PASS on the unfixed code, confirming that these behaviors are preserved
 * after the fix is applied.
 *
 * Methodology: Observation-first
 * Step 1 - Observed behaviors on unfixed code:
 *   - Group conversation with name = "Team Chat" and type = GROUP → toUiModel() returns title "Team Chat"
 *   - formatConversationTimestamp() with same-day timestamp → returns "HH:mm" format
 *   - formatConversationTimestamp() with timestamp from 2 days ago (same month) → returns day-of-week name (actually dd/MM/yy)
 *   - For all non-DM conversations, toUiModel() produces the same output regardless of participantNames map content
 *
 * Step 2 - Property-based tests encoding observed behavior.
 */
class ChatMessagingPreservationPropertyTest : StringSpec({

    val timeFormatter: (Long) -> String = { formatConversationTimestamp(it) }

    // ── Generators ──────────────────────────────────────────────────────────────

    /** Generates non-blank group names (1-30 alphanumeric chars). */
    val groupNameArb: Arb<String> = Arb.string(minSize = 1, maxSize = 30, codepoints = Codepoint.alphanumeric())

    /** Generates user IDs. */
    val userIdArb: Arb<String> = Arb.string(minSize = 5, maxSize = 15, codepoints = Codepoint.alphanumeric())

    /** Generates participant ID lists for groups (3-8 members). */
    val groupParticipantIdsArb: Arb<List<String>> = Arb.list(userIdArb, range = 3..8)

    /** Generates a same-day timestamp (today, random hour/minute). */
    val sameDayTimestampArb: Arb<Long> = arbitrary {
        val hour = Arb.int(0..23).bind()
        val minute = Arb.int(0..59).bind()
        val second = Arb.int(0..59).bind()
        val now = Calendar.getInstance()
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, now.get(Calendar.YEAR))
            set(Calendar.MONTH, now.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, now.get(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, second)
            set(Calendar.MILLISECOND, 0)
        }
        // Ensure the timestamp is not in the future
        val ts = cal.timeInMillis
        if (ts > System.currentTimeMillis()) {
            System.currentTimeMillis() - 60_000L // fallback to 1 minute ago
        } else {
            ts
        }
    }

    /** Generates a participantNames map with random content. */
    val participantNamesMapArb: Arb<Map<String, String>> = arbitrary {
        val size = Arb.int(0..5).bind()
        val map = mutableMapOf<String, String>()
        repeat(size) {
            val key = Arb.string(minSize = 3, maxSize = 10, codepoints = Codepoint.alphanumeric()).bind()
            val value = Arb.string(minSize = 1, maxSize = 20, codepoints = Codepoint.alphanumeric()).bind()
            map[key] = value
        }
        map
    }

    // ─── Property: Group conversations resolve title to group name unchanged ────

    "Property: For all group conversations, resolveConversationTitle() returns the group name unchanged" {
        /**
         * Observed behavior: Group conversations with a non-blank name always display
         * that name as the title, regardless of participantNames content.
         *
         * **Validates: Requirements 3.1**
         */
        checkAll(iterations = 200, groupNameArb, participantNamesMapArb) { groupName, participantNames ->
            val result = resolveConversationTitle(
                name = groupName,
                type = ConversationType.GROUP,
                otherUserId = null,
                participantNames = participantNames
            )
            result shouldBe groupName
        }
    }

    // ─── Property: Same-day timestamps return "HH:mm" format ────────────────────

    "Property: For all same-day timestamps, formatConversationTimestamp() returns HH:mm format" {
        /**
         * Observed behavior: Timestamps from today always return "HH:mm" format
         * (e.g., "14:32", "09:05", "23:59").
         *
         * **Validates: Requirements 3.8**
         */
        checkAll(iterations = 200, sameDayTimestampArb) { timestamp ->
            val result = formatConversationTimestamp(timestamp)
            // HH:mm format: two digits, colon, two digits
            result shouldMatch Regex("\\d{2}:\\d{2}")
        }
    }

    // ─── Property: Non-DM conversations produce same output regardless of participantNames ──

    "Property: For all non-DM conversations, toUiModel() produces the same output regardless of participantNames map content" {
        /**
         * Observed behavior: Group conversations ignore the participantNames map entirely.
         * The title comes from the conversation name, not from participant resolution.
         *
         * **Validates: Requirements 3.1, 3.9**
         */
        val currentUserId = "currentUser"

        checkAll(iterations = 150, groupNameArb, groupParticipantIdsArb, participantNamesMapArb, participantNamesMapArb) {
                groupName, participantIds, namesMap1, namesMap2 ->
            val conversation = Conversation(
                id = "group-conv-1",
                name = groupName,
                type = ConversationType.GROUP,
                participantIds = listOf(currentUserId) + participantIds,
                groupId = "group-1",
                lastMessageTimestamp = System.currentTimeMillis() - 60_000L,
                lastMessageText = "Hello group"
            )

            val result1 = conversation.toUiModel(currentUserId, timeFormatter, namesMap1)
            val result2 = conversation.toUiModel(currentUserId, timeFormatter, namesMap2)

            // Title should be the same regardless of participantNames
            result1.title shouldBe result2.title
            result1.title shouldBe groupName
            // isGroup should be true
            result1.isGroup shouldBe true
            result2.isGroup shouldBe true
        }
    }

    // ─── Property: Group conversation memberCount reflects participantIds size ───

    "Property: For all group conversations, toUiModel() memberCount equals participantIds.size" {
        /**
         * Observed behavior: The memberCount field in ConversationUiModel is set to
         * participantIds.size, which represents the number of members in the group.
         *
         * **Validates: Requirements 3.1**
         */
        val currentUserId = "currentUser"

        checkAll(iterations = 150, groupNameArb, groupParticipantIdsArb) { groupName, otherParticipants ->
            val allParticipants = listOf(currentUserId) + otherParticipants
            val conversation = Conversation(
                id = "group-conv-2",
                name = groupName,
                type = ConversationType.GROUP,
                participantIds = allParticipants,
                groupId = "group-2"
            )

            val result = conversation.toUiModel(currentUserId, timeFormatter)
            result.memberCount shouldBe allParticipants.size
        }
    }

    // ─── Property: Pinned conversations are correctly identified ─────────────────

    "Property: For all conversation lists, isPinned correctly reflects pinnedBy containing currentUserId" {
        /**
         * Observed behavior: A conversation is marked as pinned in the UI model
         * if and only if the current user's ID is in the pinnedBy list.
         * This is the basis for pinned-first ordering in applySearchFilter().
         *
         * **Validates: Requirements 3.8**
         */
        val currentUserId = "currentUser"

        checkAll(iterations = 200, groupNameArb, Arb.element(true, false)) { name, shouldBePinned ->
            val pinnedBy = if (shouldBePinned) listOf(currentUserId) else emptyList()
            val conversation = Conversation(
                id = "conv-pin-test",
                name = name,
                type = ConversationType.GROUP,
                participantIds = listOf(currentUserId, "other1", "other2"),
                pinnedBy = pinnedBy
            )

            val result = conversation.toUiModel(currentUserId, timeFormatter)
            result.isPinned shouldBe shouldBePinned
        }
    }

    // ─── Property: Muted conversations are correctly identified ──────────────────

    "Property: For all conversations, isMuted correctly reflects mutedBy containing currentUserId" {
        /**
         * Observed behavior: A conversation is marked as muted in the UI model
         * if and only if the current user's ID is in the mutedBy list.
         *
         * **Validates: Requirements 3.8**
         */
        val currentUserId = "currentUser"

        checkAll(iterations = 200, groupNameArb, Arb.element(true, false)) { name, shouldBeMuted ->
            val mutedBy = if (shouldBeMuted) listOf(currentUserId) else emptyList()
            val conversation = Conversation(
                id = "conv-mute-test",
                name = name,
                type = ConversationType.GROUP,
                participantIds = listOf(currentUserId, "other1", "other2"),
                mutedBy = mutedBy
            )

            val result = conversation.toUiModel(currentUserId, timeFormatter)
            result.isMuted shouldBe shouldBeMuted
        }
    }

    // ─── Property: formatConversationTimestamp returns non-blank for valid timestamps ─

    "Property: For all valid timestamps, formatConversationTimestamp() returns a non-blank string" {
        /**
         * Observed behavior: Any non-zero timestamp produces a non-blank formatted string.
         * This ensures the timestamp display never disappears.
         *
         * **Validates: Requirements 3.2**
         */
        // Generate timestamps from the past year to now
        val pastYearTimestampArb = Arb.long(
            min = System.currentTimeMillis() - 365L * 24 * 60 * 60 * 1000,
            max = System.currentTimeMillis()
        )

        checkAll(iterations = 200, pastYearTimestampArb) { timestamp ->
            val result = formatConversationTimestamp(timestamp)
            result.shouldNotBeBlank()
        }
    }

    // ─── Property: Zero timestamp returns empty string ──────────────────────────

    "Property: formatConversationTimestamp(0) always returns empty string" {
        /**
         * Observed behavior: A timestamp of 0 (no message) returns empty string.
         *
         * **Validates: Requirements 3.2**
         */
        formatConversationTimestamp(0L) shouldBe ""
    }

    // ─── Property: formatMessageTime always includes HH:mm time component ────────────────

    "Property: For all same-day timestamps, formatMessageTime() returns HH:mm format" {
        /**
         * Preservation: formatMessageTime() returns just "HH:mm" for today's messages.
         * For non-today messages, it includes date context but always contains the time.
         *
         * **Validates: Requirements 3.2**
         */
        val now = Calendar.getInstance()
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayTimestampArb = Arb.long(
            min = startOfToday.timeInMillis,
            max = now.timeInMillis
        )

        checkAll(iterations = 200, todayTimestampArb) { timestamp ->
            val result = formatMessageTime(timestamp)
            // Today's messages should be just "HH:mm"
            result shouldMatch Regex("\\d{2}:\\d{2}")
        }
    }

    // ─── Property: DM conversations with non-blank name use that name as title ──

    "Property: For all DM conversations with non-blank name, resolveConversationTitle() returns the name unchanged" {
        /**
         * Observed behavior: When a DM conversation has a non-blank name (pre-populated),
         * that name is used directly as the title without any participant resolution.
         *
         * **Validates: Requirements 3.9**
         */
        val nonBlankNameArb = Arb.string(minSize = 1, maxSize = 30, codepoints = Codepoint.alphanumeric())

        checkAll(iterations = 200, nonBlankNameArb, userIdArb, participantNamesMapArb) { name, otherUserId, participantNames ->
            val result = resolveConversationTitle(
                name = name,
                type = ConversationType.DIRECT,
                otherUserId = otherUserId,
                participantNames = participantNames
            )
            result shouldBe name
        }
    }

    // ─── Property: Last message status only set for current user's messages ─────

    "Property: For all conversations, lastMessageStatus is non-null only when last message is from current user" {
        /**
         * Observed behavior: The lastMessageStatus field in ConversationUiModel is only
         * populated when the last message was sent by the current user. For messages
         * from other users, it is null.
         *
         * **Validates: Requirements 3.2, 3.10**
         */
        val currentUserId = "currentUser"
        val statusArb = Arb.element(MessageStatus.PENDING, MessageStatus.SENT, MessageStatus.DELIVERED, MessageStatus.READ, MessageStatus.FAILED)

        checkAll(iterations = 200, groupNameArb, Arb.element(currentUserId, "otherUser"), statusArb) { name, senderId, status ->
            val conversation = Conversation(
                id = "conv-status-test",
                name = name,
                type = ConversationType.GROUP,
                participantIds = listOf(currentUserId, "otherUser", "thirdUser"),
                lastMessageSenderId = senderId,
                lastMessageStatus = status,
                lastMessageTimestamp = System.currentTimeMillis() - 60_000L,
                lastMessageText = "test message"
            )

            val result = conversation.toUiModel(currentUserId, timeFormatter)

            if (senderId == currentUserId) {
                result.lastMessageStatus shouldBe status
                result.isLastMessageFromCurrentUser shouldBe true
            } else {
                result.lastMessageStatus shouldBe null
                result.isLastMessageFromCurrentUser shouldBe false
            }
        }
    }

    // ─── Property: Unread count correctly extracted from unreadCounts map ────────

    "Property: For all conversations, unreadCount is correctly extracted from unreadCounts map" {
        /**
         * Observed behavior: The unread count for the current user is extracted from
         * the unreadCounts map. If not present, defaults to 0.
         *
         * **Validates: Requirements 3.7**
         */
        val currentUserId = "currentUser"
        val unreadCountArb = Arb.int(0..99)

        checkAll(iterations = 200, groupNameArb, unreadCountArb, Arb.element(true, false)) { name, count, hasEntry ->
            val unreadCounts: Map<String, Int> = if (hasEntry) mapOf(currentUserId to count) else emptyMap()
            val conversation = Conversation(
                id = "conv-unread-test",
                name = name,
                type = ConversationType.GROUP,
                participantIds = listOf(currentUserId, "other1"),
                unreadCounts = unreadCounts
            )

            val result = conversation.toUiModel(currentUserId, timeFormatter)
            if (hasEntry) {
                result.unreadCount shouldBe count
            } else {
                result.unreadCount shouldBe 0
            }
        }
    }
})
