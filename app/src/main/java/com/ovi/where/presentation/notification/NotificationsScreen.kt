package com.ovi.where.presentation.notification

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ovi.where.DeepLinkManager
import com.ovi.where.R
import com.ovi.where.core.notification.NotificationType
import com.ovi.where.core.theme.Dimens
import com.ovi.where.presentation.common.WhereTopAppBar
import java.text.DateFormat
import java.util.Date

/**
 * In-app notification inbox.
 *
 * Mirrors what the OS notification shade shows but persists past dismissal,
 * so users can browse a 30-day history of friend, chat, location, and
 * meetup events. Tapping an item routes through the same deep-link
 * pipeline that handles system-tray taps.
 *
 * Empty state: when no events have been received yet, the screen explains
 * which categories appear here so users don't think the app is broken.
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
                    } else if (uiState.items.isNotEmpty()) {
                        TextButton(onClick = viewModel::onClearAll) {
                            Text(stringResource(R.string.notifications_clear_all))
                        }
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (uiState.isEmpty) {
            EmptyState(modifier = Modifier.padding(padding).fillMaxSize())
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            items(uiState.items, key = { it.id }) { item ->
                NotificationRow(
                    item = item,
                    onClick = { viewModel.onNotificationClick(item) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
        }
    }
}

@Composable
private fun NotificationRow(
    item: NotificationUiModel,
    onClick: () -> Unit
) {
    val containerColor = if (item.isRead) {
        MaterialTheme.colorScheme.background
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.spaceLarge, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TypeIconBubble(type = item.type)

        Spacer(modifier = Modifier.width(Dimens.spaceLarge))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (item.isRead) FontWeight.Normal else FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.body.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = formatRelative(item.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(visible = !item.isRead) {
            Box(
                modifier = Modifier
                    .padding(start = Dimens.spaceMedium)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
private fun TypeIconBubble(type: NotificationType) {
    val (icon, tint) = when (type) {
        NotificationType.NEW_MESSAGE,
        NotificationType.MENTION -> Icons.Outlined.Chat to MaterialTheme.colorScheme.primary

        NotificationType.FRIEND_REQUEST,
        NotificationType.FRIEND_ACCEPTED -> Icons.Outlined.PersonAdd to MaterialTheme.colorScheme.tertiary

        NotificationType.MEMBER_JOINED,
        NotificationType.MEMBER_LEFT -> Icons.Outlined.Group to MaterialTheme.colorScheme.secondary

        NotificationType.LOCATION_UPDATE,
        NotificationType.LIVE_LOCATION_STARTED,
        NotificationType.LIVE_LOCATION_STOPPED -> Icons.Outlined.LocationOn to MaterialTheme.colorScheme.primary

        NotificationType.MEETUP_DESTINATION_SET,
        NotificationType.MEETUP_DESTINATION_CLEARED,
        NotificationType.MEETUP_MEMBER_ARRIVED -> Icons.Outlined.Place to MaterialTheme.colorScheme.tertiary

        NotificationType.GENERAL -> Icons.Outlined.Notifications to MaterialTheme.colorScheme.onSurfaceVariant
    }
    IconBubbleInner(icon = icon, tint = tint)
}

@Composable
private fun IconBubbleInner(icon: ImageVector, tint: Color) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.14f)),
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

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = Dimens.spaceXLarge),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Outlined.Notifications,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(Dimens.spaceLarge))
        Text(
            text = stringResource(R.string.notifications_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(Dimens.spaceSmall))
        Text(
            text = stringResource(R.string.notifications_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

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
