package com.ovi.where.presentation.notification

import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.WhereToVote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.ovi.where.R
import com.ovi.where.core.notification.NotificationType
import com.ovi.where.core.theme.Dimens
import java.text.DateFormat
import java.util.Date

/**
 * In-app notification inbox — Facebook model.
 *
 * Persists only friend-shaped events (request received / accepted). Every
 * other notification type is delivered via the system tray and lives on
 * its native surface (Chats tab, the map, the meetup card) — they're not
 * mirrored here. Saves a Firestore inbox write per recipient per non-
 * friend event.
 *
 * Rows are NOT clickable. The only interactions are:
 *  • Inline Accept / Decline on a FRIEND_REQUEST row.
 *  • Swipe-to-dismiss on any row.
 *
 * Read state: a single [NotificationsViewModel.onScreenOpened] call on
 * first composition flips every unread row read in one batched Firestore
 * write. No per-row markAsRead writes anywhere — opening the screen is
 * the read-receipt.
 *
 * Visual language is flat: no per-row cards, no filled containers, just
 * padded rows on the surface. Matches Friend Requests / Chats.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: NotificationsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Single batched markAllAsRead per screen open. The dotted-path update
    // is one Firestore write regardless of how many rows are unread.
    LaunchedEffect(Unit) { viewModel.onScreenOpened() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(contentPadding)
            .statusBarsPadding()
    ) {
        // ── Header — a bold title anchors the row (the other tabs use a
        //    search bar for this weight; Notifications has none, so a lone
        //    action floated unbalanced). Title left, "Clear all" right.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = Dimens.spaceLarge,
                    end = Dimens.spaceMedium,
                    top = Dimens.spaceMedium,
                    bottom = Dimens.spaceSmall
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.notifications_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            if (uiState.items.isNotEmpty()) {
                TextButton(onClick = viewModel::onClearAll) {
                    Text(
                        stringResource(R.string.notifications_clear_all),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize())
            uiState.isEmpty -> EmptyState(
                title = stringResource(R.string.notifications_empty_title),
                body = stringResource(R.string.notifications_empty_body),
                modifier = Modifier.fillMaxSize(),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    bottom = Dimens.space2XLarge + contentPadding.calculateBottomPadding()
                ),
            ) {
                uiState.sections.forEach { section ->
                    item(key = "h_${section.id}") { SectionHeader(section.id, section.items.size) }
                    items(section.items, key = { it.id }) { item ->
                        DismissibleNotificationRow(
                            item = item,
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
 * Swipe-to-dismiss wrapper. Rows are non-clickable — the only gesture is
 * swipe to dismiss. Inline Accept / Decline live inside the row itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DismissibleNotificationRow(
    item: NotificationUiModel,
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
            onAccept = onAccept,
            onDecline = onDecline,
        )
    }
}

@Composable
private fun NotificationRow(
    item: NotificationUiModel,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    // Unread state is signalled by:
    //  • a 6dp primary dot on the right
    //  • SemiBold title (vs Normal when read)
    val titleWeight = if (item.isRead) FontWeight.Normal else FontWeight.SemiBold

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
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
                    lineHeight = 20.sp,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.body.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
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

        // Inline Accept / Decline for incoming friend requests. Only
        // surface for the *receiver* of a request, not for the
        // accepted-confirmation row (which is informational only).
        if (item.type == NotificationType.FRIEND_REQUEST && item.userId != null) {
            Spacer(modifier = Modifier.height(10.dp))
            // Indent under the avatar so the buttons line up with the title.
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
    if (!item.avatarUrl.isNullOrBlank()) {
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
    // Only friend types and meetup-arrival are persisted; everything
    // else is filtered out upstream. Keep the fallback for legacy entries
    // that may still be mid-purge in the canonical doc.
    val (icon, tint) = when (type) {
        NotificationType.FRIEND_REQUEST ->
            Icons.Rounded.PersonAdd to MaterialTheme.colorScheme.primary

        NotificationType.FRIEND_ACCEPTED ->
            Icons.Rounded.Handshake to MaterialTheme.colorScheme.primary

        NotificationType.MEETUP_MEMBER_ARRIVED ->
            Icons.Rounded.WhereToVote to MaterialTheme.colorScheme.tertiary

        else ->
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
        Spacer(Modifier.height(Dimens.spaceLarge))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(Dimens.spaceSmall))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// ── Time formatting ─────────────────────────────────────────────────────────

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
