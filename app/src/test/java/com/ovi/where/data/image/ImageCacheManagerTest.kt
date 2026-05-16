package com.ovi.where.data.image

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Unit tests for [ImageCacheManager] configuration and [constrainToMaxSize] utility.
 *
 * Validates: Requirements 18.1, 18.3, 18.4
 */
class ImageCacheManagerTest : StringSpec({

    "memory cache percent should be 25%" {
        ImageCacheManager.MEMORY_CACHE_PERCENT shouldBe 0.25
    }

    "disk cache max size should be 250MB" {
        ImageCacheManager.DISK_CACHE_MAX_SIZE_BYTES shouldBe (250L * 1024 * 1024)
    }

    "disk cache directory should be image_cache" {
        ImageCacheManager.DISK_CACHE_DIRECTORY shouldBe "image_cache"
    }

    "crossfade duration should be 200ms" {
        ImageCacheManager.CROSSFADE_DURATION_MS shouldBe 200
    }

    "max thumbnail size should be 512px" {
        ImageCacheManager.MAX_THUMBNAIL_SIZE_PX shouldBe 512
    }

    // constrainToMaxSize tests

    "constrainToMaxSize returns original dimensions when both within limit" {
        val (w, h) = constrainToMaxSize(400, 300)
        w shouldBe 400
        h shouldBe 300
    }

    "constrainToMaxSize returns original dimensions when exactly at limit" {
        val (w, h) = constrainToMaxSize(512, 256)
        w shouldBe 512
        h shouldBe 256
    }

    "constrainToMaxSize scales down when width exceeds limit" {
        val (w, h) = constrainToMaxSize(1024, 512)
        w shouldBe 512
        h shouldBe 256
    }

    "constrainToMaxSize scales down when height exceeds limit" {
        val (w, h) = constrainToMaxSize(256, 1024)
        w shouldBe 128
        h shouldBe 512
    }

    "constrainToMaxSize scales down when both exceed limit" {
        val (w, h) = constrainToMaxSize(2048, 1024)
        w shouldBe 512
        h shouldBe 256
    }

    "constrainToMaxSize handles square images exceeding limit" {
        val (w, h) = constrainToMaxSize(1024, 1024)
        w shouldBe 512
        h shouldBe 512
    }

    "constrainToMaxSize handles zero width" {
        val (w, h) = constrainToMaxSize(0, 300)
        w shouldBe 512
        h shouldBe 512
    }

    "constrainToMaxSize handles zero height" {
        val (w, h) = constrainToMaxSize(300, 0)
        w shouldBe 512
        h shouldBe 512
    }

    "constrainToMaxSize handles negative dimensions" {
        val (w, h) = constrainToMaxSize(-100, -200)
        w shouldBe 512
        h shouldBe 512
    }

    "constrainToMaxSize maintains aspect ratio for landscape" {
        val (w, h) = constrainToMaxSize(2000, 1000)
        // Scale factor: 512/2000 = 0.256
        // Width: 2000 * 0.256 = 512
        // Height: 1000 * 0.256 = 256
        w shouldBe 512
        h shouldBe 256
    }

    "constrainToMaxSize maintains aspect ratio for portrait" {
        val (w, h) = constrainToMaxSize(600, 1200)
        // Scale factor: 512/1200 = 0.4267
        // Width: 600 * 0.4267 = 256
        // Height: 1200 * 0.4267 = 512
        w shouldBe 256
        h shouldBe 512
    }

    "constrainToMaxSize ensures minimum 1px dimensions" {
        // Very extreme aspect ratio
        val (w, h) = constrainToMaxSize(1, 10000)
        // Scale factor: 512/10000 = 0.0512
        // Width: 1 * 0.0512 = 0 -> coerced to 1
        // Height: 10000 * 0.0512 = 512
        w shouldBe 1
        h shouldBe 512
    }
})
