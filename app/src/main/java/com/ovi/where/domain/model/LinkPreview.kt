package com.ovi.where.domain.model

/**
 * Domain model representing Open Graph metadata for a URL link preview.
 *
 * Requirements: 12.1, 12.2, 12.6
 */
data class LinkPreview(
    val url: String,
    val title: String?,
    val description: String?,
    val imageUrl: String?,
    val domain: String
)
