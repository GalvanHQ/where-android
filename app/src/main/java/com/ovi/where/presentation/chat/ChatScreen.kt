package com.ovi.where.presentation.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.ovi.where.core.theme.Dimens
import com.ovi.where.presentation.model.BubbleDirection
import com.ovi.where.presentation.model.MessageUiModel
import com.ovi.where.core.utils.showToast
import kotlinx.coroutines.flow.distinctUntilChanged
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ChatScreen — Individual conversation view with DESIGN.md-compliant bubble styling.
 *
 * Task 11.1: Message list with bubble styling per DESIGN.md
 * - Sent bubbles: Accent Primary (MaterialTheme.colorScheme.primary), Radius Large (16dp) all corners
 *   except bottom-right which uses Radius XS (4dp) for the tail effect.
 * - Received bubbles: Background Elevated (surfaceContainerHigh), Radius Large (16dp) all corners
 *   except bottom-left which uses Radius XS (4dp) for the mirrored tail.
 * - Max bubble width: 75% of screen width.
 * - LazyColumn items keyed by message ID.
 * - Scroll position maintained on prepend (pagination).
 * - Loading indicator at top during pagination.
 * - Inline retry button on pagination failure.
 *
 * Requirements: 16.1, 2.5, 2.3, 2.7, 14.1
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: String,
    onNavigateBack: () -> Unit = {},
    onNavigateToUserProfile: (String) -> Unit = {},
    onNavigateToGroupInfo: (String) -> Unit = {},
    onNavigateToGroupMap: (String) -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val currentUserId = viewModel.currentUserId
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val context = LocalContext.current

    // Location permission launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            viewModel.requestCurrentLocationAndSend()
        } else {
            context.showToast("Location permission denied")
        }
    }

    LaunchedEffect(conversationId) {
        viewModel.init(conversationId)
    }

    // ── Task 16.2: Wire ChatSocketIoClient lifecycle to ChatScreen ─────────────
    // Connect on foreground, disconnect on background (Requirements 13.1, 13.4, 13.5)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, conversationId) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    // Reconnect when app returns to foreground
                    viewModel.onForeground()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    // Disconnect when app goes to background
                    viewModel.onBackground()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Mark read when screen is shown
    LaunchedEffect(uiState.messages.size) {
        viewModel.markRead()
    }

    // Auto-scroll to bottom when new messages arrive (only if already near bottom)
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            // Auto-scroll only if user is near the bottom (within 5 items)
            if (totalItems - lastVisibleIndex <= 5) {
                listState.animateScrollToItem(uiState.messages.size - 1)
            }
        }
    }

    // Pagination trigger: load older messages when scrolled within 5 items of top
    // Requirement 2.2: Trigger when user scrolls within 5 items of top
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex
        }.distinctUntilChanged().collect { firstVisibleIndex ->
            if (firstVisibleIndex <= 5 && !uiState.isLoadingMore && uiState.hasMoreMessages && !uiState.isLoading) {
                viewModel.loadOlderMessages()
            }
        }
    }

    Scaffold(
        topBar = {
            ChatHeader(
                conversation = uiState.conversation,
                isOtherUserFriend = uiState.isOtherUserFriend,
                onNavigateBack = onNavigateBack,
                onNavigateToUserProfile = onNavigateToUserProfile,
                onNavigateToGroupInfo = onNavigateToGroupInfo,
                onNavigateToGroupMap = onNavigateToGroupMap
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .navigationBarsPadding()
        ) {
            // ── Message list ──────────────────────────────────────────────────
            Box(modifier = Modifier.weight(1f)) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else {
                    // Requirement 2.5: Maintain scroll position on prepend (pagination)
                    // LazyColumn with reverseLayout=false; items keyed by message ID ensures
                    // Compose maintains scroll position when items are prepended.
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            horizontal = Dimens.spaceLarge,
                            vertical = Dimens.spaceMedium
                        ),
                        verticalArrangement = Arrangement.spacedBy(Dimens.spaceSmall)
                    ) {
                        // ── Pagination loading indicator at top (Requirement 2.3) ──
                        if (uiState.isLoadingMore) {
                            item(key = "__pagination_loading__") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = Dimens.spaceMedium),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                        }

                        // ── Pagination error with inline retry (Requirement 2.7) ──
                        if (uiState.paginationError) {
                            item(key = "__pagination_error__") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = Dimens.spaceSmall),
                                    contentAlignment = Alignment.Center
                                ) {
                                    TextButton(onClick = { viewModel.loadOlderMessages() }) {
                                        Icon(
                                            Icons.Default.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(Dimens.spaceSmall))
                                        Text(
                                            text = "Failed to load. Tap to retry",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                            }
                        }

                        // ── Messages grouped by date ──────────────────────────────
                        // Group messages by pre-computed dateKey from MessageUiModel
                        val grouped = uiState.messages.groupBy { it.dateKey }
                        grouped.forEach { (dateKey, messages) ->
                            item(key = "date_separator_$dateKey") {
                                DateSeparator(date = formatDateHeader(dateKey))
                            }
                            // Requirement 14.1: Key items by message ID for stable identity
                            items(items = messages, key = { it.id }) { message ->
                                MessageBubble(
                                    message = message,
                                    showAvatar = message.direction == BubbleDirection.RECEIVED
                                            && uiState.conversation?.groupId != null,
                                    maxWidth = screenWidth * 0.75f,
                                    onLocationTap = {
                                        val gId = uiState.conversation?.groupId
                                        if (gId != null) onNavigateToGroupMap(gId)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // ── Typing indicator ──────────────────────────────────────────────
            AnimatedVisibility(
                visible = uiState.typingUserName != null,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                Text(
                    text = uiState.typingIndicatorText ?: "${uiState.typingUserName} is typing…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Dimens.spaceLarge, vertical = Dimens.spaceSmall)
                )
            }

            // ── Input bar ─────────────────────────────────────────────────────
            InputBar(
                text = uiState.inputText,
                onTextChange = viewModel::onInputChange,
                onSend = viewModel::sendMessage,
                onLocationSend = {
                    val hasPermission = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasPermission) {
                        viewModel.requestCurrentLocationAndSend()
                    } else {
                        locationPermissionLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                        )
                    }
                }
            )
        }
    }
}

// ── Bubble shape per DESIGN.md Section 6.7 ────────────────────────────────────
// Requirement 16.1:
// - Sent: Radius Large (16dp) all corners except bottom-right = Radius XS (4dp)
// - Received: Radius Large (16dp) all corners except bottom-left = Radius XS (4dp)

private val BubbleRadiusLarge = 16.dp
private val BubbleRadiusTail = 4.dp

private val SentBubbleShape = RoundedCornerShape(
    topStart = BubbleRadiusLarge,
    topEnd = BubbleRadiusLarge,
    bottomStart = BubbleRadiusLarge,
    bottomEnd = BubbleRadiusTail  // tail on sent side (bottom-right)
)

private val ReceivedBubbleShape = RoundedCornerShape(
    topStart = BubbleRadiusLarge,
    topEnd = BubbleRadiusLarge,
    bottomStart = BubbleRadiusTail,  // tail on received side (bottom-left)
    bottomEnd = BubbleRadiusLarge
)

private fun bubbleShape(isSent: Boolean) = if (isSent) SentBubbleShape else ReceivedBubbleShape

// ── Message bubble ────────────────────────────────────────────────────────────

@Composable
private fun MessageBubble(
    message: MessageUiModel,
    showAvatar: Boolean,
    maxWidth: androidx.compose.ui.unit.Dp,
    onLocationTap: () -> Unit
) {
    val isSent = message.direction == BubbleDirection.SENT

    // Requirement 16.1: Sent = Accent Primary (primary), Received = Background Elevated (surfaceContainerHigh)
    val bubbleColor = if (isSent) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val textColor = if (isSent) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val secondaryTextColor = if (isSent) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isSent) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        // Avatar for received group messages
        if (!isSent && showAvatar) {
            if (!message.senderPhotoUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = message.senderPhotoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(28.dp).clip(CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = message.senderName.take(1).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.width(Dimens.spaceSmall))
        } else if (!isSent) {
            Spacer(Modifier.width(36.dp))
        }

        Column(
            horizontalAlignment = if (isSent) Alignment.End else Alignment.Start
        ) {
            if (!isSent && showAvatar) {
                Text(
                    text = message.senderName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = Dimens.spaceSmall, bottom = 2.dp)
                )
            }

            if (message.isLocation) {
                Surface(
                    shape = bubbleShape(isSent),
                    color = bubbleColor,
                    modifier = Modifier
                        .widthIn(max = maxWidth)
                        .clip(bubbleShape(isSent))
                        .clickableIf(true) { onLocationTap() }
                ) {
                    Row(
                        modifier = Modifier.padding(Dimens.spaceLarge),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.LocationOn, null,
                            modifier = Modifier.size(Dimens.iconSizeMedium),
                            tint = textColor
                        )
                        Spacer(Modifier.width(Dimens.spaceSmall))
                        Column {
                            Text(
                                text = message.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = textColor
                            )
                            if (message.locationLabel != null) {
                                Text(
                                    text = message.locationLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = secondaryTextColor
                                )
                            }
                        }
                    }
                }
            } else {
                Surface(
                    shape = bubbleShape(isSent),
                    color = bubbleColor,
                    modifier = Modifier.widthIn(max = maxWidth)
                ) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }

            // Pre-computed time from MessageUiModel — no formatting in the composable
            Text(
                text = message.formattedTime,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Dimens.spaceMedium, vertical = 2.dp)
            )
        }
    }
}

