package com.ovi.where.presentation.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PublicOff
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ovi.where.core.theme.Dimens
import com.ovi.where.core.utils.BatteryOptimizationUtils
import com.ovi.where.core.utils.PermissionUtils
import com.ovi.where.presentation.common.WhereTopAppBar

/**
 * Surfaces the four runtime conditions the app needs for "live location +
 * reliable chat notifications" to actually work. Each row shows current
 * status (granted / not granted) and offers a one-tap action to fix it.
 *
 * Why a dedicated screen?
 *  • These permissions degrade silently on Android — denying
 *    POST_NOTIFICATIONS doesn't crash anything, the user just stops getting
 *    pushes. Without an overview, users blame the app.
 *  • OEMs (Xiaomi, Huawei, OPPO, etc.) aggressively kill background
 *    services unless battery optimization is disabled. The foreground
 *    service notification helps, but the optimization exemption is the
 *    real fix.
 *  • Bundling them in one place makes onboarding triage easier — support
 *    can say "go to Settings → Permissions and check that everything's
 *    green" instead of walking users through 4 different system screens.
 *
 * Status refresh: we re-evaluate every permission on every ON_RESUME so
 * returning from the system settings page reflects the change immediately.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Snapshot all permission states. We re-read them on each ON_RESUME so
    // the UI doesn't lie about a permission the user just granted in the
    // system settings page.
    var refreshTick by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // refreshTick keeps these recomputed on resume.
    @Suppress("UNUSED_EXPRESSION") refreshTick
    val foregroundLocationGranted = PermissionUtils.hasLocationPermissions(context)
    val backgroundLocationGranted = PermissionUtils.hasBackgroundLocationPermission(context)
    val notificationsGranted = PermissionUtils.hasNotificationPermission(context)
    val batteryOptDisabled = BatteryOptimizationUtils.isBatteryOptimizationDisabled(context)

    // Foreground location launcher — multiple permissions request because
    // we ask for ACCESS_FINE_LOCATION + ACCESS_COARSE_LOCATION as a pair.
    val foregroundLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { /* refreshTick handles state update on resume */ }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* refreshTick handles state update on resume */ }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* refreshTick handles state update on resume */ }

    Scaffold(
        topBar = {
            WhereTopAppBar(
                title = "Permissions",
                onNavigateBack = onNavigateBack
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.spaceLarge)
        ) {
            Spacer(modifier = Modifier.height(Dimens.spaceLarge))

            Text(
                text = "Where needs these permissions to share your location reliably and notify you of activity from your friends and groups.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(Dimens.spaceXLarge))

            // ── Location permissions ─────────────────────────────────────
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp
            ) {
                Column {
                    PermissionRow(
                        icon = Icons.Outlined.LocationOn,
                        title = "Location",
                        subtitle = "Required to share your location with friends and groups",
                        granted = foregroundLocationGranted,
                        onClick = {
                            if (!foregroundLocationGranted) {
                                foregroundLocationLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            } else {
                                BatteryOptimizationUtils.openAppSettings(context)
                            }
                        }
                    )
                    PermissionRow(
                        icon = Icons.Outlined.PublicOff,
                        title = "Background Location",
                        subtitle = "Lets the app keep sharing when you're not actively using it",
                        granted = backgroundLocationGranted,
                        // Background location can only be requested AFTER
                        // foreground location is granted — the system flat-out
                        // denies otherwise. Disable the button until the
                        // prerequisite is met to avoid a confusing no-op.
                        enabled = foregroundLocationGranted,
                        onClick = {
                            if (!backgroundLocationGranted) {
                                backgroundLocationLauncher.launch(
                                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                                )
                            } else {
                                BatteryOptimizationUtils.openAppSettings(context)
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(Dimens.spaceLarge))

            // ── Notifications ────────────────────────────────────────────
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp
            ) {
                PermissionRow(
                    icon = Icons.Outlined.Notifications,
                    title = "Notifications",
                    subtitle = "Receive messages, friend requests, and meetup updates",
                    granted = notificationsGranted,
                    onClick = {
                        if (!notificationsGranted) {
                            // POST_NOTIFICATIONS only exists on Android 13+.
                            // On older releases the permission is granted at
                            // install — the launch() call is a safe no-op.
                            if (android.os.Build.VERSION.SDK_INT
                                >= android.os.Build.VERSION_CODES.TIRAMISU
                            ) {
                                notificationLauncher.launch(
                                    Manifest.permission.POST_NOTIFICATIONS
                                )
                            } else {
                                BatteryOptimizationUtils.openNotificationSettings(context)
                            }
                        } else {
                            BatteryOptimizationUtils.openNotificationSettings(context)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(Dimens.spaceLarge))

            // ── Battery optimization ─────────────────────────────────────
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp
            ) {
                PermissionRow(
                    icon = Icons.Outlined.BatteryFull,
                    title = "Battery Optimization",
                    subtitle = "Disable battery optimization so location sharing keeps running in the background",
                    granted = batteryOptDisabled,
                    onClick = {
                        BatteryOptimizationUtils.openBatteryOptimizationSettings(context)
                    }
                )
            }

            Spacer(modifier = Modifier.height(Dimens.spaceXLarge))

            // ── OEM hint ─────────────────────────────────────────────────
            // Some OEMs (Xiaomi, Huawei, OPPO, Realme, Vivo) ignore the
            // generic battery-opt exemption and ship their own kill-list.
            // Surface a soft hint so users on those devices know to look
            // for "Auto-start" / "Battery saver" entries in their system
            // settings. Not a permission per se — just a nudge.
            if (BatteryOptimizationUtils.isAggressiveOemKnown()) {
                Text(
                    text = "Heads up: ${android.os.Build.MANUFACTURER.replaceFirstChar { it.titlecase() }} devices have an extra battery-saver setting (often called \"Auto-start\" or \"Allow in background\"). Check your device settings if location sharing keeps stopping.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Dimens.spaceMedium)
                )
                Spacer(modifier = Modifier.height(Dimens.spaceMedium))
                TextButton(
                    onClick = { BatteryOptimizationUtils.openAppSettings(context) }
                ) {
                    Text("Open device settings")
                }
            }

            Spacer(modifier = Modifier.height(Dimens.space3XLarge))
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    granted: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val rowColor = if (enabled) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    val statusTint = when {
        granted -> Color(0xFF34C759) // success green
        else -> MaterialTheme.colorScheme.error
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Dimens.spaceLarge, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Leading icon — keeps the visual rhythm of the existing settings rows.
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.width(Dimens.spaceLarge))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = rowColor
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(Dimens.spaceMedium))

        // Status pill: filled green check when granted, outlined warning
        // when not. Tap target stays the whole row — the icon is a status
        // indicator, not a button on its own.
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(statusTint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (granted) Icons.Filled.CheckCircle
                    else Icons.Outlined.Warning,
                contentDescription = if (granted) "Granted" else "Not granted",
                tint = statusTint,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
