package com.ovi.where.presentation.common.search

/**
 * Shared UI state for the messenger-style search bar used on People and Chats screens.
 * Holds all data needed to render the search bar, recent searches, suggestions, and results.
 */
data class SearchUiState(
    val query: String = "",
    val isFocused: Boolean = false,
    val isLoading: Boolean = false,
    val recentSearches: List<String> = emptyList(),
    val suggestions: List<SuggestionUiModel> = emptyList(),
    val searchResults: List<Any> = emptyList(),
    val showEmptyState: Boolean = false
)
