package com.ovi.where.presentation.chat

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ovi.where.R
import com.ovi.where.core.constants.AppConstants.PULL_TO_REFRESH_TIMEOUT_MS
import com.ovi.where.core.theme.Dimens
import com.ovi.where.presentation.chat.components.ConversationAvatar
import com.ovi.where.presentation.chat.components.UnreadBadge
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
    onNavigateToNewMessage: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    viewModel: ChatsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // ── Task 16.3: Wire foreground sync on app resume ─────────────────────────
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
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

    // Show action feedback snackbar
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissSnackbar()
        }
    }

    // Pull-to-refresh state
    var isRefreshing by remember { mutableStateOf(false) }

    // Bottom sheet state
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selectedConversation = uiState.conversations.find { it.id == uiState.contextMenuConversationId }

    Scaffold(
        modifier = Modifier.padding(contentPadding),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
        ) {
           Row(
               modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.spaceLarge),
               horizontalArrangement = Arrangement.spacedBy(8.dp),
               verticalAlignment = Alignment.CenterVertically
           ){
               // ── Search bar tap target — navigates to full-screen search ─────
               SearchBarTapTarget(
                   placeholderText = "Search chats...",
                   onClick = onNavigateToSearch,
                   modifier = Modifier.weight(1f)
               )
               IconButton(
                   onClick = onNavigateToNewMessage
               ){
                   Icon(
                       imageVector = ImageVector.vectorResource(id = R.drawable.square_pen),
                       contentDescription = "New message",
                       tint = MaterialTheme.colorScheme.onSurfaceVariant,
                       modifier = Modifier.size(28.dp)
                   )
               }
           }

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
                            item{
                                Spacer(Modifier.height(Dimens.spaceSmall))
                            }
                            items(
                                items = uiState.conversations,
                                key = { it.id },
                                contentType = { "conversation_row" }
                            ) { conv ->
                                val isOnline = viewModel.isConversationOnline(conv)
                                ConversationRow(
                                    conversation = conv,
                                    isOnline = isOnline,
                                    onClick = { onNavigateToChat(conv.id) },
                                    onLongClick = { viewModel.showConversationContextMenu(conv.id) },
                                    modifier = Modifier.animateItem(
                                        fadeInSpec = tween(LIST_ITEM_ANIMATION_DURATION_MS),
                                        placementSpec = tween(
                                            durationMillis = LIST_ITEM_ANIMATION_DURATION_MS,
                                            easing = EaseInOut
                                        ),
                                        fadeOutSpec = tween(LIST_ITEM_ANIMATION_DURATION_MS)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Conversation Actions Bottom Sheet ────────────────────────────────
    if (selectedConversation != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissConversationContextMenu() },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            ConversationActionsSheetContent(
                isPinned = selectedConversation.isPinned,
                isMuted = selectedConversation.isMuted,
                onPin = {
                    viewModel.dismissConversationContextMenu()
                    viewModel.togglePinConversation(selectedConversation.id)
                },
                onMute = {
                    viewModel.dismissConversationContextMenu()
                    viewModel.requestMuteConversation(selectedConversation.id)
                },
                onDelete = {
                    viewModel.dismissConversationContextMenu()
                    viewModel.requestDeleteConversation(selectedConversation.id)
                }
            )
        }
    }

    // ── Mute flow ────────────────────────────────────────────────────────
    // If the conversation is already muted: show a quick "Unmute" confirm.
    // If it's not muted yet: show the duration picker so the user can pick
    // how long they want silence for. Mentions still bypass mute regardless.
    if (uiState.confirmMuteConversationId != null) {
        val muteConv = uiState.conversations.find { it.id == uiState.confirmMuteConversationId }
        val isMuted = muteConv?.isMuted == true
        if (isMuted) {
            AlertDialog(
                onDismissRequest = { viewModel.cancelMuteConversation() },
                title = {
                    Text(
                        text = "Unmute conversation?",
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                text = {
                    Text(
                        text = "You will start receiving notifications from this conversation again.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.confirmMuteConversation() }) {
                        Text("Unmute")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.cancelMuteConversation() }) {
                        Text("Cancel")
                    }
                }
            )
        } else {
            com.ovi.where.presentation.chat.components.MuteDurationSheet(
                onDismiss = { viewModel.cancelMuteConversation() },
                onSelect = { option -> viewModel.muteConversationFor(option) }
            )
        }
    }

    // ── Delete Confirmation Dialog ───────────────────────────────────────
    if (uiState.confirmDeleteConversationId != null) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelDeleteConversation() },
            title = {
                Text(
                    text = "Delete conversation?",
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = {
                Text(
                    text = "This conversation will be removed from your chat list. This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmDeleteConversation() }
                ) {
                    Text(
                        text = "Delete",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.cancelDeleteConversation() }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ── Bottom Sheet Content ─────────────────────────────────────────────────────────

@Composable
private fun ConversationActionsSheetContent(
    isPinned: Boolean,
    isMuted: Boolean,
    onPin: () -> Unit,
    onMute: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = Dimens.spaceLarge)
    ) {
        // Pin/Unpin
        SheetActionRow(
            icon = if (isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
            label = if (isPinned) "Unpin" else "Pin",
            onClick = onPin
        )
        // Mute/Unmute
        SheetActionRow(
            icon = if (isMuted) Icons.Outlined.Notifications else Icons.Default.NotificationsOff,
            label = if (isMuted) "Unmute" else "Mute",
            onClick = onMute
        )
        // Delete
        SheetActionRow(
            icon = Icons.Default.Delete,
            label = "Delete",
            tint = MaterialTheme.colorScheme.error,
            onClick = onDelete
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SheetActionRow(
    icon: ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick)
            .padding(horizontal = Dimens.spaceLarge, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = tint
        )
        Spacer(Modifier.width(Dimens.spaceLarge))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = tint
        )
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

// ── Conversation Row ──────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ConversationRow(
    conversation: ConversationUiModel,
    isOnline: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasUnread = conversation.unreadCount > 0 && !conversation.isMuted

    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Leading: 56dp ConversationAvatar with online indicator
        ConversationAvatar(
            name = conversation.title,
            photoUrl = conversation.photoUrl,
            isOnline = isOnline && !conversation.isGroup,
            size = 50.dp,
            indicatorSize = 14.dp,
            indicatorBorderWidth = 2.dp
        )

        Spacer(Modifier.width(12.dp))

        // Middle: title + preview (takes remaining space)
        Column(modifier = Modifier.weight(1f)) {
            // Title line: Name + optional mute/pin icons
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = conversation.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (conversation.isMuted) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.NotificationsOff,
                        contentDescription = "Muted",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
                if (conversation.isPinned) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "Pinned",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(Modifier.height(2.dp))

            // Preview line: "You: message" or just message text
            Row(verticalAlignment = Alignment.CenterVertically) {
                val previewText = buildString {
                    if (conversation.locationSharingPreview != null) {
                        append(conversation.locationSharingPreview)
                    } else {
                        // Messenger-style: prefix "You: " for own messages
                        if (conversation.isLastMessageFromCurrentUser) {
                            append("You: ")
                        }
                        append(conversation.lastMessageText)
                    }
                }

                Text(
                    text = previewText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        conversation.locationSharingPreview != null -> MaterialTheme.colorScheme.tertiary
                        hasUnread -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (hasUnread) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                // Timestamp: " · 14:32"
                Text(
                    text = " · ${conversation.lastMessageTime}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hasUnread) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (hasUnread) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }

        // Trailing: unread badge — numbered pill for ≥1 unread, capped at "99+"
        if (hasUnread) {
            Spacer(Modifier.width(8.dp))
            UnreadBadge(count = conversation.unreadCount)
        }
    }
}

// ── End of ChatsScreen ───────────────────────────────────────────────────────────
