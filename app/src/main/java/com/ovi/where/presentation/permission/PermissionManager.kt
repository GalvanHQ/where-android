package com.ovi.where.presentation.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

// ── Public API ────────────────────────────────────────────────────────────────

/**
 * Data class holding the permission name and a one-sentence explanation
 * of the app feature that requires it.
 */
data class PermissionRationale(
    val permissionName: String,
    val explanation: String
)

/**
 * State holder for the PermissionManager composable.
 * Provides a single entry point for requesting any runtime permission
 * used by the app (ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION,
 * ACCESS_BACKGROUND_LOCATION, POST_NOTIFICATIONS).
 */
@Stable
interface PermissionManagerState {
    /**
     * Request a single runtime permission with a rationale dialog shown first.
     *
     * @param permission The Android permission string (e.g., Manifest.permission.ACCESS_FINE_LOCATION)
     * @param rationale  The rationale to display before the system dialog
     * @param onGranted  Callback invoked when the permission is granted
     * @param onDenied   Callback invoked when the permission is denied or the user dismisses the rationale
     */
    fun requestPermission(
        permission: String,
        rationale: PermissionRationale,
        onGranted: () -> Unit,
        onDenied: () -> Unit
    )

    /**
     * Request ACCESS_BACKGROUND_LOCATION. This first verifies that foreground
     * location (ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION) is already granted.
     * If not, onDenied is called immediately.
     *
     * @param onGranted Callback invoked when background location is granted
     * @param onDenied  Callback invoked when denied or foreground location is missing
     */
    fun requestBackgroundLocation(
        onGranted: () -> Unit,
        onDenied: () -> Unit
    )

    /**
     * Request POST_NOTIFICATIONS permission on Android 13+.
     * On lower API levels, onGranted is called immediately (no permission needed).
     *
     * @param onGranted Callback invoked when notification permission is granted (or not needed)
     * @param onDenied  Callback invoked when the user denies notification permission
     */
    fun requestNotificationPermission(
        onGranted: () -> Unit,
        onDenied: () -> Unit
    )
}

// ── Composable entry point ────────────────────────────────────────────────────

/**
 * Remember and create a [PermissionManagerState] that handles:
 * - Rationale dialog display before system permission request
 * - Permanent denial detection with Settings deep-link dialog
 * - Background location ordering (foreground location must be granted first)
 * - POST_NOTIFICATIONS on Android 13+
 * - Graceful dismissal and denial handling (cancel operation)
 */
