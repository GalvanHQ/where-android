package com.ovi.where.presentation.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ovi.where.core.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isSignedOut) {
        if (uiState.isSignedOut) onSignOut()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // ── General ──────────────────────────────────────────────
            SettingsSectionHeader("General")
            SettingsRow(
                icon = Icons.Outlined.Notifications,
                title = "Notifications",
                subtitle = "Push notifications & sounds",
                onClick = { /* TODO */ }
            )
            SettingsRow(
                icon = Icons.Outlined.DarkMode,
                title = "Appearance",
                subtitle = "Theme & display settings",
                onClick = { /* TODO */ }
            )
            SettingsRow(
                icon = Icons.Outlined.Storage,
                title = "Data & Storage",
                subtitle = "Network usage & storage",
                onClick = { /* TODO */ }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = Dimens.spaceMedium))

            // ── Privacy & Security ───────────────────────────────────
            SettingsSectionHeader("Privacy & Security")
            SettingsRow(
                icon = Icons.Outlined.Lock,
                title = "Account Security",
                subtitle = "Password & two-factor authentication",
                onClick = { /* TODO */ }
            )
            SettingsRow(
                icon = Icons.Outlined.PrivacyTip,
                title = "Privacy",
                subtitle = "Location sharing & visibility",
                onClick = { /* TODO */ }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = Dimens.spaceMedium))

            // ── About ────────────────────────────────────────────────
            SettingsSectionHeader("About")
            SettingsRow(
                icon = Icons.AutoMirrored.Outlined.HelpOutline,
                title = "Help & Support",
                subtitle = "FAQ, contact us",
                onClick = { /* TODO */ }
            )
            SettingsRow(
                icon = Icons.Outlined.Info,
                title = "About Where",
                subtitle = "Version, licenses",
                onClick = { /* TODO */ }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = Dimens.spaceMedium))

            // ── Sign out ─────────────────────────────────────────────
            SettingsRow(
                icon = Icons.AutoMirrored.Outlined.Logout,
                title = "Sign Out",
                subtitle = uiState.email,
                onClick = { viewModel.signOut() },
                tint = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(Dimens.space2XLarge))
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(
            start = Dimens.spaceLarge,
            top = Dimens.spaceLarge,
            bottom = Dimens.spaceMedium
        )
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.spaceLarge, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(Dimens.iconSizeMedium),
            tint = tint
        )
        Spacer(modifier = Modifier.width(Dimens.spaceLarge))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (tint == MaterialTheme.colorScheme.error) tint
                        else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
