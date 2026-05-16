package com.ovi.where.presentation.chat

/**
 * MentionEngine handles @mention detection, suggestion filtering, and token management
 * for group conversations.
 *
 * Responsibilities:
 * - Detect "@" trigger in input text and extract the query string
 * - Filter group members by prefix match (case-insensitive) on display name
 * - Limit suggestions to 5 when group has > 10 members
 * - Insert mention tokens (replacing "@query" with display name)
 * - Track mentioned user IDs for the message payload
 * - Handle token deletion (remove entire token + userId)
 *
 * Requirements: 14.1, 14.2, 14.3, 14.4, 14.5, 14.6, 14.7, 14.8
 */
class MentionEngine {

    /**
     * Represents a mention token inserted into the input text.
     *
     * @param userId The user ID of the mentioned member
     * @param displayName The display name shown in the token
     * @param startIndex The start index of the token in the input text
     * @param endIndex The end index (exclusive) of the token in the input text
     */
    data class MentionToken(
        val userId: String,
        val displayName: String,
        val startIndex: Int,
        val endIndex: Int
    )

    /**
     * Represents a member available for mention suggestions.
     */
    data class MentionMember(
        val userId: String,
        val displayName: String,
        val photoUrl: String? = null
    )

    /**
     * Result of inserting a mention into the input text.
     *
     * @param newText The updated input text with the mention token inserted
     * @param newCursorPosition The cursor position after the inserted token
     * @param mentionToken The token that was inserted
     */
    data class MentionInsertResult(
        val newText: String,
        val newCursorPosition: Int,
        val mentionToken: MentionToken
    )

    /**
     * Result of detecting an active mention query in the input text.
     *
     * @param isActive Whether an "@" trigger is active at the cursor position
     * @param query The text after "@" (empty string if just "@" typed)
     * @param triggerStartIndex The index of the "@" character in the text
     */
    data class MentionQueryResult(
        val isActive: Boolean,
        val query: String = "",
        val triggerStartIndex: Int = -1
    )

    // Currently active mention tokens in the input
    private val _mentionTokens = mutableListOf<MentionToken>()
    val mentionTokens: List<MentionToken> get() = _mentionTokens.toList()

    /**
     * Returns deduplicated list of mentioned user IDs for the message payload.
     *
     * Requirement 14.3: Include deduplicated array of mentioned user IDs
     * (one entry per distinct user regardless of how many times they are mentioned).
     */
    val mentionedUserIds: List<String>
        get() = _mentionTokens.map { it.userId }.distinct()

    /**
     * Detects whether the cursor is positioned after an active "@" trigger.
     *
     * An "@" trigger is active when:
     * - The character at or before the cursor is "@" or follows "@" with no spaces
     * - The "@" is either at the start of the text or preceded by a space
     *
     * Requirement 14.1: Detect "@" character in input field within a group conversation.
     *
     * @param text The current input text
     * @param cursorPosition The current cursor position
     * @return MentionQueryResult indicating whether a mention query is active
     */
    fun detectMentionQuery(text: String, cursorPosition: Int): MentionQueryResult {
        if (text.isEmpty() || cursorPosition <= 0) {
            return MentionQueryResult(isActive = false)
        }

        // Check if cursor is inside an existing mention token
        for (token in _mentionTokens) {
            if (cursorPosition > token.startIndex && cursorPosition <= token.endIndex) {
                return MentionQueryResult(isActive = false)
            }
        }

        // Search backwards from cursor to find "@" trigger
        val textBeforeCursor = text.substring(0, cursorPosition)
        val atIndex = textBeforeCursor.lastIndexOf('@')

        if (atIndex < 0) {
            return MentionQueryResult(isActive = false)
        }

        // "@" must be at start of text or preceded by a space/newline
        if (atIndex > 0 && !textBeforeCursor[atIndex - 1].isWhitespace()) {
            return MentionQueryResult(isActive = false)
        }

        // Check that the text between "@" and cursor has no spaces (it's a continuous query)
        val queryText = textBeforeCursor.substring(atIndex + 1)
        if (queryText.contains(' ') || queryText.contains('\n')) {
            return MentionQueryResult(isActive = false)
        }

        // Check that this "@" is not inside an existing mention token
        for (token in _mentionTokens) {
            if (atIndex >= token.startIndex && atIndex < token.endIndex) {
                return MentionQueryResult(isActive = false)
            }
        }

        return MentionQueryResult(
            isActive = true,
            query = queryText,
            triggerStartIndex = atIndex
        )
    }

