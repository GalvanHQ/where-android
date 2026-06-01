package com.ovi.where.presentation.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ovi.where.core.theme.Dimens
import com.ovi.where.presentation.common.WhereTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onSignOut: () -> Unit,
    onNavigateToNotificationPreferences: () -> Unit = {},
    onNavigateToAppearance: () -> Unit = {},
    onNavigateToDataStorage: () -> Unit = {},
    onNavigateToPermissions: () -> Unit = {},
    onNavigateToSecurity: () -> Unit = {},
    onNavigateToPrivacy: () -> Unit = {},
    onNavigateToHelp: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    onNavigateToDevelopers: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // ── Entrance animation ───────────────────────────────────────────────
    val contentAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        contentAlpha.animateTo(
            1f,
            animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
        )
    }

    LaunchedEffect(uiState.isSignedOut) {
        if (uiState.isSignedOut) onSignOut()
    }

    Scaffold(
        topBar = {
            WhereTopAppBar(
                title = "Settings",
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
                .alpha(contentAlpha.value)
                .padding(horizontal = Dimens.spaceLarge)
        ) {
            Spacer(modifier = Modifier.height(Dimens.spaceLarge))

            // ── General section ──────────────────────────────────────────
            SettingsSectionHeader("General")

            Spacer(modifier = Modifier.height(Dimens.spaceMedium))

            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp
            ) {
                Column {
                    SettingsRow(
                        icon = Icons.Outlined.Notifications,
                        title = "Notifications",
                        subtitle = "Push notifications & sounds",
                        onClick = onNavigateToNotificationPreferences
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Outlined.DarkMode,
                        title = "Appearance",
                        subtitle = "Theme & display settings",
                        onClick = onNavigateToAppearance
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Outlined.Storage,
                        title = "Data & Storage",
                        subtitle = "Network usage & storage",
                        onClick = onNavigateToDataStorage
                    )
                }
            }

            Spacer(modifier = Modifier.height(Dimens.spaceXLarge))

            // ── Permissions section ──────────────────────────────────────
            // Surfaces the four runtime conditions the app needs (location,
            // background location, notifications, battery optimization).
            // Pre-empts user confusion when location sharing or
            // notifications stop working — they can self-diagnose here
            // instead of contacting support.
            SettingsSectionHeader("Permissions")

            Spacer(modifier = Modifier.height(Dimens.spaceMedium))

            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp
            ) {
                SettingsRow(
                    icon = Icons.Outlined.Shield,
                    title = "App Permissions",
                    subtitle = "Location, notifications, and battery",
                    onClick = onNavigateToPermissions
                )
            }

            Spacer(modifier = Modifier.height(Dimens.spaceXLarge))

            // ── Privacy & Security section ───────────────────────────────
            SettingsSectionHeader("Privacy & Security")

            Spacer(modifier = Modifier.height(Dimens.spaceMedium))

            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp
            ) {
                Column {
                    SettingsRow(
                        icon = Icons.Outlined.Lock,
                        title = "Account Security",
                        subtitle = "Password & two-factor authentication",
                        onClick = onNavigateToSecurity
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Outlined.PrivacyTip,
                        title = "Privacy",
                        subtitle = "Location sharing & visibility",
                        onClick = onNavigateToPrivacy
                    )
                }
            }

            Spacer(modifier = Modifier.height(Dimens.spaceXLarge))

            // ── About section ────────────────────────────────────────────
            SettingsSectionHeader("About")

            Spacer(modifier = Modifier.height(Dimens.spaceMedium))

            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp
            ) {
                Column {
                    SettingsRow(
                        icon = Icons.AutoMirrored.Outlined.HelpOutline,
                        title = "Help & Support",
                        subtitle = "FAQ, contact us",
                        onClick = onNavigateToHelp
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Outlined.Info,
                        title = "About Where",
                        subtitle = "Version, licenses",
                        onClick = onNavigateToAbout
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Outlined.Code,
                        title = "Developers",
                        subtitle = "The team behind Where",
                        onClick = onNavigateToDevelopers
                    )
                }
            }

            Spacer(modifier = Modifier.height(Dimens.spaceXLarge))

            // ── Sign out ─────────────────────────────────────────────────
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                tonalElevation = 0.dp
            ) {
                SettingsRow(
                    icon = Icons.AutoMirrored.Outlined.Logout,
                    title = if (uiState.isSigningOut) "Signing out…" else "Sign Out",
                    subtitle = uiState.email,
                    onClick = { viewModel.signOut() },
                    tint = MaterialTheme.colorScheme.error,
                    showChevron = false,
                    isLoading = uiState.isSigningOut
                )
            }

            Spacer(modifier = Modifier.height(Dimens.space3XLarge))
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = Dimens.spaceSmall)
    )
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    showChevron: Boolean = true,
    /**
     * When `true`, the icon is replaced with a circular progress indicator
     * (same color as the row's tint), the row's subtitle is hidden in
     * favour of a "Signing out…" style label provided by the caller via
     * [subtitle], and clicks are no-oped to prevent double-fire while the
     * action is in flight.
     */
    isLoading: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isLoading, onClick = onClick)
            .padding(horizontal = Dimens.spaceLarge, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon container — swaps to a spinner while loading.
        Box(
            modifier = Modifier
                .size(Dimens.settingIconContainer)
                .background(
                    color = if (tint == MaterialTheme.colorScheme.error)
                        MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                    else
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(Dimens.cornerSmall)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(Dimens.iconSizeMedium),
                    color = tint,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(Dimens.iconSizeMedium),
                    tint = tint
                )
            }
        }
        Spacer(modifier = Modifier.width(Dimens.spaceLarge))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (tint == MaterialTheme.colorScheme.error) tint
                else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (showChevron && !isLoading) {
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(Dimens.iconSizeMedium),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}
