package com.ovi.where.startup

import android.content.Context
import androidx.startup.Initializer
import coil.Coil
import coil.ImageLoader
import com.ovi.where.data.image.ImageCacheManager

/**
 * App Startup [Initializer] for Coil image loading.
 *
 * Sets up Coil with a lazy [ImageLoader] factory that delegates to [ImageCacheManager].
 * The actual ImageLoader is NOT created during app startup — it is lazily initialized
 * on first image load request, keeping the startup path fast (Req 20.1, 20.4).
 *
 * The [ImageCacheManager] is obtained via Hilt's EntryPoint mechanism since App Startup
 * initializers run before Hilt injection is available in Activities/Fragments.
 *
 * Configuration (applied lazily via ImageCacheManager):
 * - Memory cache: 25% of available heap
 * - Disk cache: 250MB in app cache directory
 * - Crossfade: 200ms from disk/network
 * - Firebase Storage fetcher with token refresh on 403
 *
 * Has no dependencies — Coil initialization is independent of Firebase and Timber.
 */
class CoilInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        // Set a lazy ImageLoader factory so Coil doesn't eagerly create the loader.
        // The ImageCacheManager's imageLoader is itself lazy, so the full Coil
        // configuration (memory cache, disk cache, Firebase fetcher) is deferred
        // until the first image request.
        Coil.setImageLoader {
            val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
                context.applicationContext,
                ImageCacheManagerEntryPoint::class.java
            )
            entryPoint.imageCacheManager().imageLoader
        }
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        // Coil has no dependencies on other initializers
        return emptyList()
    }
}

/**
 * Hilt EntryPoint to access [ImageCacheManager] from the App Startup initializer.
 */
@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface ImageCacheManagerEntryPoint {
    fun imageCacheManager(): ImageCacheManager
}
