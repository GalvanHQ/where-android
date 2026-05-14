package com.ovi.where.presentation.common.search

/**
 * Presentation model for a suggested user in the search bar suggestions row.
 * Represents a user the current user recently interacted with (messaged or viewed profile).
 */
data class SuggestionUiModel(
    val userId: String,
    val displayName: String,
    val photoUrl: String?,
    val isOnline: Boolean
)
