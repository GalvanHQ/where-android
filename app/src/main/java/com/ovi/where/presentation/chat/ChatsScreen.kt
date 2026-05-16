package com.ovi.where.presentation.chat

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.ovi.where.R
import com.ovi.where.core.constants.AppConstants.PULL_TO_REFRESH_TIMEOUT_MS
import com.ovi.where.core.theme.Dimens
import com.ovi.where.domain.model.MessageStatus
import com.ovi.where.presentation.common.LIST_ITEM_ANIMATION_DURATION_MS
import com.ovi.where.presentation.common.search.SearchBarTapTarget
import com.ovi.where.presentation.model.ConversationUiModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatsScreen(
    contentPadding: PaddingValues = PaddingValues(),
    onNavigateToChat: (String) -> Unit = {},
    onNavigateToCreateGroup: () -> Unit = {},
    onNavigateToJoinGroup: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    viewModel: ChatsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // ── Task 16.3: Wire foreground sync on app resume ─────────────────────────
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

    // Show pin limit error as snackbar (Req 24.4)
    LaunchedEffect(uiState.pinLimitError) {
        uiState.pinLimitError?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.dismissPinLimitError()
        }
    }

    // Pull-to-refresh state
    var isRefreshing by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.padding(contentPadding),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            // FAB for "Start Conversation" on Chats screen
            FloatingActionButton(
                onClick = onNavigateToSearch,
                containerColor = MaterialTheme.colorScheme.primary,
                shape = MaterialTheme.shapes.large
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Start Conversation",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
        ) {
            // ── Search bar tap target — navigates to full-screen search ─────
            SearchBarTapTarget(
                placeholderText = "Search chats...",
                onClick = onNavigateToSearch,
                modifier = Modifier.padding(horizontal = Dimens.spaceLarge)
            )

            Spacer(Modifier.height(Dimens.spaceSmall))

            // ── Conversation list with pull-to-refresh (Requirement 3.4) ─────
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    isRefreshing = true
                    viewModel.onRefresh()
                    scope.launch {
                        // 10s timeout for pull-to-refresh
                        delay(PULL_TO_REFRESH_TIMEOUT_MS)
                        isRefreshing = false
                    }
                },
                modifier = Modifier.fillMaxSize()
            ) {
                // Stop refresh indicator when data arrives
                if (isRefreshing && !uiState.isLoading) {
                    LaunchedEffect(Unit) { isRefreshing = false }
                }

                when {
                    uiState.isLoading && !isRefreshing -> {
                        com.ovi.where.presentation.common.ShimmerGroupList()
                    }
                    uiState.conversations.isEmpty() -> {
                        ChatsEmptyState()
                    }
                    else -> {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(
                                items = uiState.conversations,
                                key = { it.id },
                                contentType = { "conversation_row" }
                            ) { conv ->
                                val isOnline = viewModel.isConversationOnline(conv)
                                Column(modifier = Modifier.animateItem(
                                    fadeInSpec = tween(LIST_ITEM_ANIMATION_DURATION_MS),
                                    placementSpec = tween(LIST_ITEM_ANIMATION_DURATION_MS),
                                    fadeOutSpec = tween(LIST_ITEM_ANIMATION_DURATION_MS)
                                )) {
                                    ConversationRow(
                                        conversation = conv,
                                        isOnline = isOnline,
                                        isContextMenuVisible = uiState.contextMenuConversationId == conv.id,
                                        onClick = { onNavigateToChat(conv.id) },
                                        onLongClick = { viewModel.showConversationContextMenu(conv.id) },
                                        onDismissContextMenu = { viewModel.dismissConversationContextMenu() },
                                        onPin = { viewModel.togglePinConversation(conv.id) },
                                        onMute = { viewModel.toggleMuteConversation(conv.id) },
                                        onArchive = { viewModel.archiveConversation(conv.id) },
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
    }
}

// ── Empty State ─────────────────────────────────────────────────────────────────

@Composable
private fun ChatsEmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(Dimens.spaceXLarge)
        ) {
            Image(
                painter = painterResource(id = R.drawable.dialogue),
                contentDescription = null,
                modifier = Modifier.size(140.dp).alpha(0.9f),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.height(Dimens.spaceLarge))
            Text(
                "No chats yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(Dimens.spaceMedium))
            Text(
                "Start a conversation with friends",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f)
            )
        }
    }
}

// ── Conversation Row with Long-Press Context Menu ───────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    conversation: ConversationUiModel,
    isOnline: Boolean,
    isContextMenuVisible: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDismissContextMenu: () -> Unit,
    onPin: () -> Unit,
    onMute: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit
) {
    val hasUnread = conversation.unreadCount > 0 && !conversation.isMuted
    val backgroundColor by animateColorAsState(
        targetValue = if (hasUnread)
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        else
            MaterialTheme.colorScheme.surface,
        label = "unread_bg"
    )

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundColor)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Pin icon for pinned conversations (Req 24.4)
                        if (conversation.isPinned) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = "Pinned",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        // Location pin icon when any member is sharing (Req 3.1)
                        if (conversation.hasActiveLocationSharing) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = "Location sharing active",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        // Mute icon for muted conversations (Req 24.5)
                        if (conversation.isMuted) {
                            Icon(
                                imageVector = Icons.Default.NotificationsOff,
                                contentDescription = "Muted",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            text = conversation.lastMessageTime,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (hasUnread) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(Dimens.spaceXSmall))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Message status indicator before preview for own last message (Req 24.2)
                        if (conversation.isLastMessageFromCurrentUser && conversation.lastMessageStatus != null) {
                            ConversationMessageStatusIcon(status = conversation.lastMessageStatus)
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            text = conversation.locationSharingPreview ?: conversation.lastMessageText,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (conversation.locationSharingPreview != null)
                                MaterialTheme.colorScheme.tertiary
                            else if (hasUnread) MaterialTheme.colorScheme.onBackground
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (hasUnread) FontWeight.Medium else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    // Unread badge — hidden for muted conversations (Req 24.5)
                    if (conversation.unreadCount > 0 && !conversation.isMuted) {
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

        // Long-press context menu (Req 24.3)
        ConversationContextMenu(
            expanded = isContextMenuVisible,
            isPinned = conversation.isPinned,
            isMuted = conversation.isMuted,
            onDismiss = onDismissContextMenu,
            onPin = onPin,
            onMute = onMute,
            onArchive = onArchive,
            onDelete = onDelete
        )
    }
}

// ── Message Status Icon for Conversation List (Req 24.2) ────────────────────────

@Composable
private fun ConversationMessageStatusIcon(status: MessageStatus) {
    when (status) {
        MessageStatus.SENT -> {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Message sent",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        MessageStatus.DELIVERED -> {
            Icon(
                imageVector = Icons.Filled.DoneAll,
                contentDescription = "Message delivered",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        MessageStatus.READ -> {
            Icon(
                imageVector = Icons.Filled.DoneAll,
                contentDescription = "Message read",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        MessageStatus.FAILED -> {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = "Message failed",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.error
            )
        }
        MessageStatus.PENDING -> {
            Icon(
                imageVector = Icons.Filled.Schedule,
                contentDescription = "Message pending",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Long-Press Context Menu (Req 24.3) ──────────────────────────────────────────

@Composable
private fun ConversationContextMenu(
    expanded: Boolean,
    isPinned: Boolean,
    isMuted: Boolean,
    onDismiss: () -> Unit,
    onPin: () -> Unit,
    onMute: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        // Pin/Unpin action
        DropdownMenuItem(
            text = { Text(if (isPinned) "Unpin" else "Pin") },
            onClick = {
                onDismiss()
                onPin()
            },
            leadingIcon = {
                Icon(
                    imageVector = if (isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                    contentDescription = if (isPinned) "Unpin conversation" else "Pin conversation"
                )
            }
        )
        // Mute/Unmute action
        DropdownMenuItem(
            text = { Text(if (isMuted) "Unmute" else "Mute") },
            onClick = {
                onDismiss()
                onMute()
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.NotificationsOff,
                    contentDescription = if (isMuted) "Unmute conversation" else "Mute conversation"
                )
            }
        )
        // Archive action
        DropdownMenuItem(
            text = { Text("Archive") },
            onClick = {
                onDismiss()
                onArchive()
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Archive,
                    contentDescription = "Archive conversation"
                )
            }
        )
        // Delete action
        DropdownMenuItem(
            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
            onClick = {
                onDismiss()
                onDelete()
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete conversation",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        )
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
