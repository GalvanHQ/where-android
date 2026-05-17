package com.ovi.where.presentation.model

/**
 * Represents a media thumbnail displayed in shared media sections
 * of conversation and group info screens.
 */
data class MediaThumbnail(
    val id: String,
    val thumbnailUrl: String,
    val type: MediaType
)
