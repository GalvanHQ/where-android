package com.ovi.where.core.notification

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.ovi.where.presentation.permission.PermissionManagerState
import com.ovi.where.presentation.permission.rememberPermissionManager
import androidx.core.content.edit

/**
 * One-shot prompt for the POST_NOTIFICATIONS runtime permission on
 * Android 13+.
 *
 * UX:
 *  • On Android < 13 this is a no-op (permission is granted at install).
 *  • On Android 13+ we route through the app's [rememberPermissionManager]
 *    so the user sees the standard rationale dialog *first* explaining
 *    why we need notifications, and only then the system prompt. This
 *    matches the rest of the app's permission UX (location, etc.) and
 *    handles permanent denial via the Settings deep-link dialog for free.
 *  • We ask exactly once per install — after the user answers we record
 *    the prompt happened in SharedPreferences so subsequent app starts
 *    don't re-ask. Re-enabling later goes through the in-app
 *    NotificationPreferencesScreen "Open settings" hand-off.
 *
 * The effect attaches to the post-auth nav graph (MapTab composable) so
 * it only runs after the user has signed in — asking earlier would feel
 * pushy on the login/onboarding screens.
 */
@Composable
fun NotificationPermissionEffect() {
    val context = LocalContext.current
    val permissionManager = rememberPermissionManager()

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
        if (hasNotificationPermission(context)) return@LaunchedEffect

        val prefs = context.notificationPromptPrefs()
        if (prefs.getBoolean(KEY_ASKED, false)) return@LaunchedEffect
        prefs.edit { putBoolean(KEY_ASKED, true) }

        permissionManager.requestNotificationPermission(
            onGranted = { /* great — channels gate the rest */ },
            onDenied = { /* user can re-enable from settings later */ }
        )
    }
}

private fun hasNotificationPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
}

private fun Context.notificationPromptPrefs(): SharedPreferences =
    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

private const val PREFS_NAME = "notification_permission_prefs"
private const val KEY_ASKED = "post_notifications_asked"

// `PermissionManagerState` import retained to keep the public surface
// stable in case future callers want to build on this effect with a custom
// onDenied callback.
@Suppress("unused")
private val permissionManagerStateMarker: Class<PermissionManagerState> = PermissionManagerState::class.java
