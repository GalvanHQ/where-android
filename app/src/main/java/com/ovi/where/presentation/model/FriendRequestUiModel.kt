package com.ovi.where.presentation.model

import com.ovi.where.domain.model.RequestEntry

/**
 * Presentation model for a received friend request shown in [FriendRequestsScreen].
 *
 * Built directly from a [RequestEntry] (single-doc inbox aggregation) — display
 * fields are already denormalized so no extra user fetch is needed per row.
 *
 * The `pairId` is exposed as a stable list key; the rendered identity stays
 * [requesterId] which the accept/decline use cases consume.
 */
data class FriendRequestUiModel(
    val pairId: String,
    val requesterId: String,
    val displayName: String,
    val username: String,
    val photoUrl: String?,
    val sentAt: Long,
    val avatarInitial: String
)

fun RequestEntry.toUiModel(): FriendRequestUiModel = FriendRequestUiModel(
    pairId        = pairId,
    requesterId   = uid,
    displayName   = displayName.ifEmpty { "Unknown" },
    username      = username,
    photoUrl      = photoUrl,
    sentAt        = sentAt,
    avatarInitial = displayName.take(1).uppercase().ifEmpty { "?" }
)
