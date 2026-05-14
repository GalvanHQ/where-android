package com.ovi.where.domain.usecase

import com.ovi.where.domain.model.Conversation
import javax.inject.Inject

/**
 * Filters a list of conversations by a search query.
 *
 * Returns conversations where the query is a case-insensitive substring
 * of the conversation title (name) or last message text.
 * Empty or whitespace-only queries return an empty list.
 */
class SearchChatsUseCase @Inject constructor() {

    operator fun invoke(query: String, conversations: List<Conversation>): List<Conversation> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val lowerQuery = trimmed.lowercase()
        return conversations.filter { conversation ->
            conversation.name.lowercase().contains(lowerQuery) ||
                conversation.lastMessageText.lowercase().contains(lowerQuery)
        }
    }
}