@Composable
fun rememberPermissionManager(): PermissionManagerState {
    val context = LocalContext.current

    // Mutable state for dialog management
    var showRationaleDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var currentRationale by remember { mutableStateOf<PermissionRationale?>(null) }
    var pendingPermission by remember { mutableStateOf<String?>(null) }
    var onGrantedCallback by remember { mutableStateOf<(() -> Unit)?>(null) }
    var onDeniedCallback by remember { mutableStateOf<(() -> Unit)?>(null) }
    // Track whether we've already asked for this permission (to detect permanent denial)
    var hasRequestedOnce by remember { mutableStateOf(false) }

    // Permission launcher for single permission requests
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onGrantedCallback?.invoke()
        } else {
            // Check if permanently denied
            val activity = context.findActivity()
            val permission = pendingPermission
            if (activity != null && permission != null) {
                val shouldShowRationale = activity.shouldShowRequestPermissionRationale(permission)
                if (!shouldShowRationale && hasRequestedOnce) {
                    // Permanently denied — show settings dialog
                    showSettingsDialog = true
                } else {
                    // Denied but not permanently — cancel operation
                    onDeniedCallback?.invoke()
                    resetState(
                        resetShowRationale = { showRationaleDialog = false },
                        resetShowSettings = { showSettingsDialog = false },
                        resetCurrentRationale = { currentRationale = null },
                        resetPendingPermission = { pendingPermission = null },
                        resetOnGranted = { onGrantedCallback = null },
                        resetOnDenied = { onDeniedCallback = null },
                        resetHasRequested = { hasRequestedOnce = false }
                    )
                }
            } else {
                onDeniedCallback?.invoke()
            }
        }
    }

    // Background location launcher (separate as required by Android)
    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onGrantedCallback?.invoke()
        } else {
            val activity = context.findActivity()
            val permission = Manifest.permission.ACCESS_BACKGROUND_LOCATION
            if (activity != null) {
                val shouldShowRationale = activity.shouldShowRequestPermissionRationale(permission)
                if (!shouldShowRationale && hasRequestedOnce) {
                    showSettingsDialog = true
                } else {
                    onDeniedCallback?.invoke()
                    resetState(
                        resetShowRationale = { showRationaleDialog = false },
                        resetShowSettings = { showSettingsDialog = false },
                        resetCurrentRationale = { currentRationale = null },
                        resetPendingPermission = { pendingPermission = null },
                        resetOnGranted = { onGrantedCallback = null },
                        resetOnDenied = { onDeniedCallback = null },
                        resetHasRequested = { hasRequestedOnce = false }
                    )
                }
            } else {
                onDeniedCallback?.invoke()
            }
        }
    }

    val state = remember {
        object : PermissionManagerState {
            override fun requestPermission(
                permission: String,
                rationale: PermissionRationale,
                onGranted: () -> Unit,
                onDenied: () -> Unit
            ) {
                // Already granted — invoke immediately
                if (isPermissionGranted(context, permission)) {
                    onGranted()
                    return
                }

                // Store callbacks and show rationale dialog
                pendingPermission = permission
                currentRationale = rationale
                onGrantedCallback = onGranted
                onDeniedCallback = onDenied
                hasRequestedOnce = true
                showRationaleDialog = true
            }

            override fun requestBackgroundLocation(
                onGranted: () -> Unit,
                onDenied: () -> Unit
            ) {
                // Verify foreground location is already granted (coarse is sufficient)
                val hasForeground = isPermissionGranted(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
                    isPermissionGranted(context, Manifest.permission.ACCESS_COARSE_LOCATION)

                if (!hasForeground) {
                    // Cannot request background without foreground — deny immediately
                    onDenied()
                    return
                }

                // Already granted
                if (isPermissionGranted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                    onGranted()
                    return
                }

                // Store callbacks and show rationale for background location
                pendingPermission = Manifest.permission.ACCESS_BACKGROUND_LOCATION
                currentRationale = PermissionRationale(
                    permissionName = "Background Location",
                    explanation = "Where needs background location access to share your location with friends even when the app is not in the foreground."
                )
                onGrantedCallback = onGranted
                onDeniedCallback = onDenied
                hasRequestedOnce = true
                showRationaleDialog = true
            }

            override fun requestNotificationPermission(
                onGranted: () -> Unit,
                onDenied: () -> Unit
            ) {
                // POST_NOTIFICATIONS only required on Android 13+ (API 33)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    onGranted()
                    return
                }

                val permission = Manifest.permission.POST_NOTIFICATIONS

                if (isPermissionGranted(context, permission)) {
                    onGranted()
                    return
                }

                // Store callbacks and show rationale
                pendingPermission = permission
                currentRationale = PermissionRationale(
                    permissionName = "Notifications",
                    explanation = "Where needs notification permission to alert you about messages, friend requests, and location updates."
                )
                onGrantedCallback = onGranted
                onDeniedCallback = onDenied
                hasRequestedOnce = true
                showRationaleDialog = true
            }
        }
    }

    // ── Rationale Dialog ──────────────────────────────────────────────────────
    if (showRationaleDialog && currentRationale != null) {
        RationaleDialog(
            rationale = currentRationale!!,
            onProceed = {
                showRationaleDialog = false
                val permission = pendingPermission
                if (permission != null) {
                    if (permission == Manifest.permission.ACCESS_BACKGROUND_LOCATION) {
                        backgroundLocationLauncher.launch(permission)
                    } else {
                        permissionLauncher.launch(permission)
                    }
                }
            },
            onDismiss = {
                // User dismissed rationale — cancel operation (Req 9.7)
                showRationaleDialog = false
                onDeniedCallback?.invoke()
                resetState(
                    resetShowRationale = { showRationaleDialog = false },
                    resetShowSettings = { showSettingsDialog = false },
                    resetCurrentRationale = { currentRationale = null },
                    resetPendingPermission = { pendingPermission = null },
                    resetOnGranted = { onGrantedCallback = null },
                    resetOnDenied = { onDeniedCallback = null },
                    resetHasRequested = { hasRequestedOnce = false }
                )
            }
        )
    }

    // ── Settings Dialog (permanent denial) ────────────────────────────────────
    if (showSettingsDialog) {
        SettingsDialog(
            permissionName = currentRationale?.permissionName ?: "Permission",
            onOpenSettings = {
                showSettingsDialog = false
                openAppSettings(context)
                // After directing to settings, cancel the current operation
                onDeniedCallback?.invoke()
                resetState(
                    resetShowRationale = { showRationaleDialog = false },
                    resetShowSettings = { showSettingsDialog = false },
                    resetCurrentRationale = { currentRationale = null },
                    resetPendingPermission = { pendingPermission = null },
                    resetOnGranted = { onGrantedCallback = null },
                    resetOnDenied = { onDeniedCallback = null },
                    resetHasRequested = { hasRequestedOnce = false }
                )
            },
            onDismiss = {
                showSettingsDialog = false
                onDeniedCallback?.invoke()
                resetState(
                    resetShowRationale = { showRationaleDialog = false },
                    resetShowSettings = { showSettingsDialog = false },
                    resetCurrentRationale = { currentRationale = null },
                    resetPendingPermission = { pendingPermission = null },
                    resetOnGranted = { onGrantedCallback = null },
                    resetOnDenied = { onDeniedCallback = null },
                    resetHasRequested = { hasRequestedOnce = false }
                )
            }
        )
    }

    return state
}

