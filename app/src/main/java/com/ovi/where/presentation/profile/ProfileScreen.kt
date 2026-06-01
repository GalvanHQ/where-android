package com.ovi.where.presentation.profile

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddLink
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.ovi.where.core.theme.Dimens

/**
 * Profile screen — clean, content-first layout inspired by Instagram /
 * Facebook: a compact header (avatar + stats + name + bio + home), primary
 * actions, then a single "Links" list where every saved social link is its
 * own full, tappable row.
 */
@Composable
fun ProfileScreen(
    onNavigateToEditProfile: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToMap: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val profile = uiState.profile
    val context = LocalContext.current

    // ── Entrance animation ───────────────────────────────────────────────
    val contentAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        contentAlpha.animateTo(
            1f,
            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
        )
    }

    // ── Live ring animation for avatar border ────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "live_ring")
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring_alpha"
    )

    val hasSocial = !profile?.facebookUrl.isNullOrBlank() ||
        !profile?.instagramUrl.isNullOrBlank() ||
        !profile?.linkedinUrl.isNullOrBlank()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .statusBarsPadding()
            .alpha(contentAlpha.value),
        contentPadding = PaddingValues(bottom = Dimens.space3XLarge)
    ) {
        // ── Header: Avatar + Stats + Name + Bio ─────────────────────────
        item {
            ProfileHeaderCard(
                displayName = profile?.displayName ?: "—",
                username = profile?.username,
                bio = profile?.bio,
                photoUrl = profile?.photoUrl,
                groupCount = uiState.groupCount,
                friendCount = uiState.friendCount,
                sharedCount = uiState.sharedLocations,
                ringAlpha = ringAlpha
            )
        }

        // ── Action Buttons ──────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.spaceLarge),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMedium)
            ) {
                Button(
                    onClick = onNavigateToEditProfile,
                    modifier = Modifier
                        .weight(1f)
                        .height(Dimens.buttonHeightSmall),
                    shape = RoundedCornerShape(Dimens.cornerMedium),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(Dimens.iconSizeSmall)
                    )
                    Spacer(modifier = Modifier.width(Dimens.spaceMedium))
                    Text("Edit profile", style = MaterialTheme.typography.labelLarge)
                }

                OutlinedButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier
                        .weight(1f)
                        .height(Dimens.buttonHeightSmall),
                    shape = RoundedCornerShape(Dimens.cornerMedium),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface
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
        }

        // ── Home section ────────────────────────────────────────────────
        if (profile != null) {
            item {
                SectionHeader(
                    title = "Home",
                    showEdit = profile.hasHome,
                    onEdit = onNavigateToEditProfile
                )
            }
            item {
                SectionCard {
                    if (profile.hasHome) {
                        AboutRow(
                            icon = ImageVector.vectorResource(id = com.ovi.where.R.drawable.home_outlined),
                            title = "Home",
                            subtitle = profile.homeLabel.ifBlank { "Location set" },
                            onClick = {
                                viewModel.showHomeOnMap()
                                onNavigateToMap()
                            },
                            trailingIconRes = com.ovi.where.R.drawable.navigate_to
                        )
                    } else {
                        SectionEmptyState(
                            icon = ImageVector.vectorResource(id = com.ovi.where.R.drawable.home_outlined),
                            text = "Set your home so friends know when you're around.",
                            actionLabel = "Set home",
                            onAction = onNavigateToEditProfile
                        )
                    }
                }
            }

            // ── Social section ────────────────────────────────────────────
            item {
                SectionHeader(
                    title = "Social",
                    showEdit = hasSocial,
                    onEdit = onNavigateToEditProfile
                )
            }
            item {
                SectionCard {
                    if (hasSocial) {
                        Column {
                            val rows = buildList<@Composable () -> Unit> {
                                if (profile.facebookUrl.isNotBlank()) add {
                                    AboutRow(
                                        icon = ImageVector.vectorResource(id = com.ovi.where.R.drawable.facebook),
                                        title = "Facebook",
                                        subtitle = prettySocialHandle(profile.facebookUrl),
                                        onClick = { openSocialLink(context, profile.facebookUrl) }
                                    )
                                }
                                if (profile.instagramUrl.isNotBlank()) add {
                                    AboutRow(
                                        icon = ImageVector.vectorResource(id = com.ovi.where.R.drawable.instagram),
                                        title = "Instagram",
                                        subtitle = prettySocialHandle(profile.instagramUrl),
                                        onClick = { openSocialLink(context, profile.instagramUrl) }
                                    )
                                }
                                if (profile.linkedinUrl.isNotBlank()) add {
                                    AboutRow(
                                        icon = ImageVector.vectorResource(id = com.ovi.where.R.drawable.linkedin),
                                        title = "LinkedIn",
                                        subtitle = prettySocialHandle(profile.linkedinUrl),
                                        onClick = { openSocialLink(context, profile.linkedinUrl) }
                                    )
                                }
                            }
                            rows.forEachIndexed { index, row ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 64.dp),
                                        thickness = 0.5.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    )
                                }
                                row()
                            }
                        }
                    } else {
                        SectionEmptyState(
                            icon = Icons.Outlined.AddLink,
                            text = "Link your Facebook, Instagram, or LinkedIn.",
                            actionLabel = "Add links",
                            onAction = onNavigateToEditProfile
                        )
                    }
                }
            }
        }
    }
}