private fun Modifier.clickableIf(enabled: Boolean, onClick: () -> Unit): Modifier =
    if (enabled) this.clickable { onClick() } else this

@Composable
private fun DateSeparator(date: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.spaceMedium),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = date,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Dimens.spaceLarge, vertical = Dimens.spaceSmall)
            )
        }
    }
}

@Composable
private fun InputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onLocationSend: () -> Unit
) {
    Surface(
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.spaceMedium, vertical = Dimens.spaceSmall),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Location button
            IconButton(onClick = onLocationSend) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = "Send location",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Text field (pill shape)
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = { Text("Message") },
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.extraLarge,
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f)
                )
            )

            Spacer(Modifier.width(Dimens.spaceMedium))

            // Send FAB — only shown when text non-empty
            AnimatedVisibility(
                visible = text.isNotBlank(),
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                FloatingActionButton(
                    onClick = onSend,
                    modifier = Modifier.size(40.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    shape = CircleShape
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "Send",
                        modifier = Modifier.size(Dimens.iconSizeMedium),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

/**
 * Converts a yyyy-MM-dd dateKey (pre-computed in [MessageUiModel]) to a
 * human-readable header like "Today", "Yesterday", or "Monday, 3 Jun".
 */
private fun formatDateHeader(dateKey: String): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = sdf.parse(dateKey) ?: return dateKey
        val millis = date.time
        when {
            isToday(millis) -> "Today"
            isYesterday(millis) -> "Yesterday"
            else -> SimpleDateFormat("EEEE, d MMM", Locale.getDefault()).format(date)
        }
    } catch (e: Exception) {
        dateKey
    }
}

private fun isToday(timestamp: Long): Boolean {
    val now = java.util.Calendar.getInstance()
    val cal = java.util.Calendar.getInstance().apply { time = Date(timestamp) }
    return now.get(java.util.Calendar.DATE) == cal.get(java.util.Calendar.DATE)
            && now.get(java.util.Calendar.MONTH) == cal.get(java.util.Calendar.MONTH)
            && now.get(java.util.Calendar.YEAR) == cal.get(java.util.Calendar.YEAR)
}

private fun isYesterday(timestamp: Long): Boolean {
    val now = java.util.Calendar.getInstance()
    val yesterday = java.util.Calendar.getInstance().apply {
        add(java.util.Calendar.DATE, -1)
    }
    val cal = java.util.Calendar.getInstance().apply { time = Date(timestamp) }
    return yesterday.get(java.util.Calendar.DATE) == cal.get(java.util.Calendar.DATE)
            && yesterday.get(java.util.Calendar.MONTH) == cal.get(java.util.Calendar.MONTH)
            && yesterday.get(java.util.Calendar.YEAR) == cal.get(java.util.Calendar.YEAR)
}
