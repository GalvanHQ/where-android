package com.ovi.where.domain.usecase

import com.ovi.where.domain.model.FriendEntry
import javax.inject.Inject

/**
 * Filters a list of friends by a search query.
 *
 * Returns friends whose [FriendEntry.displayName] or [FriendEntry.username]
 * contains the query as a case-insensitive substring. Empty or whitespace-only
 * queries return an empty list.
 */
class SearchPeopleUseCase @Inject constructor() {

    operator fun invoke(query: String, friends: List<FriendEntry>): List<FriendEntry> {
        if (query.isBlank()) return emptyList()

        val normalizedQuery = query.trim().lowercase()
        return friends.filter { friend ->
            friend.displayName.lowercase().contains(normalizedQuery) ||
                friend.username.lowercase().contains(normalizedQuery)
        }
    }
}
