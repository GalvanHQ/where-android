package com.ovi.where

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.rememberNavController
import com.ovi.where.core.theme.WhereTheme
import com.ovi.where.presentation.navigation.AppNavGraph
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Stores the deep-link route that should be opened after authentication.
    // Set when the Activity is launched/resumed via a notification tap.
    private var pendingDeepLinkRoute: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Extract deep link from the launch intent (notification tap while app was closed)
        pendingDeepLinkRoute = extractDeepLinkRoute(intent)
        enableEdgeToEdge()
        setContent {
            WhereTheme {
                val navController = rememberNavController()
                // Pass the pending deep-link route to the nav graph.
                // AppNavGraph resolves it after the auth/splash check.
                AppNavGraph(
                    navController    = navController,
                    deepLinkRoute    = pendingDeepLinkRoute
                )
            }
        }
    }

    /**
     * Called when the app is already running (singleTop) and a notification is tapped.
     * We re-deliver the deep link so the nav graph can react.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val route = extractDeepLinkRoute(intent) ?: return
        // Post to the current nav controller via the shared mutable state —
        // AppNavGraph observes DeepLinkManager.
        DeepLinkManager.pending = route
    }

    /**
     * Extracts a navigation route string from the intent.
     *
     * Supports two delivery mechanisms:
     *  1. [EXTRA_DEEP_LINK_ROUTE] string extra set by [FcmMessagingService] — highest priority.
     *  2. `where://` URI scheme carried in `Intent.data`.
     */
    private fun extractDeepLinkRoute(intent: Intent?): String? {
        if (intent == null) return null

        // 1. String extra from FCM notification PendingIntent
        val extraRoute = intent.getStringExtra(EXTRA_DEEP_LINK_ROUTE)
        if (!extraRoute.isNullOrBlank()) return extraRoute

        // 2. URI scheme  e.g. where://chat/CONV_ID
        val uri = intent.data
        if (uri != null && uri.scheme == "where") {
            // Strip the leading "//" host + path → "chat/CONV_ID"
            val host = uri.host ?: return null
            val path = uri.path?.removePrefix("/") ?: ""
            return if (path.isBlank()) host else "$host/$path"
        }
        return null
    }

    companion object {
        /** Intent extra key used by FcmMessagingService to carry the target nav route. */
        const val EXTRA_DEEP_LINK_ROUTE = "extra_deep_link_route"
    }
}
