package com.ovi.where

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Simple singleton that carries a pending deep-link route between
 * [MainActivity.onNewIntent] (called while the app is already running)
 * and [AppNavGraph] (which observes it via Compose state).
 *
 * The route is consumed once — after [AppNavGraph] reads and navigates to it
 * the value is reset to null.
 *
 * ## Supported "where://" URI patterns
 * - `where://chat/{id}`           → Chat screen
 * - `where://user_profile/{id}`   → UserProfile screen
 * - `where://group_details/{id}`  → GroupDetails screen
 * - `where://group_map/{id}`      → GroupMap screen
 * - `where://friend_requests`     → FriendRequests screen
 *
 * Unrecognized URIs are silently discarded by [AppNavGraph.navigateToDeepLink].
 */
object DeepLinkManager {
    var pending: String? by mutableStateOf(null)

    /**
     * Parses a "where://" URI into a plain route string.
     *
     * Examples:
     * - `where://chat/abc123`        → `"chat/abc123"`
     * - `where://friend_requests`    → `"friend_requests"`
     * - `where://unknown/path`       → `"unknown/path"` (handled gracefully downstream)
     * - `https://example.com`        → `null` (wrong scheme)
     * - `null`                       → `null`
     *
     * @return The extracted route string, or null if the URI is null or not a "where://" URI.
     */
    fun parseWhereUri(uri: Uri?): String? {
        if (uri == null) return null
        if (uri.scheme != "where") return null
        val host = uri.host ?: return null
        val path = uri.path?.removePrefix("/") ?: ""
        return if (path.isBlank()) host else "$host/$path"
    }
}