    /**
     * Filters group members by prefix match on display name (case-insensitive).
     *
     * Requirement 14.1: Filter by case-insensitive prefix match on characters after "@".
     * Requirement 14.5: Group > 10 members → show max 5 suggestions sorted by displayName ascending.
     * Requirement 14.6: "@" with no additional chars → show first 5 members (excluding current user).
     *
     * @param members All group members
     * @param query The text after "@" to filter by
     * @param currentUserId The current user's ID (excluded from suggestions)
     * @param totalGroupSize Total number of members in the group
     * @return Filtered and sorted list of suggestions
     */
    fun getSuggestions(
        members: List<MentionMember>,
        query: String,
        currentUserId: String,
        totalGroupSize: Int
    ): List<MentionMember> {
        val filtered = members
            .filter { it.userId != currentUserId }
            .filter { member ->
                if (query.isEmpty()) true
                else member.displayName.startsWith(query, ignoreCase = true)
            }
            .sortedBy { it.displayName.lowercase() }

        // Requirement 14.5: Group > 10 members → max 5 suggestions
        // Requirement 14.6: "@" with no additional chars → first 5 members
        return if (totalGroupSize > 10 || query.isEmpty()) {
            filtered.take(5)
        } else {
            filtered
        }
    }

    /**
     * Inserts a mention token into the input text, replacing "@query" with the member's display name.
     *
     * Requirement 14.2: Replace "@" and typed filter characters with styled mention token,
     * place cursor immediately after the token.
     *
     * @param text Current input text
     * @param triggerStartIndex Index of the "@" character
     * @param cursorPosition Current cursor position (end of "@query")
     * @param member The selected member to mention
     * @return MentionInsertResult with updated text and cursor position
     */
    fun insertMention(
        text: String,
        triggerStartIndex: Int,
        cursorPosition: Int,
        member: MentionMember
    ): MentionInsertResult {
        // The mention token text is "@DisplayName" followed by a space
        val tokenText = "@${member.displayName} "
        val beforeTrigger = text.substring(0, triggerStartIndex)
        val afterCursor = text.substring(cursorPosition)
        val newText = beforeTrigger + tokenText + afterCursor

        val tokenEndIndex = triggerStartIndex + tokenText.length
        val newCursorPosition = tokenEndIndex

        val token = MentionToken(
            userId = member.userId,
            displayName = member.displayName,
            startIndex = triggerStartIndex,
            endIndex = tokenEndIndex - 1 // Exclude the trailing space from the token range
        )

        // Adjust existing tokens that come after the insertion point
        val lengthDifference = tokenText.length - (cursorPosition - triggerStartIndex)
        val adjustedTokens = _mentionTokens.map { existing ->
            if (existing.startIndex >= cursorPosition) {
                existing.copy(
                    startIndex = existing.startIndex + lengthDifference,
                    endIndex = existing.endIndex + lengthDifference
                )
            } else {
                existing
            }
        }
        _mentionTokens.clear()
        _mentionTokens.addAll(adjustedTokens)
        _mentionTokens.add(token)

        return MentionInsertResult(
            newText = newText,
            newCursorPosition = newCursorPosition,
            mentionToken = token
        )
    }

    /**
     * Handles text changes to detect if a mention token was partially deleted.
     * If any character within a token is deleted, the entire token is removed.
     *
     * Requirement 14.7: Delete within mention token → remove entire token and userId from list.
     *
     * @param oldText The previous input text
     * @param newText The new input text after the change
     * @param newCursorPosition The cursor position after the change
     * @return The corrected text with full tokens removed if partially deleted, or null if no correction needed
     */
    fun handleTextChange(oldText: String, newText: String, newCursorPosition: Int): String? {
        if (_mentionTokens.isEmpty()) return null
        if (newText.length >= oldText.length) {
            // Text was added, not deleted — just adjust token positions
            recalculateTokenPositions(newText)
            return null
        }

        // Text was deleted — check if any token was affected
        val tokensToRemove = mutableListOf<MentionToken>()
        for (token in _mentionTokens) {
            val tokenText = "@${token.displayName}"
            // Check if the token text is still intact at its position
            if (token.startIndex >= newText.length ||
                token.endIndex > newText.length ||
                !newText.substring(token.startIndex, minOf(token.endIndex, newText.length))
                    .startsWith(tokenText.take(minOf(tokenText.length, newText.length - token.startIndex)))
            ) {
                tokensToRemove.add(token)
            }
        }

        if (tokensToRemove.isEmpty()) return null

        // Remove affected tokens entirely from the text
        var resultText = newText
        // Sort tokens by start index descending to remove from end first (preserves indices)
        val sortedTokensToRemove = tokensToRemove.sortedByDescending { it.startIndex }
        for (token in sortedTokensToRemove) {
            val tokenText = "@${token.displayName}"
            // Find what remains of this token in the text
            val start = token.startIndex.coerceAtMost(resultText.length)
            // Find the end of whatever remains of this token
            val remainingEnd = findTokenRemainder(resultText, start, tokenText)
            if (remainingEnd > start) {
                resultText = resultText.removeRange(start, remainingEnd)
            }
            _mentionTokens.remove(token)
        }

        // Recalculate positions of remaining tokens
        recalculateTokenPositions(resultText)

        return resultText
    }