// ── Dialog Composables ────────────────────────────────────────────────────────

@Composable
private fun RationaleDialog(
    rationale: PermissionRationale,
    onProceed: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            // Premium icon hero — primary-tinted bubble matching the
            // first-run onboarding sheet's visual language.
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            }
        },
        title = {
            Text(
                text = rationale.permissionName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Text(
                text = rationale.explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        confirmButton = {
            Button(
                onClick = onProceed,
                shape = RoundedCornerShape(50),
            ) {
                Text(
                    text = "Allow",
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not now")
            }
        }
    )
}

@Composable
private fun SettingsDialog(
    permissionName: String,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp),
                )
            }
        },
        title = {
            Text(
                text = "$permissionName needs to be enabled",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Text(
                text = "You've blocked this permission. Open the system settings to turn it on — we'll bring you straight back when you're done.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        confirmButton = {
            Button(
                onClick = onOpenSettings,
                shape = RoundedCornerShape(50),
            ) {
                Text(
                    text = "Open settings",
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not now")
            }
        }
    )
}

// ── Utility functions ─────────────────────────────────────────────────────────

private fun isPermissionGranted(context: Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

/**
 * Find the Activity from a Context. Compose LocalContext provides
 * a ContextWrapper chain that eventually leads to an Activity.
 */
private fun Context.findActivity(): android.app.Activity? {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is android.app.Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private inline fun resetState(
    resetShowRationale: () -> Unit,
    resetShowSettings: () -> Unit,
    resetCurrentRationale: () -> Unit,
    resetPendingPermission: () -> Unit,
    resetOnGranted: () -> Unit,
    resetOnDenied: () -> Unit,
    resetHasRequested: () -> Unit
) {
    resetShowRationale()
    resetShowSettings()
    resetCurrentRationale()
    resetPendingPermission()
    resetOnGranted()
    resetOnDenied()
    resetHasRequested()
}
