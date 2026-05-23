package com.ovi.where.presentation.people

import android.text.format.DateUtils
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RemoveCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.ovi.where.R
import com.ovi.where.core.theme.Dimens
import com.ovi.where.presentation.model.ProfileFriendshipAction
import com.ovi.where.presentation.people.components.ProfileErrorState
import com.ovi.where.presentation.people.components.ProfileNotFoundState
import com.ovi.where.presentation.people.components.ProfileSkeleton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    userId: String,
    onNavigateBack: () -> Unit = {},
    onNavigateToChat: (String) -> Unit = {},
    viewModel: UserProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val navigateToChat by viewModel.navigateToChat.collectAsState()
    var showBlockDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    LaunchedEffect(userId) { viewModel.loadUser(userId) }

    // Start location service when sharing is activated from this screen
    val context = LocalContext.current
    LaunchedEffect(uiState.locationSharingActive, uiState.locationSharingTargetId) {
        if (uiState.locationSharingActive && uiState.locationSharingTargetId != null) {
            val intent = com.ovi.where.service.LocationTrackingService.createStartIntent(
                context, 60L
            )
            context.startForegroundService(intent)
            android.widget.Toast.makeText(context, "Sharing location for 1 hour", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(navigateToChat) {
        navigateToChat?.let { conversationId ->
            onNavigateToChat(conversationId)
            viewModel.onChatNavigated()
        }
    }

    // Entrance animation
    val contentAlpha = remember { Animatable(0f) }
    LaunchedEffect(uiState.profile) {
        if (uiState.profile != null) {
            contentAlpha.animateTo(1f, animationSpec = tween(400, easing = FastOutSlowInEasing))
        }
    }

    // Ring animation
    val infiniteTransition = rememberInfiniteTransition(label = "ring")
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring_alpha"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    uiState.profile?.let {
                        Text(
                            "@${it.username}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (uiState.profile != null) {
                        Box {
                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(Icons.Default.MoreVert, "More")
                            }
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false },
                                shape = RoundedCornerShape(Dimens.cornerMedium),
                                offset = DpOffset((-16).dp, 0.dp)
                            ) {
                                val action = uiState.profile?.friendshipAction
                                if (action is ProfileFriendshipAction.AlreadyFriends) {
                                    DropdownMenuItem(
                                        text = { Text("Unfriend", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = { Icon(Icons.Rounded.PersonRemove, null, tint = MaterialTheme.colorScheme.error) },
                                        onClick = { viewModel.removeFriend(userId); showMoreMenu = false }
                                    )
                                }
                                if (action !is ProfileFriendshipAction.Blocked && action !is ProfileFriendshipAction.BlockedByThem) {
                                    DropdownMenuItem(
                                        text = { Text("Block", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = { Icon(Icons.Rounded.RemoveCircle, null, tint = MaterialTheme.colorScheme.error) },
                                        onClick = { showMoreMenu = false; showBlockDialog = true }
                                    )
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> ProfileSkeleton()
                uiState.error != null && uiState.profile == null -> {
                    ProfileErrorState(
                        message = uiState.error ?: "Something went wrong",
                        onRetry = { viewModel.loadUser(userId) }
                    )
                }
                uiState.notFound -> ProfileNotFoundState(onBack = onNavigateBack)
                uiState.profile != null -> {
                    val profile = uiState.profile!!

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(contentAlpha.value),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = Dimens.space3XLarge)
                    ) {
                        // ── Header: Avatar left + Stats right (Instagram-style) ──
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Dimens.spaceLarge, vertical = Dimens.spaceLarge),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Avatar with gradient ring
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
                                    val context = LocalContext.current
                                    val density = LocalDensity.current

                                    val profilePhotoPixelSize = remember(density) {
                                        with(density) { 80.dp.roundToPx() }
                                    }

                                    val profilePhotoRequest = remember(profile.photoUrl, profilePhotoPixelSize) {
                                        if (profile.photoUrl.isNullOrBlank()) {
                                            null
                                        } else {
                                            ImageRequest.Builder(context)
                                                .data(profile.photoUrl)
                                                .crossfade(true)
                                                .size(profilePhotoPixelSize)
                                                .memoryCachePolicy(CachePolicy.ENABLED)
                                                .diskCachePolicy(CachePolicy.ENABLED)
                                                .networkCachePolicy(CachePolicy.ENABLED)
                                                .memoryCacheKey(profile.photoUrl)
                                                .diskCacheKey(profile.photoUrl)
                                                .build()
                                        }
                                    }

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
                                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Outlined.Person, null,
                                                modifier = Modifier.size(Dimens.iconSizeLarge),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    // Online dot
                                    if (profile.isOnline) {
                                        Box(
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.tertiary)
                                                .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                                .align(Alignment.BottomEnd)
                                        )
                                    }
                                }

                                Spacer(Modifier.width(Dimens.spaceXLarge))

                                // Stats
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    StatItem(
                                        label = "Friends",
                                        icon = when (profile.friendshipAction) {
                                            is ProfileFriendshipAction.AlreadyFriends -> Icons.Rounded.CheckCircle
                                            else -> Icons.Rounded.Remove
                                        }
                                    )
                                    StatItem(
                                        label = "Status",
                                        value = if (profile.isOnline) "Online" else "Offline"
                                    )
                                    StatItem(
                                        label = "Joined",
                                        value = if (profile.createdAt > 0) formatShortDate(profile.createdAt) else "—"
                                    )
                                }
                            }
                        }

                        // ── Name + Username + Bio ────────────────────────────────
                        item {
                            Column(
                                modifier = Modifier.padding(horizontal = Dimens.spaceLarge)
                            ) {
                                Text(
                                    text = profile.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                if (profile.isOnline) {
                                    Text(
                                        text = "Active now",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                } else if (profile.lastSeen > 0) {
                                    Text(
                                        text = "Last seen ${formatRelativeTime(profile.lastSeen)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (profile.bio.isNotBlank()) {
                                    Spacer(Modifier.height(Dimens.spaceMedium))
                                    Text(
                                        text = profile.bio,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 4,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // ── Action Buttons ───────────────────────────────────────
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Dimens.spaceLarge, vertical = Dimens.spaceLarge),
                                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMedium)
                            ) {
                                when (profile.friendshipAction) {
                                    is ProfileFriendshipAction.AddFriend -> {
                                        Button(
                                            onClick = { viewModel.sendFriendRequest(userId) },
                                            modifier = Modifier.weight(1f).height(44.dp),
                                            shape = RoundedCornerShape(Dimens.cornerSmall)
                                        ) {
                                            Icon(Icons.Rounded.PersonAdd, null, Modifier.size(Dimens.iconSizeSmall))
                                            Spacer(Modifier.width(Dimens.spaceMedium))
                                            Text("Add Friend", style = MaterialTheme.typography.labelLarge)
                                        }
                                    }
                                    is ProfileFriendshipAction.RequestSent -> {
                                        OutlinedButton(
                                            onClick = { viewModel.cancelFriendRequest(userId) },
                                            modifier = Modifier.weight(1f).height(44.dp),
                                            shape = RoundedCornerShape(Dimens.cornerSmall)
                                        ) {
                                            Text("Cancel Request", style = MaterialTheme.typography.labelLarge)
                                        }
                                    }
                                    is ProfileFriendshipAction.RequestReceived -> {
                                        Button(
                                            onClick = { viewModel.acceptFriendRequest(userId) },
                                            modifier = Modifier.weight(1f).height(44.dp),
                                            shape = RoundedCornerShape(Dimens.cornerSmall)
                                        ) {
                                            Text("Accept", style = MaterialTheme.typography.labelLarge)
                                        }
                                        OutlinedButton(
                                            onClick = { viewModel.declineFriendRequest(userId) },
                                            modifier = Modifier.weight(1f).height(44.dp),
                                            shape = RoundedCornerShape(Dimens.cornerSmall)
                                        ) {
                                            Text("Decline", style = MaterialTheme.typography.labelLarge)
                                        }
                                    }
                                    is ProfileFriendshipAction.AlreadyFriends -> {
                                        Button(
                                            onClick = { viewModel.openOrCreateDm(userId) },
                                            modifier = Modifier.weight(1f).height(44.dp),
                                            shape = RoundedCornerShape(Dimens.cornerSmall)
                                        ) {
                                            Icon(imageVector = ImageVector.vectorResource(id = R.drawable.chat_filled), contentDescription = null, Modifier.size(Dimens.iconSizeSmall))
                                            Spacer(Modifier.width(Dimens.spaceMedium))
                                            Text("Message", style = MaterialTheme.typography.labelLarge)
                                        }
                                        OutlinedButton(
                                            onClick = { viewModel.startLocationSharingWithFriend(userId) },
                                            modifier = Modifier.weight(1f).height(44.dp),
                                            shape = RoundedCornerShape(Dimens.cornerSmall),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = MaterialTheme.colorScheme.onSurface
                                            )
                                        ) {
                                            Text("Share Location", style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        // Close-friend star toggle. Filled when active.
                                        // The whole notification system honors this:
                                        // close-friend pushes bypass quiet hours so urgent
                                        // contacts always come through.
                                        OutlinedButton(
                                            onClick = { viewModel.toggleCloseFriend(userId) },
                                            modifier = Modifier.height(44.dp),
                                            shape = RoundedCornerShape(Dimens.cornerSmall),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = if (uiState.isCloseFriend)
                                                    MaterialTheme.colorScheme.primary
                                                else
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        ) {
                                            Icon(
                                                imageVector = if (uiState.isCloseFriend)
                                                    Icons.Filled.Star
                                                else
                                                    Icons.Outlined.StarBorder,
                                                contentDescription = if (uiState.isCloseFriend)
                                                    "Remove from close friends"
                                                else
                                                    "Add to close friends",
                                                modifier = Modifier.size(Dimens.iconSizeSmall)
                                            )
                                        }
                                    }
                                    is ProfileFriendshipAction.Blocked -> {
                                        Button(
                                            onClick = { viewModel.unblockUser(userId) },
                                            modifier = Modifier.weight(1f).height(44.dp),
                                            shape = RoundedCornerShape(Dimens.cornerSmall),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        ) {
                                            Text("Unblock", style = MaterialTheme.typography.labelLarge)
                                        }
                                    }
                                    is ProfileFriendshipAction.BlockedByThem -> {
                                        // No actions available
                                    }
                                }
                            }
                        }

                        // ── Info Section ─────────────────────────────────────────
                        item {
                            Text(
                                text = "About",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(
                                    start = Dimens.spaceLarge,
                                    top = Dimens.spaceMedium,
                                    bottom = Dimens.spaceMedium
                                )
                            )
                        }

                        item {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Dimens.spaceLarge),
                                shape = RoundedCornerShape(Dimens.cornerMedium),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                tonalElevation = 0.dp
                            ) {
                                Column {
                                    InfoRow(
                                        icon = ImageVector.vectorResource(id = R.drawable.at_the_rate),
                                        title = "Username",
                                        subtitle = "@${profile.username}"
                                    )
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 56.dp),
                                        thickness = 0.5.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    )
                                    InfoRow(
                                        icon = ImageVector.vectorResource(id = R.drawable.location_outlined),
                                        title = "Location sharing",
                                        subtitle = if (profile.friendshipAction is ProfileFriendshipAction.AlreadyFriends)
                                            "Available" else "Not available"
                                    )
                                    if (profile.createdAt > 0) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(start = 56.dp),
                                            thickness = 0.5.dp,
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                        )
                                        InfoRow(
                                            icon = ImageVector.vectorResource(id = R.drawable.membership_outlined),
                                            title = "Member since",
                                            subtitle = formatJoinDate(profile.createdAt)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Block confirmation dialog
    if (showBlockDialog) {
        AlertDialog(
            onDismissRequest = { showBlockDialog = false },
            title = { Text("Block ${uiState.profile?.displayName ?: "user"}?") },
            text = { Text("They won't be able to find you or contact you. You can unblock them later.") },
            confirmButton = {
                TextButton(onClick = { viewModel.blockUser(userId); showBlockDialog = false }) {
                    Text("Block", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// ── Stat Item ────────────────────────────────────────────────────────────────
@Composable
private fun StatItem(
    label: String,
    value: String? = null,
    icon: ImageVector? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        when {
            icon != null -> {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            value != null -> {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(Dimens.spaceXSmall))

        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Info Row ─────────────────────────────────────────────────────────────────

@Composable
private fun InfoRow(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
                imageVector = icon, null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.width(Dimens.spaceLarge))
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
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun formatRelativeTime(timestamp: Long): String {
    if (timestamp == 0L) return ""
    return DateUtils.getRelativeTimeSpanString(
        timestamp, System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString()
}

private fun formatShortDate(timestamp: Long): String {
    if (timestamp == 0L) return "—"
    return SimpleDateFormat("MMM ''yy", Locale.getDefault()).format(Date(timestamp))
}

private fun formatJoinDate(timestamp: Long): String {
    if (timestamp == 0L) return "Unknown"
    return SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(timestamp))
}