    /**
     * Finds the end index of whatever remains of a token at the given position.
     */
    private fun findTokenRemainder(text: String, start: Int, originalTokenText: String): Int {
        if (start >= text.length) return start
        // Look for the longest prefix of the original token that matches at this position
        var end = start
        val maxEnd = minOf(start + originalTokenText.length, text.length)
        while (end < maxEnd && end - start < originalTokenText.length &&
            text[end] == originalTokenText[end - start]
        ) {
            end++
        }
        // If nothing matches, include trailing space if it was part of the token
        if (end == start && start < text.length && text[start] == ' ') {
            end = start + 1
        }
        return if (end > start) end else start
    }

    /**
     * Recalculates token positions based on current text content.
     * Searches for "@DisplayName" patterns and updates indices.
     */
    private fun recalculateTokenPositions(text: String) {
        val updatedTokens = _mentionTokens.mapNotNull { token ->
            val tokenText = "@${token.displayName}"
            val index = text.indexOf(tokenText, token.startIndex.coerceAtMost(text.length - 1).coerceAtLeast(0))
            if (index >= 0) {
                token.copy(startIndex = index, endIndex = index + tokenText.length)
            } else {
                // Token no longer exists in text
                null
            }
        }
        _mentionTokens.clear()
        _mentionTokens.addAll(updatedTokens)
    }

    /**
     * Clears all mention tokens. Called when a message is sent or input is cleared.
     */
    fun clear() {
        _mentionTokens.clear()
    }

    /**
     * Returns the ranges of mention tokens in the given text for styled rendering.
     * Used both for input field styling and for rendering mentions in displayed messages.
     *
     * Requirement 14.4: Render mentions in primary color + bold in displayed messages.
     */
    fun getMentionRanges(text: String): List<IntRange> {
        return _mentionTokens.mapNotNull { token ->
            val tokenText = "@${token.displayName}"
            val index = text.indexOf(tokenText)
            if (index >= 0) {
                index until (index + tokenText.length)
            } else null
        }
    }

    companion object {
        /**
         * Finds mention patterns in a message text for display rendering.
         * This is a static utility for rendering mentions in received messages
         * where we don't have the MentionEngine state.
         *
         * Looks for "@DisplayName" patterns where the display name matches
         * one of the mentioned user IDs mapped to display names.
         *
         * Requirement 14.4: Render mentioned names in primary color and bold weight.
         *
         * @param text The message text
         * @param mentionedUserIds List of mentioned user IDs in the message
         * @param userDisplayNames Map of userId to displayName for lookup
         * @return List of ranges in the text that should be styled as mentions
         */
        fun findMentionRangesInMessage(
            text: String,
            mentionedUserIds: List<String>,
            userDisplayNames: Map<String, String>
        ): List<IntRange> {
            if (mentionedUserIds.isEmpty() || text.isEmpty()) return emptyList()

            val ranges = mutableListOf<IntRange>()
            for (userId in mentionedUserIds) {
                val displayName = userDisplayNames[userId] ?: continue
                val mentionText = "@$displayName"
                var searchFrom = 0
                while (true) {
                    val index = text.indexOf(mentionText, searchFrom)
                    if (index < 0) break
                    ranges.add(index until (index + mentionText.length))
                    searchFrom = index + mentionText.length
                }
            }
            return ranges.sortedBy { it.first }
        }
    }
}
