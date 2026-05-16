package com.ovi.where.data.image

import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.source
import timber.log.Timber
import java.io.IOException

/**
 * Custom Coil [Fetcher] for Firebase Storage URLs.
 *
 * Handles:
 * - Appending the Firebase download token to the URL
 * - Token refresh on 403 responses with a single retry
 * - Permanent failure if retry also fails with 403 or token refresh fails
 *
 * Validates: Requirement 18.5
 */
class FirebaseStorageFetcher(
    private val url: String,
    private val options: Options,
    private val httpClient: OkHttpClient,
    private val firebaseAuth: FirebaseAuth
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        // First attempt with current token
        val token = getFirebaseToken()
        val authenticatedUrl = appendToken(url, token)

        val response = executeRequest(authenticatedUrl)

        if (response.code == 403 && token != null) {
            // Token might be expired — refresh and retry exactly once
            response.close()
            Timber.d("FirebaseStorageFetcher: 403 received, attempting token refresh and retry")

            val refreshedToken = refreshFirebaseToken()
            if (refreshedToken == null) {
                Timber.w("FirebaseStorageFetcher: Token refresh failed, treating as permanent failure")
                throw FirebaseStorageTokenException("Token refresh failed for URL: $url")
            }

            val retryUrl = appendToken(url, refreshedToken)
            val retryResponse = executeRequest(retryUrl)

            if (retryResponse.code == 403) {
                retryResponse.close()
                Timber.w("FirebaseStorageFetcher: Retry also returned 403, permanent failure")
                throw FirebaseStorageTokenException("Permanent 403 after token refresh for URL: $url")
            }

            if (!retryResponse.isSuccessful) {
                retryResponse.close()
                throw IOException("HTTP ${retryResponse.code} on retry for URL: $url")
            }

            val retryBody = retryResponse.body
                ?: throw IOException("Empty response body on retry for URL: $url")

            return SourceResult(
                source = ImageSource(
                    source = retryBody.source(),
                    context = options.context
                ),
                mimeType = retryBody.contentType()?.toString(),
                dataSource = DataSource.NETWORK
            )
        }

        if (!response.isSuccessful) {
            response.close()
            throw IOException("HTTP ${response.code} for URL: $url")
        }

        val body = response.body
            ?: throw IOException("Empty response body for URL: $url")

        return SourceResult(
            source = ImageSource(
                source = body.source(),
                context = options.context
            ),
            mimeType = body.contentType()?.toString(),
            dataSource = DataSource.NETWORK
        )
    }

    private fun executeRequest(url: String): okhttp3.Response {
        val request = Request.Builder()
            .url(url)
            .build()
        return httpClient.newCall(request).execute()
    }

    private suspend fun getFirebaseToken(): String? {
        return try {
            firebaseAuth.currentUser?.getIdToken(false)?.await()?.token
        } catch (e: Exception) {
            Timber.w(e, "FirebaseStorageFetcher: Failed to get current token")
            null
        }
    }

    private suspend fun refreshFirebaseToken(): String? {
        return try {
            firebaseAuth.currentUser?.getIdToken(true)?.await()?.token
        } catch (e: Exception) {
            Timber.w(e, "FirebaseStorageFetcher: Failed to refresh token")
            null
        }
    }

    /**
     * Appends the Firebase auth token to the URL as a query parameter.
     * If the URL already has query parameters, appends with &; otherwise with ?.
     */
    private fun appendToken(url: String, token: String?): String {
        if (token == null) return url
        val separator = if (url.contains("?")) "&" else "?"
        return "${url}${separator}token=$token"
    }

    /**
     * Factory that creates [FirebaseStorageFetcher] instances for Firebase Storage URLs.
     *
     * Recognizes URLs containing "firebasestorage.googleapis.com" or
     * "storage.googleapis.com" as Firebase Storage URLs.
     */
    class Factory(
        private val httpClient: OkHttpClient,
        private val firebaseAuth: FirebaseAuth
    ) : Fetcher.Factory<String> {

        override fun create(data: String, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (!isFirebaseStorageUrl(data)) return null
            return FirebaseStorageFetcher(data, options, httpClient, firebaseAuth)
        }

        private fun isFirebaseStorageUrl(url: String): Boolean {
            return url.contains("firebasestorage.googleapis.com") ||
                url.contains("storage.googleapis.com")
        }
    }
}

/**
 * Exception indicating a permanent Firebase Storage token failure.
 * When this is thrown, the image should show an error placeholder
 * and not auto-retry.
 */
class FirebaseStorageTokenException(message: String) : IOException(message)
