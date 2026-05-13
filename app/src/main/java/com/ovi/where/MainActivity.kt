package com.ovi.where

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.navigation.compose.rememberNavController
import com.ovi.where.core.theme.WhereTheme
import com.ovi.where.presentation.navigation.AppNavGraph
import com.ovi.where.presentation.settings.AppearanceViewModel
import com.ovi.where.presentation.settings.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var dataStore: DataStore<Preferences>

    // Stores the deep-link route that should be opened after authentication.
    // Set when the Activity is launched/resumed via a notification tap.
    private var pendingDeepLinkRoute: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install the splash screen before calling super.onCreate().
        // The keep-on-screen condition ensures the splash is shown for at most 1000ms.
        val splashStartTime = SystemClock.elapsedRealtime()
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition {
            val elapsed = SystemClock.elapsedRealtime() - splashStartTime
            elapsed < MAX_SPLASH_DURATION_MS
        }

        super.onCreate(savedInstanceState)
        // Extract deep link from the launch intent (notification tap while app was closed)
        pendingDeepLinkRoute = extractDeepLinkRoute(intent)
        enableEdgeToEdge()
        setContent {
            // Observe theme preference from DataStore to apply immediately without restart
            val themeMode by dataStore.data
                .map { preferences ->
                    ThemeMode.fromKey(preferences[AppearanceViewModel.THEME_MODE_KEY])
                }
                .collectAsState(initial = ThemeMode.SYSTEM)

            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            WhereTheme(darkTheme = darkTheme) {
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
     *  2. `where://` URI scheme carried in `Intent.data` — parsed via [DeepLinkManager.parseWhereUri].
     */
    private fun extractDeepLinkRoute(intent: Intent?): String? {
        if (intent == null) return null

        // 1. String extra from FCM notification PendingIntent
        val extraRoute = intent.getStringExtra(EXTRA_DEEP_LINK_ROUTE)
        if (!extraRoute.isNullOrBlank()) return extraRoute

        // 2. URI scheme  e.g. where://chat/CONV_ID
        return DeepLinkManager.parseWhereUri(intent.data)
    }

    companion object {
        /** Intent extra key used by FcmMessagingService to carry the target nav route. */
        const val EXTRA_DEEP_LINK_ROUTE = "extra_deep_link_route"

        /** Maximum duration (ms) the splash screen stays visible before transitioning. */
        private const val MAX_SPLASH_DURATION_MS = 1000L
    }
}
