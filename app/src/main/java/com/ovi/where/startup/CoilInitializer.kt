package com.ovi.where.startup

import android.content.Context
import androidx.startup.Initializer
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache

/**
 * App Startup [Initializer] for Coil image loading.
 *
 * Configures Coil with:
 * - Memory cache: 25% of available heap
 * - Disk cache: 50MB
 *
 * Has no dependencies — Coil initialization is independent of Firebase and Timber.
 */
class CoilInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        val imageLoader = ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024) // 50MB
                    .build()
            }
            .respectCacheHeaders(true)
            .build()

        Coil.setImageLoader(imageLoader)
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        // Coil has no dependencies on other initializers
        return emptyList()
    }
}
