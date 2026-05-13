package com.ovi.where.presentation.chat

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.ovi.where.core.theme.Dimens
import com.ovi.where.presentation.common.PrimaryButton
import com.ovi.where.presentation.common.WhereTabHeader
import com.ovi.where.presentation.common.WhereTextField
import com.ovi.where.presentation.model.ConversationUiModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(
    contentPadding: PaddingValues = PaddingValues(),
    onNavigateToChat: (String) -> Unit = {},
    onNavigateToSearchPeople: () -> Unit = {},
    onNavigateToCreateGroup: () -> Unit = {},
    onNavigateToJoinGroup: () -> Unit = {},
    viewModel: ChatsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSearchField by remember { mutableStateOf(false) }

    // ── Task 16.3: Wire foreground sync on app resume ─────────────────────────
    // Requirement 12.5: Trigger foreground sync on app resume
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onForegroundSync()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToSearchPeople,
                modifier = Modifier.padding(bottom = 88.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                shape = MaterialTheme.shapes.large
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "New message",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(contentPadding)
        ) {
            // ── Header ──────────────────────────────────────────────────────
            WhereTabHeader(title = "Chats") {
                IconButton(onClick = { showSearchField = !showSearchField }) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // ── Search field ────────────────────────────────────────────────
            if (showSearchField) {
                WhereTextField(
                    value = uiState.searchQuery,
                    onValueChange = viewModel::onSearchQueryChanged,
                    label = "Search chats",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.spaceLarge, vertical = Dimens.spaceMedium)
                )
            }

            Spacer(Modifier.height(Dimens.spaceSmall))

            // ── Conversation list ────────────────────────────────────────────
            when {
                uiState.isLoading -> {
                    com.ovi.where.presentation.common.ShimmerGroupList()
                }
                uiState.conversations.isEmpty() -> {
                    ChatsEmptyState(onNavigateToSearchPeople = onNavigateToSearchPeople)
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(
                            items = uiState.conversations,
                            key = { it.id }
                        ) { conv ->
                            val isOnline = viewModel.isConversationOnline(conv)
                            SwipeableConversationRow(
                                conversation = conv,
                                isOnline = isOnline,
                                onClick = { onNavigateToChat(conv.id) },
                                onArchive = { /* archive action placeholder */ },
                                onMute = { /* mute action placeholder */ },
                                onDelete = { viewModel.deleteConversation(conv.id) }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 80.dp),
                                thickness = Dimens.dividerThickness,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Empty State ─────────────────────────────────────────────────────────────────

@Composable
private fun ChatsEmptyState(onNavigateToSearchPeople: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(Dimens.spaceXLarge)
        ) {
            Icon(
                Icons.Default.Forum, null,
                modifier = Modifier.size(Dimens.iconSizeXXLarge),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f)
            )
            Spacer(Modifier.height(Dimens.spaceLarge))
            Text(
                "No chats yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(Dimens.spaceMedium))
            Text(
                "Find friends and start a conversation",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f)
            )
            Spacer(Modifier.height(Dimens.spaceXLarge))
            PrimaryButton(
                text = "Find People",
                onClick = onNavigateToSearchPeople,
                modifier = Modifier.fillMaxWidth(0.6f)
            )
        }
    }
}

// ── Swipeable Conversation Row ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableConversationRow(
    conversation: ConversationUiModel,
    isOnline: Boolean,
    onClick: () -> Unit,
    onArchive: () -> Unit,
    onMute: () -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> {
                    // Reveal actions — don't actually dismiss
                    false
                }
                else -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = Dimens.spaceLarge),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onArchive) {
                    Icon(
                        Icons.Default.Archive,
                        contentDescription = "Archive",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(Dimens.spaceMedium))
                IconButton(onClick = onMute) {
                    Icon(
                        Icons.Default.NotificationsOff,
                        contentDescription = "Mute",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(Dimens.spaceMedium))
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true
    ) {
        ConversationRow(
            conversation = conversation,
            isOnline = isOnline,
            onClick = onClick
        )
    }
}

// ── Conversation Row ────────────────────────────────────────────────────────────

@Composable
private fun ConversationRow(
    conversation: ConversationUiModel,
    isOnline: Boolean,
    onClick: () -> Unit
) {
    val hasUnread = conversation.unreadCount > 0
    val backgroundColor by animateColorAsState(
        targetValue = if (hasUnread)
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        else
            MaterialTheme.colorScheme.surface,
        label = "unread_bg"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.spaceLarge, vertical = Dimens.spaceMedium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ConversationAvatar(
            conversation = conversation,
            isOnline = isOnline
        )

        Spacer(Modifier.width(Dimens.spaceLarge))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = conversation.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(Dimens.spaceMedium))
                Text(
                    text = conversation.lastMessageTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (hasUnread) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(Dimens.spaceXSmall))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = conversation.lastMessageText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hasUnread) MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (hasUnread) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (hasUnread) {
                    Spacer(Modifier.width(Dimens.spaceMedium))
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Text(
                            text = if (conversation.unreadCount > 99) "99+"
                            else "${conversation.unreadCount}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

// ── Conversation Avatar with Online Dot ─────────────────────────────────────────

@Composable
private fun ConversationAvatar(
    conversation: ConversationUiModel,
    isOnline: Boolean
) {
    val onlineDescription = if (isOnline && !conversation.isGroup) {
        "${conversation.title} is online"
    } else {
        null
    }

    Box(
        modifier = Modifier
            .size(Dimens.avatarSizeMedium)
            .then(
                if (onlineDescription != null) {
                    Modifier.semantics { contentDescription = onlineDescription }
                } else Modifier
            )
    ) {
        if (!conversation.photoUrl.isNullOrEmpty()) {
            AsyncImage(
                model = conversation.photoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(Dimens.avatarSizeMedium)
                    .clip(CircleShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(Dimens.avatarSizeMedium)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = conversation.title.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Online status dot for direct conversations
        if (isOnline && !conversation.isGroup) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary)
                    .align(Alignment.BottomEnd)
                    .offset(x = (-2).dp, y = (-2).dp)
            )
        }
    }
}
