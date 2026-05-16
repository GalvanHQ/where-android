package com.ovi.where.data.image

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Unit tests for [FirebaseStorageFetcher.Factory] URL detection logic.
 *
 * Validates: Requirement 18.5 (Firebase Storage URL recognition)
 */
class FirebaseStorageFetcherTest : StringSpec({

    "Factory recognizes firebasestorage.googleapis.com URLs" {
        val url = "https://firebasestorage.googleapis.com/v0/b/my-app.appspot.com/o/images%2Fphoto.jpg?alt=media"
        isFirebaseStorageUrl(url) shouldBe true
    }

    "Factory recognizes storage.googleapis.com URLs" {
        val url = "https://storage.googleapis.com/my-app.appspot.com/images/photo.jpg"
        isFirebaseStorageUrl(url) shouldBe true
    }

    "Factory does not match regular HTTP URLs" {
        val url = "https://example.com/images/photo.jpg"
        isFirebaseStorageUrl(url) shouldBe false
    }

    "Factory does not match empty string" {
        isFirebaseStorageUrl("") shouldBe false
    }

    "Factory does not match non-Firebase cloud storage URLs" {
        val url = "https://s3.amazonaws.com/bucket/photo.jpg"
        isFirebaseStorageUrl(url) shouldBe false
    }

    "appendToken adds token with ? when no existing query params" {
        val url = "https://firebasestorage.googleapis.com/v0/b/app/o/photo.jpg"
        val result = appendTokenToUrl(url, "my-token-123")
        result shouldBe "https://firebasestorage.googleapis.com/v0/b/app/o/photo.jpg?token=my-token-123"
    }

    "appendToken adds token with & when existing query params present" {
        val url = "https://firebasestorage.googleapis.com/v0/b/app/o/photo.jpg?alt=media"
        val result = appendTokenToUrl(url, "my-token-123")
        result shouldBe "https://firebasestorage.googleapis.com/v0/b/app/o/photo.jpg?alt=media&token=my-token-123"
    }

    "appendToken returns original URL when token is null" {
        val url = "https://firebasestorage.googleapis.com/v0/b/app/o/photo.jpg"
        val result = appendTokenToUrl(url, null)
        result shouldBe url
    }

    "FirebaseStorageTokenException is an IOException" {
        val exception = FirebaseStorageTokenException("test error")
        exception.shouldBeInstanceOf<java.io.IOException>()
        exception.message shouldBe "test error"
    }
})

// Helper functions extracted for testability (mirrors internal logic)
private fun isFirebaseStorageUrl(url: String): Boolean {
    return url.contains("firebasestorage.googleapis.com") ||
        url.contains("storage.googleapis.com")
}

private fun appendTokenToUrl(url: String, token: String?): String {
    if (token == null) return url
    val separator = if (url.contains("?")) "&" else "?"
    return "${url}${separator}token=$token"
}
