package com.ovi.where.data.image

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages image loading configuration using Coil.
 *
 * Configuration:
 * - Memory cache: 25% of available heap
 * - Disk cache: 250MB in app cache directory
 * - Priority: memory → disk → network
 * - Crossfade: 200ms from disk/network; instant from memory (Coil default behavior)
 * - Thumbnails: downscale to layout dimensions, max 512px longest edge
 * - Firebase Storage fetcher: appends download token, handles 403 with token refresh + single retry
 * - On all-layer failure: error placeholder, no auto-retry until scroll out/in or pull-to-refresh
 *
 * This component is lazily initialized — the [imageLoader] is not created until first access.
 *
 * Validates: Requirements 18.1, 18.2, 18.3, 18.4, 18.5, 18.6
 */
@Singleton
class ImageCacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firebaseAuth: FirebaseAuth
) {
    /**
     * The configured [ImageLoader] instance.
     *
     * Lazily initialized to avoid impacting app startup time (Req 20.1, 20.4).
     * Uses memory → disk → network priority (Req 18.2).
     * Crossfade 200ms for disk/network loads; memory cache hits display instantly (Req 18.3).
     */
    val imageLoader: ImageLoader by lazy {
        Timber.d("ImageCacheManager: Initializing ImageLoader")
        ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(MEMORY_CACHE_PERCENT)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve(DISK_CACHE_DIRECTORY))
                    .maxSizeBytes(DISK_CACHE_MAX_SIZE_BYTES)
                    .build()
            }
            // Crossfade 200ms for disk/network loads.
            // Coil automatically skips crossfade for memory cache hits.
            .crossfade(CROSSFADE_DURATION_MS)
            // Cache policies: memory → disk → network priority
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            // Respect cache headers from server
            .respectCacheHeaders(true)
            // Custom fetcher for Firebase Storage URLs
            .components {
                add(FirebaseStorageFetcher.Factory(createHttpClient(), firebaseAuth))
            }
            .build()
    }

    private fun createHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    companion object {
        /** Memory cache: 25% of available heap (Req 18.1) */
        const val MEMORY_CACHE_PERCENT = 0.25

        /** Disk cache: 250MB (Req 18.1) */
        const val DISK_CACHE_MAX_SIZE_BYTES = 250L * 1024 * 1024

        /** Disk cache directory name within app cache dir */
        const val DISK_CACHE_DIRECTORY = "image_cache"

        /** Crossfade duration for disk/network loads (Req 18.3) */
        const val CROSSFADE_DURATION_MS = 200

        /** Maximum thumbnail size on longest edge (Req 18.4) */
        const val MAX_THUMBNAIL_SIZE_PX = 512
    }
}
