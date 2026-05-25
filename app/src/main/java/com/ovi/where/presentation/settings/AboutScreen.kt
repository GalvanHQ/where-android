package com.ovi.where.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.ovi.where.presentation.common.WhereTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ovi.where.BuildConfig
import com.ovi.where.core.theme.Dimens

private data class LicenseEntry(
    val name: String,
    val license: String
)

private val OPEN_SOURCE_LICENSES = listOf(
    LicenseEntry("Jetpack Compose", "Apache License 2.0"),
    LicenseEntry("Kotlin", "Apache License 2.0"),
    LicenseEntry("Hilt (Dagger)", "Apache License 2.0"),
    LicenseEntry("Room", "Apache License 2.0"),
    LicenseEntry("Firebase SDK", "Apache License 2.0"),
    LicenseEntry("Coil", "Apache License 2.0"),
    LicenseEntry("OkHttp", "Apache License 2.0"),
    LicenseEntry("Retrofit", "Apache License 2.0"),
    LicenseEntry("Timber", "Apache License 2.0"),
    LicenseEntry("Google Maps SDK", "Google Maps Platform Terms"),
    LicenseEntry("Socket.IO Client", "MIT License"),
    LicenseEntry("kotlinx.serialization", "Apache License 2.0"),
    LicenseEntry("kotlinx.coroutines", "Apache License 2.0"),
    LicenseEntry("WorkManager", "Apache License 2.0"),
    LicenseEntry("DataStore", "Apache License 2.0")
)

/**
 * About screen displaying app version info, open-source licenses,
 * and links to Terms of Service and Privacy Policy. The legal pages live
 * in-app at [TermsOfServiceScreen] and [PrivacyPolicyScreen] (no public
 * domain yet, so we don't punt to the browser).
 *
 * Requirements: 8.10
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit,
    onNavigateToTerms: () -> Unit = {},
    onNavigateToPrivacyPolicy: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            WhereTopAppBar(
                title = "About Where",
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

            // ── Version info section ─────────────────────────────────────
            Text(
                text = "App Info",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = Dimens.spaceSmall)
            )

            Spacer(modifier = Modifier.height(Dimens.spaceMedium))

            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = Dimens.spaceLarge,
                        vertical = 14.dp
                    )
                ) {
                    AboutInfoRow(
                        label = "Version",
                        value = BuildConfig.VERSION_NAME
                    )
                    Spacer(modifier = Modifier.height(Dimens.spaceMedium))
                    AboutInfoRow(
                        label = "Build",
                        value = BuildConfig.VERSION_CODE.toString()
                    )
                }
            }

            Spacer(modifier = Modifier.height(Dimens.spaceXLarge))

            // ── Legal links section ──────────────────────────────────────
            Text(
                text = "Legal",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = Dimens.spaceSmall)
            )

            Spacer(modifier = Modifier.height(Dimens.spaceMedium))

            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp
            ) {
                Column {
                    LegalLinkRow(
                        icon = Icons.Outlined.Description,
                        title = "Terms of Service",
                        onClick = onNavigateToTerms
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                    LegalLinkRow(
                        icon = Icons.Outlined.Policy,
                        title = "Privacy Policy",
                        onClick = onNavigateToPrivacyPolicy
                    )
                }
            }

            Spacer(modifier = Modifier.height(Dimens.spaceXLarge))

            // ── Open-source licenses section ─────────────────────────────
            Text(
                text = "Open-Source Licenses",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = Dimens.spaceSmall)
            )

            Spacer(modifier = Modifier.height(Dimens.spaceMedium))

            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp
            ) {
                Column {
                    OPEN_SOURCE_LICENSES.forEachIndexed { index, entry ->
                        LicenseRow(entry = entry)
                        if (index < OPEN_SOURCE_LICENSES.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = Dimens.spaceLarge),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Dimens.space3XLarge))
        }
    }
}

@Composable
private fun AboutInfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LegalLinkRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit
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
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(Dimens.spaceLarge))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun LicenseRow(entry: LicenseEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spaceLarge, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = entry.license,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
