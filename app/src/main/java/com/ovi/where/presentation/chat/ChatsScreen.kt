package com.ovi.where.presentation.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ovi.where.R
import com.ovi.where.core.constants.AppConstants.PULL_TO_REFRESH_TIMEOUT_MS
import com.ovi.where.core.theme.Dimens
import com.ovi.where.domain.model.MessageStatus
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
    onNavigateToCreateGroup: () -> Unit = {},
    onNavigateToJoinGroup: () -> Unit = {},
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

    // Expandable FAB menu state
    var isFabExpanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.padding(contentPadding),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExpandableFabMenu(
                isExpanded = isFabExpanded,
                onToggle = { isFabExpanded = !isFabExpanded },
                onNewChat = {
                    isFabExpanded = false
                    onNavigateToSearch()
                },
                onNewGroup = {
                    isFabExpanded = false
                    onNavigateToCreateGroup()
                }
            )
        }
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
               Surface(
                   modifier = Modifier.size(48.dp),
                   onClick = onNavigateToCreateGroup,
                   shape = RoundedCornerShape(50)
               ){
                   Icon(
                       imageVector = ImageVector.vectorResource(id = R.drawable.team_check_alt),
                       contentDescription = "Join group",
                       tint = MaterialTheme.colorScheme.onSurfaceVariant,
                       modifier = Modifier.padding(8.dp)
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
                            items(
                                items = uiState.conversations,
                                key = { it.id },
                                contentType = { "conversation_row" }
                            ) { conv ->
                                val isOnline = viewModel.isConversationOnline(conv)
                                ConversationRow(
                                    conversation = conv,
                                    isOnline = isOnline,
                                    isContextMenuVisible = uiState.contextMenuConversationId == conv.id,
                                    onClick = { onNavigateToChat(conv.id) },
                                    onLongClick = { viewModel.showConversationContextMenu(conv.id) },
                                    onDismissContextMenu = { viewModel.dismissConversationContextMenu() },
                                    onPin = { viewModel.togglePinConversation(conv.id) },
                                    onMute = { viewModel.toggleMuteConversation(conv.id) },
                                    onDelete = { viewModel.requestDeleteConversation(conv.id) },
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

// ── FAB Menu (Material 3) ────────────────────────────────────────────────────

@Composable
private fun ExpandableFabMenu(
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onNewChat: () -> Unit,
    onNewGroup: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // ── Animated menu items ──────────────────────────────────────────
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + slideInVertically { it / 2 } + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + slideOutVertically { it / 2 } + scaleOut(targetScale = 0.8f)
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(bottom = 4.dp, end = 4.dp)
            ) {
                FabMenuItem(
                    icon = Icons.Rounded.Group,
                    label = "New Group",
                    onClick = onNewGroup
                )
                FabMenuItem(
                    icon = Icons.Rounded.Edit,
                    label = "New Chat",
                    onClick = onNewChat
                )
            }
        }

        // ── Main FAB with transition animations ─────────────────────────
        val transition = updateTransition(targetState = isExpanded, label = "FAB Transition")

        val fabSize by transition.animateDp(label = "FAB Size") { expanded ->
            if (expanded) 48.dp else 56.dp
        }

        val fabShape by transition.animateDp(label = "FAB Shape") { expanded ->
            if (expanded) 28.dp else 16.dp
        }

        val containerColor by transition.animateColor(label = "FAB Color") { expanded ->
            if (expanded)
                MaterialTheme.colorScheme.secondary
            else
                MaterialTheme.colorScheme.primary
        }

        val iconRotation by transition.animateFloat(label = "Icon Rotation") { expanded ->
            if (expanded) 90f else 0f
        }

        val fabElevation by transition.animateDp(
            transitionSpec = {
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            },
            label = "FAB Elevation"
        ) { expanded ->
            if (expanded) 8.dp else 6.dp
        }

        FloatingActionButton(
            onClick = onToggle,
            containerColor = containerColor,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .size(fabSize)
                .shadow(fabElevation, MaterialTheme.shapes.large)
        ) {
            Icon(
                imageVector = if (isExpanded) Icons.Rounded.Close else Icons.Rounded.Edit,
                contentDescription = if (isExpanded) "Close menu" else "New conversation",
                tint = if (isExpanded)
                    MaterialTheme.colorScheme.onSecondary
                else
                    MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .size(24.dp)
                    .rotate(iconRotation)
            )
        }
    }
}

@Composable
private fun FabMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .shadow(4.dp, RoundedCornerShape(50)),
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primary
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 16.sp
                )
            )
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
internal fun ConversationRow(
    conversation: ConversationUiModel,
    isOnline: Boolean,
    isContextMenuVisible: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDismissContextMenu: () -> Unit,
    onPin: () -> Unit,
    onMute: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasUnread = conversation.unreadCount > 0 && !conversation.isMuted

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Leading: 56dp ConversationAvatar with online indicator
            ConversationAvatar(
                name = conversation.title,
                photoUrl = conversation.photoUrl,
                isOnline = isOnline && !conversation.isGroup,
                size = 56.dp,
                indicatorSize = 14.dp,
                indicatorBorderWidth = 2.dp
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Title line: Conversation_Title + trailing timestamp
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = conversation.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(Dimens.spaceMedium))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Pin icon for pinned conversations
                        if (conversation.isPinned) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = "Pinned",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        // Location pin icon when any member is sharing
                        if (conversation.hasActiveLocationSharing) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = "Location sharing active",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        // Mute icon for muted conversations
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

                // Preview line: message preview + trailing UnreadBadge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Message status indicator before preview for own last message
                        if (conversation.isLastMessageFromCurrentUser && conversation.lastMessageStatus != null) {
                            ConversationMessageStatusIcon(status = conversation.lastMessageStatus)
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            text = conversation.locationSharingPreview ?: conversation.lastMessageText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (conversation.locationSharingPreview != null)
                                MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (hasUnread) FontWeight.Medium else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    // Unread badge — hidden for muted conversations
                    if (conversation.unreadCount > 0 && !conversation.isMuted) {
                        Spacer(Modifier.width(Dimens.spaceMedium))
                        UnreadBadge(count = conversation.unreadCount)
                    }
                }
            }
        }

        // Long-press context menu
        ConversationContextMenu(
            expanded = isContextMenuVisible,
            isPinned = conversation.isPinned,
            isMuted = conversation.isMuted,
            onDismiss = onDismissContextMenu,
            onPin = onPin,
            onMute = onMute,
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
                    imageVector = if (isMuted) Icons.Outlined.Notifications else Icons.Default.NotificationsOff,
                    contentDescription = if (isMuted) "Unmute conversation" else "Mute conversation"
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

// ── End of ChatsScreen ───────────────────────────────────────────────────────────
