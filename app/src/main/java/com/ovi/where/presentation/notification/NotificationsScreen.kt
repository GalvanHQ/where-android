package com.ovi.where.presentation.notification

import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.FlagCircle
import androidx.compose.material.icons.outlined.GroupRemove
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.ShareLocation
import androidx.compose.material.icons.rounded.WhereToVote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.ovi.where.DeepLinkManager
import com.ovi.where.R
import com.ovi.where.core.links.LinkText
import com.ovi.where.core.notification.NotificationType
import com.ovi.where.core.theme.Dimens
import com.ovi.where.presentation.common.WhereTopAppBar
import java.text.DateFormat
import java.util.Date

/**
 * In-app notification inbox.
 *
 * Curated to high-signal, action-required events:
 *  • Friend requests and accepted requests (with inline Accept / Decline)
 *  • Meetup destination set
 *  • Meetup member arrived
 *
 * Non-important events (chat, member join/leave, live-location start/stop,
 * location updates, meetup cleared, GENERAL) aren't persisted — they live
 * elsewhere in the product (Chats tab, the map). See
 * [NotificationType.isInboxImportant].
 *
 * Visual language is flat to match the rest of the app: no per-row cards
 * or filled containers, just padded rows on the scaffold surface — same
 * pattern as Friend Requests and Chats. Unread is a single primary dot
 * on the right; the row title weight does the rest of the lifting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onNavigateBack: () -> Unit,
    viewModel: NotificationsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val pendingNavigation by viewModel.pendingNavigation.collectAsState()

    // Forward tap-to-navigate via the existing deep-link plumbing so the
    // navigation logic stays in AppNavGraph (same path used by FCM taps).
    LaunchedEffect(pendingNavigation) {
        val route = pendingNavigation ?: return@LaunchedEffect
        DeepLinkManager.pending = route
        viewModel.onNavigationConsumed()
        onNavigateBack()
    }

    Scaffold(
        topBar = {
            WhereTopAppBar(
                title = stringResource(R.string.notifications_title),
                onNavigateBack = onNavigateBack,
                actions = {
                    if (uiState.items.any { !it.isRead }) {
                        TextButton(onClick = viewModel::onMarkAllRead) {
                            Text(stringResource(R.string.notifications_mark_all_read))
                        }
                    } else if (uiState.totalCount > 0) {
                        TextButton(onClick = viewModel::onClearAll) {
                            Text(stringResource(R.string.notifications_clear_all))
                        }
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            FilterRow(
                selected = uiState.filter,
                onSelect = viewModel::onFilterSelected,
            )

            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize())
                uiState.isFilteredEmpty -> EmptyState(
                    title = stringResource(R.string.notifications_empty_filter_title),
                    body = stringResource(R.string.notifications_empty_filter_body),
                    modifier = Modifier.fillMaxSize(),
                )
                uiState.isEmpty -> EmptyState(
                    title = stringResource(R.string.notifications_empty_title),
                    body = stringResource(R.string.notifications_empty_body),
                    modifier = Modifier.fillMaxSize(),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = Dimens.space2XLarge),
                ) {
                    uiState.sections.forEach { section ->
                        item(key = "h_${section.id}") { SectionHeader(section.id, section.items.size) }
                        items(section.items, key = { it.id }) { item ->
                            DismissibleNotificationRow(
                                item = item,
                                onClick = { viewModel.onNotificationClick(item) },
                                onDismiss = { viewModel.onDelete(item.id) },
                                onAccept = { viewModel.onAcceptFriendRequest(item) },
                                onDecline = { viewModel.onDeclineFriendRequest(item) },
                                modifier = Modifier.animateItem(
                                    fadeInSpec = tween(200),
                                    placementSpec = tween(200),
                                    fadeOutSpec = tween(150),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Filter pills ────────────────────────────────────────────────────────────
//
// Lightweight underline-style pills, not Material FilterChips. The default
// FilterChip has heavy borders and a chip outline that fights with our flat
// list rows. A simple text + thin underline reads cleaner and matches the
// tab feel the rest of the app uses.

@Composable
private fun FilterRow(
    selected: NotificationFilter,
    onSelect: (NotificationFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spaceLarge, vertical = Dimens.spaceMedium),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceLarge),
    ) {
        NotificationFilter.values().forEach { filter ->
            val label = when (filter) {
                NotificationFilter.ALL -> stringResource(R.string.notifications_filter_all)
                NotificationFilter.REQUESTS -> stringResource(R.string.notifications_filter_requests)
                NotificationFilter.MEETUPS -> stringResource(R.string.notifications_filter_meetups)
            }
            FilterPill(
                label = label,
                selected = selected == filter,
                onClick = { onSelect(filter) },
            )
        }
    }
}

@Composable
private fun FilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val container = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        Color.Transparent
    }
    val textColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.cornerRound))
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = textColor,
        )
    }
}

// ── Section header ──────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(id: SectionId, count: Int) {
    val label = when (id) {
        SectionId.TODAY -> stringResource(R.string.notifications_section_today)
        SectionId.THIS_WEEK -> stringResource(R.string.notifications_section_this_week)
        SectionId.EARLIER -> stringResource(R.string.notifications_section_earlier)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = Dimens.spaceLarge,
                end = Dimens.spaceLarge,
                top = Dimens.spaceLarge,
                bottom = Dimens.spaceSmall,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.width(Dimens.spaceMedium))
        Text(
            text = "$count",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Row + swipe wrapper ─────────────────────────────────────────────────────

/**
 * Swipe-to-dismiss wrapper. Forwards to [onDismiss] once a swipe settles
 * past either threshold. The reveal background is intentionally subtle —
 * a flat surface tint, no error red — to keep the screen calm.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DismissibleNotificationRow(
    item: NotificationUiModel,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            val triggered = value == SwipeToDismissBoxValue.StartToEnd ||
                value == SwipeToDismissBoxValue.EndToStart
            if (triggered) onDismiss()
            triggered
        }
    )
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .padding(horizontal = Dimens.spaceLarge),
                contentAlignment = if (state.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                    Alignment.CenterStart
                } else {
                    Alignment.CenterEnd
                },
            ) {
                Text(
                    text = stringResource(R.string.notifications_action_dismiss),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    ) {
        NotificationRow(
            item = item,
            onClick = onClick,
            onAccept = onAccept,
            onDecline = onDecline,
        )
    }
}

@Composable
private fun NotificationRow(
    item: NotificationUiModel,
    onClick: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    // Unread state is signalled by:
    //  • a 6dp primary dot on the right
    //  • SemiBold title (vs Normal when read)
    // No background tint — keeps the list flat and brand-clean.
    val titleWeight = if (item.isRead) FontWeight.Normal else FontWeight.SemiBold

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.spaceLarge, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            LeadingVisual(item)

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = titleWeight,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.body.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    LinkText(
                        text = item.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatRelative(item.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!item.isRead) {
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp, start = Dimens.spaceMedium)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }

        // Inline Accept / Decline for incoming friend requests. Same
        // sizing as Friend Requests screen so the two surfaces feel
        // identical when this row matches a request there.
        if (item.type == NotificationType.FRIEND_REQUEST && item.userId != null) {
            Spacer(modifier = Modifier.height(10.dp))
            // Indent under the avatar so the buttons line up with the text.
            Row(modifier = Modifier.padding(start = 52.dp)) {
                FriendRequestActions(
                    inFlight = item.actionState == RequestActionState.InFlight,
                    onAccept = onAccept,
                    onDecline = onDecline,
                )
            }
        }
    }
}

@Composable
private fun FriendRequestActions(
    inFlight: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMedium),
    ) {
        Button(
            onClick = onAccept,
            enabled = !inFlight,
            modifier = Modifier.weight(1f).height(34.dp),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(0.dp),
        ) {
            if (inFlight) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(
                    text = stringResource(R.string.notifications_action_accept),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        OutlinedButton(
            onClick = onDecline,
            enabled = !inFlight,
            modifier = Modifier.weight(1f).height(34.dp),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
        ) {
            Text(
                text = stringResource(R.string.notifications_action_decline),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

// ── Leading visual (avatar or icon bubble) ─────────────────────────────────

@Composable
private fun LeadingVisual(item: NotificationUiModel) {
    // Friend-related rows show the user's avatar when we can resolve it.
    // Other types use a colored icon bubble keyed by type.
    val showAvatar = (item.type == NotificationType.FRIEND_REQUEST ||
        item.type == NotificationType.FRIEND_ACCEPTED ||
        item.type == NotificationType.MEETUP_MEMBER_ARRIVED) &&
        !item.avatarUrl.isNullOrBlank()

    if (showAvatar) {
        AvatarBubble(photoUrl = item.avatarUrl)
    } else {
        TypeIconBubble(type = item.type)
    }
}

@Composable
private fun AvatarBubble(photoUrl: String?) {
    val context = LocalContext.current
    val request = remember(photoUrl) {
        photoUrl?.let {
            ImageRequest.Builder(context)
                .data(it)
                .crossfade(true)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build()
        }
    }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (request != null) {
            AsyncImage(
                model = request,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun TypeIconBubble(type: NotificationType) {
    // Each type maps to its own icon AND its own accent so rows scan
    // distinctly down the list. Friend-related rows lean primary; meetup
    // uses tertiary (matching the meetup pin / chip language used on the
    // map and chat screens); membership uses secondary; live-location
    // uses primary; and "stopped"-style states use the muted onSurface
    // tint so they read calmer than their "started" counterparts.
    val (icon, tint) = when (type) {
        NotificationType.FRIEND_REQUEST ->
            Icons.Rounded.PersonAdd to MaterialTheme.colorScheme.primary

        NotificationType.FRIEND_ACCEPTED ->
            Icons.Rounded.Handshake to MaterialTheme.colorScheme.primary

        NotificationType.MEMBER_JOINED ->
            Icons.Rounded.GroupAdd to MaterialTheme.colorScheme.secondary

        NotificationType.MEMBER_LEFT ->
            Icons.Outlined.GroupRemove to MaterialTheme.colorScheme.onSurfaceVariant

        NotificationType.LIVE_LOCATION_STARTED ->
            Icons.Rounded.ShareLocation to MaterialTheme.colorScheme.primary

        NotificationType.LIVE_LOCATION_STOPPED ->
            Icons.Outlined.LocationOff to MaterialTheme.colorScheme.onSurfaceVariant

        NotificationType.LOCATION_UPDATE ->
            Icons.Outlined.MyLocation to MaterialTheme.colorScheme.primary

        NotificationType.MEETUP_DESTINATION_SET ->
            Icons.Rounded.Flag to MaterialTheme.colorScheme.tertiary

        NotificationType.MEETUP_DESTINATION_CLEARED ->
            Icons.Outlined.FlagCircle to MaterialTheme.colorScheme.onSurfaceVariant

        NotificationType.MEETUP_MEMBER_ARRIVED ->
            Icons.Rounded.WhereToVote to MaterialTheme.colorScheme.tertiary

        NotificationType.NEW_MESSAGE ->
            Icons.AutoMirrored.Outlined.Chat to MaterialTheme.colorScheme.primary

        NotificationType.MENTION ->
            Icons.Outlined.AlternateEmail to MaterialTheme.colorScheme.primary

        NotificationType.GENERAL ->
            Icons.Outlined.NotificationsNone to MaterialTheme.colorScheme.onSurfaceVariant
    }
    IconBubbleInner(icon = icon, tint = tint)
}

@Composable
private fun IconBubbleInner(icon: ImageVector, tint: Color) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

// ── Empty state ─────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = Dimens.spaceXLarge),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.bell),
            contentDescription = null,
            modifier = Modifier.size(140.dp).alpha(0.9f),
            contentScale = ContentScale.Fit
        )
        Spacer(modifier = Modifier.height(Dimens.spaceLarge))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(Dimens.spaceMedium))
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f),
            textAlign = TextAlign.Center
        )
    }
}

// ── Time formatting ─────────────────────────────────────────────────────────

/**
 * Lightweight relative formatter. We keep it inline here rather than reuse
 * the chat formatter — the inbox cares about coarser buckets (just now, 5m,
 * 2h, Apr 12) where exact second-level precision isn't needed.
 */
private fun formatRelative(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val deltaMs = now - timestamp
    val deltaMin = deltaMs / 60_000
    val deltaHr = deltaMs / 3_600_000
    val deltaDay = deltaMs / 86_400_000
    return when {
        deltaMs < 60_000 -> "just now"
        deltaMin < 60 -> "${deltaMin}m ago"
        deltaHr < 24 -> "${deltaHr}h ago"
        deltaDay < 7 -> "${deltaDay}d ago"
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp))
    }
}
