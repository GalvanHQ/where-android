package com.ovi.where.presentation.profile

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.ovi.where.core.theme.Dimens

/**
 * Social-focused Profile screen inspired by Instagram / WhatsApp.
 * Read-only display — editing happens on [EditProfileScreen], settings on [SettingsScreen].
 */
@Composable
fun ProfileScreen(
    onNavigateToEditProfile: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val profile = uiState.profile

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(Dimens.spaceXLarge))

        // ── Avatar + Name + Username ─────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.spaceXLarge),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Avatar
            if (profile?.photoUrl != null) {
                AsyncImage(
                    model = profile.photoUrl,
                    contentDescription = "Profile photo",
                    modifier = Modifier
                        .size(Dimens.avatarCircle)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(Dimens.avatarCircle)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Person,
                        contentDescription = null,
                        modifier = Modifier.size(Dimens.iconSizeXLarge),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(Dimens.spaceLarge))

            // Display name + verified badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = profile?.displayName ?: "—",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (profile?.isEmailVerified == true) {
                    Spacer(modifier = Modifier.width(Dimens.spaceSmall))
                    Icon(
                        imageVector = Icons.Outlined.Verified,
                        contentDescription = "Verified",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Username
            if (!profile?.username.isNullOrBlank()) {
                Text(
                    text = "@${profile!!.username}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Bio
            if (!profile?.bio.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(Dimens.spaceMedium))
                Text(
                    text = profile!!.bio,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = Dimens.space2XLarge)
                )
            }
        }

        Spacer(modifier = Modifier.height(Dimens.spaceXLarge))

        // ── Stats row ────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.space3XLarge),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ProfileStat(
                count = uiState.groupCount.toString(),
                label = "Groups",
                icon = Icons.Outlined.Group
            )
            ProfileStat(
                count = uiState.friendCount.toString(),
                label = "Friends",
                icon = Icons.Outlined.Person
            )
            ProfileStat(
                count = uiState.sharedLocations.toString(),
                label = "Shared",
                icon = Icons.Outlined.LocationOn
            )
        }

        Spacer(modifier = Modifier.height(Dimens.spaceXLarge))

        // ── Action buttons ───────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.spaceXLarge),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMedium)
        ) {
            Button(
                onClick = onNavigateToEditProfile,
                modifier = Modifier
                    .weight(1f)
                    .height(Dimens.buttonHeightSmall),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(Dimens.iconSizeSmall)
                )
                Spacer(modifier = Modifier.width(Dimens.spaceMedium))
                Text("Edit Profile", style = MaterialTheme.typography.labelLarge)
            }

            Button(
                onClick = onNavigateToSettings,
                modifier = Modifier
                    .weight(1f)
                    .height(Dimens.buttonHeightSmall),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(
                    Icons.Outlined.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(Dimens.iconSizeSmall)
                )
                Spacer(modifier = Modifier.width(Dimens.spaceMedium))
                Text("Settings", style = MaterialTheme.typography.labelLarge)
            }
        }

        Spacer(modifier = Modifier.height(Dimens.spaceXLarge))

        HorizontalDivider(modifier = Modifier.padding(horizontal = Dimens.spaceLarge))

        Spacer(modifier = Modifier.height(Dimens.spaceLarge))

        // ── Quick actions card ───────────────────────────────────────
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.spaceLarge),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column {
                QuickActionRow(
                    icon = Icons.AutoMirrored.Outlined.Chat,
                    title = "Messages",
                    subtitle = "Your conversations",
                    onClick = { /* handled by bottom nav */ }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 56.dp),
                    thickness = 0.5.dp
                )
                QuickActionRow(
                    icon = Icons.Outlined.Group,
                    title = "Groups",
                    subtitle = "Manage your groups",
                    onClick = { /* handled by bottom nav */ }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 56.dp),
                    thickness = 0.5.dp
                )
                QuickActionRow(
                    icon = Icons.Outlined.LocationOn,
                    title = "Location Sharing",
                    subtitle = "Active sharing sessions",
                    onClick = { /* handled by bottom nav */ }
                )
            }
        }

        Spacer(modifier = Modifier.height(Dimens.space3XLarge))
    }
}

@Composable
private fun ProfileStat(
    count: String,
    label: String,
    icon: ImageVector
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(Dimens.iconSizeMedium),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(Dimens.spaceSmall))
        Text(
            text = count,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun QuickActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
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
            modifier = Modifier.size(Dimens.iconSizeMedium),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(Dimens.spaceLarge))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
