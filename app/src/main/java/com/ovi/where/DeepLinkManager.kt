package com.ovi.where

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
 */
object DeepLinkManager {
    var pending: String? by mutableStateOf(null)
}
