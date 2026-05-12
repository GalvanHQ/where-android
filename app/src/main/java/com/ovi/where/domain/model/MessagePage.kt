package com.ovi.where.domain.model

data class MessagePage(
    val messages: List<Message>,
    val nextCursor: String?,
    val hasMore: Boolean
)
