package com.ovi.where.presentation.permission

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ovi.where.core.theme.Dimens
import com.ovi.where.core.utils.BatteryOptimizationUtils
import com.ovi.where.core.utils.PermissionUtils
import kotlinx.coroutines.launch

/**
 * Premium first-run permission onboarding.
 *
 * Design choice:
 *   The previous flow stacked two `AlertDialog`s on the map screen and
 *   never even mentioned background location or battery optimization,
 *   leaving live sharing silently broken on most OEMs. This screen
 *   replaces both with a single full-height bottom sheet that lists
 *   *every* condition the app needs in one place.
 *
 *   Modeled after the patterns used by Life360, Google Maps, WhatsApp,
 *   and Discord:
 *     • One sheet, not a chain of modal dialogs.
 *     • Education first — concrete benefit copy ("See yourself on the
 *       map", "Don't put Where to sleep") instead of regurgitating the
 *       system permission name.
 *     • Each row shows current grant state with a calm green check or
 *       a primary "Allow" button. Granted rows stay visible so the user
 *       feels progress.
 *     • One sticky CTA at the bottom: "Get started" lights up once every
 *       *required* step is satisfied; "Maybe later" is always available
 *       so the sheet never traps the user.
 *
 * The sheet is fully dismissable. After dismissal the [PermissionsScreen]
 * in Settings remains the canonical place to revisit any step.
 */
@RequiresApi(Build.VERSION_CODES.Q)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionOnboardingSheet(
    onDismiss: () -> Unit,
    onComplete: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Refresh trigger — bumps on ON_RESUME so coming back from a system
    // settings page (battery opt, app info) re-evaluates grant state.
    var refreshTick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    @Suppress("UNUSED_EXPRESSION") refreshTick

    // ── Per-step grant state ───────────────────────────────────────────
    val foregroundGranted = PermissionUtils.hasLocationPermissions(context)
    val backgroundGranted = PermissionUtils.hasBackgroundLocationPermission(context)
    val notificationsGranted = PermissionUtils.hasNotificationPermission(context)
    val batteryOptDisabled = BatteryOptimizationUtils.isBatteryOptimizationDisabled(context)

    fun isGranted(step: PermissionStep): Boolean = when (step) {
        is PermissionStep.ForegroundLocation -> foregroundGranted
        is PermissionStep.BackgroundLocation -> backgroundGranted
        is PermissionStep.Notifications -> notificationsGranted
        is PermissionStep.BatteryOptimization -> batteryOptDisabled
    }

    // ── Permission launchers ───────────────────────────────────────────
    val foregroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* refreshTick handles state update on resume */ }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* refreshTick handles state update on resume */ }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* refreshTick handles state update on resume */ }

    fun handleAllow(step: PermissionStep) {
        when (step) {
            is PermissionStep.ForegroundLocation -> {
                foregroundLocationLauncher.launch(step.permissions.toTypedArray())
            }
            is PermissionStep.BackgroundLocation -> {
                if (!foregroundGranted) {
                    // System will reject the request without foreground;
                    // route the user to grant foreground first.
                    foregroundLocationLauncher.launch(
                        PermissionStep.ForegroundLocation.permissions.toTypedArray()
                    )
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    backgroundLocationLauncher.launch(step.permission)
                }
            }
            is PermissionStep.Notifications -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(step.permission)
                }
            }
            is PermissionStep.BatteryOptimization -> {
                BatteryOptimizationUtils.openBatteryOptimizationSettings(context)
            }
        }
    }

    val steps = remember { PermissionStep.ALL }
    val allRequiredSatisfied = steps.filter { it.required }.all { isGranted(it) }

    fun closeSheetAndCallback(callback: () -> Unit) {
        scope.launch {
            sheetState.hide()
        }.invokeOnCompletion {
            if (!sheetState.isVisible) callback()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // Pull the sheet to ~92% of screen height so the hero + 4 rows + CTA
        // fit without scrolling on most devices, while still leaving the
        // top status bar visible.
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
        // No drag handle — the sheet is non-trivial and shouldn't feel like
        // a quick action; the explicit "Maybe later" button handles dismissal.
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 480.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.spaceLarge)
                .padding(top = Dimens.spaceLarge)
        ) {
            HeroHeader()

            Spacer(Modifier.height(Dimens.spaceXLarge))

            steps.forEachIndexed { index, step ->
                if (index > 0) Spacer(Modifier.height(10.dp))
                PermissionStepCard(
                    step = step,
                    granted = isGranted(step),
                    // Background location has a hard prerequisite — disable
                    // the Allow button until foreground is granted.
                    enabled = step !is PermissionStep.BackgroundLocation || foregroundGranted,
                    onAllowClick = { handleAllow(step) },
                )
            }

            Spacer(Modifier.height(Dimens.spaceXLarge))

            PrivacyFooterRow()

            Spacer(Modifier.height(Dimens.spaceLarge))

            // ── Sticky CTA group ─────────────────────────────────────────
            Button(
                onClick = {
                    closeSheetAndCallback(onComplete)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                ),
                shape = RoundedCornerShape(16.dp),
                enabled = allRequiredSatisfied,
            ) {
                Text(
                    text = if (allRequiredSatisfied) "Get started"
                    else "Allow required permissions to continue",
                    fontWeight = FontWeight.SemiBold,
                )
            }

            TextButton(
                onClick = {
                    closeSheetAndCallback(onDismiss)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text(
                    text = "Maybe later",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.navigationBarsPadding())
            Spacer(Modifier.height(Dimens.spaceMedium))
        }
    }
}

@Composable
private fun HeroHeader() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Hero glyph — large, primary-tinted bubble so the sheet has a
        // clear focal point on first impression.
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
        }

        Spacer(Modifier.height(Dimens.spaceLarge))

        Text(
            text = "A few quick permissions",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Where uses these to keep you on the map and connected with friends.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Dimens.spaceMedium),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun PermissionStepCard(
    step: PermissionStep,
    granted: Boolean,
    enabled: Boolean,
    onAllowClick: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    val borderColor = if (granted) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(18.dp),
            )
            .clickable { expanded = !expanded },
    ) {
        Column(
            modifier = Modifier.padding(
                start = 14.dp,
                end = 14.dp,
                top = 14.dp,
                bottom = 14.dp,
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Leading icon bubble.
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = step.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }

                Spacer(Modifier.width(Dimens.spaceMedium))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = step.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (!step.required) {
                            Spacer(Modifier.width(8.dp))
                            OptionalPill()
                        }
                    }
                    Text(
                        text = step.subline,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.width(Dimens.spaceSmall))

                // Trailing affordance: green check when granted, primary
                // "Allow" pill when not. The pill matches the height of
                // the leading icon for visual rhythm.
                if (granted) {
                    GrantedBadge()
                } else {
                    AllowPill(enabled = enabled, onClick = onAllowClick)
                }
            }

            // Expandable body — keeps the default state compact and gives
            // curious users a deeper "why" without a navigation jump.
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Text(
                    text = step.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun OptionalPill() {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            text = "Optional",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun GrantedBadge() {
    val tint = Color(0xFF34C759)
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = "Granted",
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun AllowPill(enabled: Boolean, onClick: () -> Unit) {
    val container = if (enabled) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
    val content = MaterialTheme.colorScheme.onPrimary

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        color = container,
        contentColor = content,
        modifier = Modifier.height(36.dp),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Allow",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun PrivacyFooterRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "Your location is shared only with people you choose. " +
                "You can change anything later from Settings → Privacy.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