// ── Section header (title + optional Edit action) ────────────────────────────
@Composable
private fun SectionHeader(
    title: String,
    showEdit: Boolean,
    onEdit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = Dimens.spaceLarge,
                end = Dimens.spaceLarge,
                top = Dimens.spaceXLarge,
                bottom = Dimens.spaceMedium
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        if (showEdit) {
            Text(
                text = "Edit",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onEdit)
            )
        }
    }
}

// ── Section card wrapper ──────────────────────────────────────────────────────
@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spaceLarge),
        shape = RoundedCornerShape(Dimens.cornerLarge),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp
    ) {
        content()
    }
}

// ── Per-section empty state (compact, inviting) ──────────────────────────────
@Composable
private fun SectionEmptyState(
    icon: ImageVector,
    text: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onAction)
            .padding(horizontal = Dimens.spaceLarge, vertical = Dimens.spaceLarge),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(Dimens.spaceLarge))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(Dimens.spaceMedium))
        Text(
            text = actionLabel,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

// ── Profile Header Card (Avatar left, Stats right — Instagram-style) ─────────
@Composable
private fun ProfileHeaderCard(
    displayName: String,
    username: String?,
    bio: String?,
    photoUrl: String?,
    groupCount: Int,
    friendCount: Int,
    sharedCount: Int,
    ringAlpha: Float
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val profilePhotoPixelSize = remember(density) {
        with(density) { 80.dp.roundToPx() }
    }

    val profilePhotoRequest = remember(photoUrl, profilePhotoPixelSize) {
        if (photoUrl.isNullOrBlank()) {
            null
        } else {
            ImageRequest.Builder(context)
                .data(photoUrl)
                .crossfade(true)
                .size(profilePhotoPixelSize)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .networkCachePolicy(CachePolicy.ENABLED)
                .memoryCacheKey(photoUrl)
                .diskCacheKey(photoUrl)
                .build()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spaceLarge, vertical = Dimens.spaceLarge)
    ) {
        // ── Row: Avatar + Stats ──────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with animated gradient ring
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .border(
                            width = 2.5.dp,
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = ringAlpha),
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = ringAlpha),
                                    MaterialTheme.colorScheme.primary.copy(alpha = ringAlpha)
                                )
                            ),
                            shape = CircleShape
                        )
                )

                if (profilePhotoRequest != null) {
                    AsyncImage(
                        model = profilePhotoRequest,
                        contentDescription = "Profile photo",
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.Person,
                            contentDescription = null,
                            modifier = Modifier.size(Dimens.iconSizeLarge),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(Dimens.spaceXLarge))

            // Stats row
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(count = groupCount, label = "Groups")
                StatItem(count = friendCount, label = "Friends")
                StatItem(count = sharedCount, label = "Shared")
            }
        }

        Spacer(modifier = Modifier.height(Dimens.spaceLarge))

        // ── Name + Username ──────────────────────────────────────────────
        Text(
            text = displayName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        if (!username.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(Dimens.spaceXSmall))
            Text(
                text = "@$username",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ── Bio ──────────────────────────────────────────────────────────
        if (!bio.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(Dimens.spaceMedium))
            Text(
                text = bio,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontStyle = FontStyle.Italic,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StatItem(
    count: Int,
    label: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(Dimens.spaceXSmall))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── About row (icon badge + title + subtitle, optional open action) ──────────
@Composable
private fun AboutRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    trailingIconRes: Int? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Dimens.spaceLarge, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.width(Dimens.spaceLarge))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (onClick != null) {
            if (trailingIconRes != null) {
                Icon(
                    imageVector = ImageVector.vectorResource(id = trailingIconRes),
                    contentDescription = null,
                    modifier = Modifier.size(Dimens.iconSizeMedium),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(Dimens.iconSizeMedium),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

/**
 * Strips scheme / www / trailing slash for a clean, readable handle line.
 * "https://www.instagram.com/foo/" → "instagram.com/foo".
 */
internal fun prettySocialHandle(raw: String): String {
    var s = raw.trim()
    s = s.removePrefix("https://").removePrefix("http://")
    s = s.removePrefix("www.")
    s = s.trimEnd('/')
    return s.ifBlank { raw.trim() }
}

/**
 * Opens a social link in the browser. Bare handles / domains are normalized
 * to an https URL so a value like "instagram.com/foo" or "foo" still opens.
 */
internal fun openSocialLink(context: android.content.Context, raw: String) {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return
    val url = when {
        trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
        else -> "https://$trimmed"
    }
    try {
        val intent = android.content.Intent(
            android.content.Intent.ACTION_VIEW,
            url.toUri()
        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (_: Exception) {
        // No browser / malformed URL — silently ignore.
    }
}
