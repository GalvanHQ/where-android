package com.ovi.where.data.local

import com.ovi.where.data.local.dao.CacheMetadataDao
import com.ovi.where.data.local.entity.CacheMetadataEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Determines whether cached data should be re-fetched based on a staleness threshold.
 * The threshold is 5 minutes (300,000 milliseconds) as per requirement 11.5.
 */
@Singleton
class CacheStalenessChecker @Inject constructor(
    private val cacheMetadataDao: CacheMetadataDao
) {

    companion object {
        const val STALENESS_THRESHOLD_MS = 300_000L
    }

    /**
     * Returns true if the cache entry for the given [key] is stale or does not exist.
     * A cache entry is considered stale if currentTime - lastFetchedAt > 300,000ms.
     *
     * @param key The cache key to check staleness for.
     * @param currentTimeMs The current time in milliseconds (defaults to System.currentTimeMillis()).
     */
    suspend fun shouldFetch(key: String, currentTimeMs: Long = System.currentTimeMillis()): Boolean {
        val metadata = cacheMetadataDao.getByKey(key) ?: return true
        return currentTimeMs - metadata.lastFetchedAt > STALENESS_THRESHOLD_MS
    }

    /**
     * Returns the stored ETag for the given [key], or null if no metadata exists.
     */
    suspend fun getETag(key: String): String? {
        return cacheMetadataDao.getByKey(key)?.eTag
    }

    /**
     * Updates the cache metadata for the given [key] with the current time and optional ETag.
     */
    suspend fun updateMetadata(key: String, eTag: String? = null, currentTimeMs: Long = System.currentTimeMillis()) {
        cacheMetadataDao.insert(
            CacheMetadataEntity(
                key = key,
                lastFetchedAt = currentTimeMs,
                eTag = eTag
            )
        )
    }
}
