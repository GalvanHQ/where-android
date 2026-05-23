package com.ovi.where.domain.usecase.chat

import com.ovi.where.core.common.Resource
import com.ovi.where.data.local.dao.LinkPreviewCacheDao
import com.ovi.where.data.local.entity.LinkPreviewCacheEntity
import com.ovi.where.data.remote.chat.ChatApiClient
import com.ovi.where.data.remote.chat.LinkPreviewDto
import com.ovi.where.domain.model.LinkPreview
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

/**
 * Fetches Open Graph metadata for a URL to generate a link preview.
 *
 * Behavior:
 * - Detects the first URL matching `https?://[^\s]+` in the message text
 * - Checks the local Room cache first (LinkPreviewCacheEntity)
 * - If not cached, fetches from the server-side link preview API with a 5-second timeout
 * - On success: caches the result and returns the LinkPreview
 * - On timeout/error: returns Resource.Error (caller sends message without preview)
 * - If no title in metadata: uses domain as title, omits description
 *
 * Requirements: 12.1, 12.2, 12.3, 12.5, 12.6
 */
class FetchLinkPreviewUseCase @Inject constructor(
    private val linkPreviewCacheDao: LinkPreviewCacheDao
) {
    companion object {
        /** Timeout for the link preview API call. Requirement 12.3 */
        private const val FETCH_TIMEOUT_MS = 5_000L

        /** Cache validity duration: 24 hours */
        private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L
    }

    /**
     * Extracts the first external URL (http/https or bare domain) from the
     * given text. Requirement 12.5: only preview the first URL if multiple
     * are present.
     *
     * Detection is delegated to [com.ovi.where.core.links.LinkParser] so
     * the link-preview pipeline, the chat bubble's tappable text, and the
     * notification inbox all agree on what counts as a URL.
     *
     * @return The first external URL found, or null if no URL is present.
     */
    fun extractFirstUrl(text: String): String? =
        com.ovi.where.core.links.LinkParser.firstExternalUrl(text)

    /**
     * Fetches link preview metadata for the given URL.
     *
     * @param url The URL to fetch Open Graph metadata for.
     * @return Resource.Success with LinkPreview on success, Resource.Error on timeout/failure.
     */
    suspend operator fun invoke(url: String): Resource<LinkPreview> {
        return try {
            // Check cache first
            val cached = linkPreviewCacheDao.getByUrl(url)
            if (cached != null && !isCacheExpired(cached.fetchedAt)) {
                return Resource.Success(cached.toDomain())
            }

            // Fetch from server-side API with 5-second timeout (Requirement 12.3)
            val dto = withTimeout(FETCH_TIMEOUT_MS) {
                ChatApiClient.apiService.fetchLinkPreview(url)
            }

            val domain = extractDomain(url)

            // Requirement 12.6: If no title, use domain as title and omit description
            val effectiveTitle = dto.title?.takeIf { it.isNotBlank() } ?: domain
            val effectiveDescription = if (dto.title.isNullOrBlank()) null else dto.description

            val linkPreview = LinkPreview(
                url = url,
                title = effectiveTitle,
                description = effectiveDescription,
                imageUrl = dto.imageUrl,
                domain = domain
            )

            // Cache the result
            linkPreviewCacheDao.insert(
                LinkPreviewCacheEntity(
                    url = url,
                    title = effectiveTitle,
                    description = effectiveDescription,
                    imageUrl = dto.imageUrl,
                    domain = domain,
                    fetchedAt = System.currentTimeMillis()
                )
            )

            Resource.Success(linkPreview)
        } catch (e: Exception) {
            // Requirement 12.3: On timeout or error, return error
            Resource.Error("Failed to fetch link preview: ${e.message}")
        }
    }

    private fun isCacheExpired(fetchedAt: Long): Boolean {
        return System.currentTimeMillis() - fetchedAt > CACHE_TTL_MS
    }

    private fun extractDomain(url: String): String {
        return try {
            val withoutProtocol = url.removePrefix("https://").removePrefix("http://")
            withoutProtocol.substringBefore("/").substringBefore("?").substringBefore("#")
        } catch (_: Exception) {
            url
        }
    }

    private fun LinkPreviewCacheEntity.toDomain(): LinkPreview {
        return LinkPreview(
            url = url,
            title = title ?: domain,
            description = if (title.isNullOrBlank()) null else description,
            imageUrl = imageUrl,
            domain = domain
        )
    }
}
