package com.ovi.where.presentation.chat

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ovi.where.core.theme.Dimens
import com.ovi.where.core.utils.LocalReducedMotion
import com.ovi.where.core.utils.showToast
import com.ovi.where.presentation.chat.components.AnimatedMessageBubble
import com.ovi.where.presentation.chat.components.ChatBubble
import com.ovi.where.presentation.chat.components.ChatEmptyState
import com.ovi.where.presentation.chat.components.ChatInputBar
import com.ovi.where.presentation.chat.components.DateSeparator
import com.ovi.where.presentation.chat.components.ImageSizeLimitError
import com.ovi.where.presentation.chat.components.MentionSuggestionPopup
import com.ovi.where.presentation.chat.components.MessageAnimationConstants
import com.ovi.where.presentation.chat.components.MessageSearchBar
import com.ovi.where.presentation.chat.components.NewMessageIndicator
import com.ovi.where.presentation.chat.components.QueuedForSyncBanner
import com.ovi.where.presentation.chat.components.ReactionPickerOverlay
import com.ovi.where.presentation.chat.components.ReplyPreviewBar
import com.ovi.where.presentation.chat.components.SwipeToReplyContainer
import com.ovi.where.presentation.chat.components.TypingIndicator
import com.ovi.where.presentation.model.BubbleDirection
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

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
    startInSearchMode: Boolean = false,
    onNavigateBack: () -> Unit = {},
    onNavigateToUserProfile: (String) -> Unit = {},
    onNavigateToGroupInfo: (String) -> Unit = {},
    onNavigateToGroupMap: (String) -> Unit = {},
    onNavigateToEditGroup: (String) -> Unit = {},
    onNavigateToMediaGallery: (String) -> Unit = {},
    onNavigateToConversationInfo: (String) -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val voicePlaybackState by viewModel.voicePlaybackController.playbackState.collectAsState()
    val listState = rememberLazyListState()
    val currentUserId = viewModel.currentUserId
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Theme color — single source of truth derived from conversation state.
    // Stable: only changes when the actual hex string value changes.
    // During initial load (conversation == null), returns null (uses default primary).
    val themeColorHex = uiState.conversation?.themeColor
    val conversationThemeColor: androidx.compose.ui.graphics.Color? = remember(themeColorHex) {
        if (themeColorHex.isNullOrBlank()) null
        else try { androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(themeColorHex)) } catch (_: Exception) { null }
    }
    val reducedMotion = LocalReducedMotion.current
    val density = LocalDensity.current

    // ── Message animation state (Task 15.1) ───────────────────────────────────
    // Track IDs of messages that have just appeared and should animate in.
    var newMessageIds by remember { mutableStateOf(setOf<String>()) }
    // Track the previous message count to detect new arrivals.
    var previousMessageCount by remember { mutableStateOf(0) }
    // Track whether to show the "new message" indicator (Requirement 23.4).
    var showNewMessageIndicator by remember { mutableStateOf(false) }
    // Count of unread messages while scrolled up.
    var newMessageUnreadCount by remember { mutableStateOf(0) }

    // FocusRequester for the input field — used by swipe-to-reply to focus input (Requirement 8.3)
    val inputFocusRequester = remember { FocusRequester() }

    // Location permission launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            viewModel.requestCurrentLocationAndSend()
        } else {
            android.widget.Toast.makeText(context, "Location permission denied", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // ── Image Attachment: Gallery picker with multi-select (max 5) ──────────────
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<android.net.Uri> ->
        val selected = uris.take(5) // Max 5 photos
        for (sourceUri in selected) {
            val cacheFile = java.io.File.createTempFile("gallery_", ".jpg", context.cacheDir)
            try {
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    cacheFile.outputStream().use { output -> input.copyTo(output) }
                }
                val localUri = android.net.Uri.fromFile(cacheFile)
                viewModel.sendImageMessage(localUri)
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Failed to read image", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Image Attachment: Camera capture ──────────────────────────────────────
    // Create a temporary URI for the camera to write to
    var cameraImageUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            cameraImageUri?.let { viewModel.sendImageMessage(it) }
        }
    }

    // Camera permission launcher (required for camera capture)
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                java.io.File.createTempFile("camera_", ".jpg", context.cacheDir)
            )
            cameraImageUri = uri
            cameraLauncher.launch(uri)
        } else {
            android.widget.Toast.makeText(context, "Camera permission denied", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // Microphone permission launcher (required for voice recording)
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startVoiceRecording()
        } else {
            android.widget.Toast.makeText(context, "Microphone permission denied", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(conversationId) {
        viewModel.init(conversationId)
    }

    // ── Task 16.2: Wire ChatSocketIoClient lifecycle to ChatScreen ─────────────
    // Connect on foreground, disconnect on background (Requirements 13.1, 13.4, 13.5)
    // Pause voice playback on navigate away (Requirement 11.9)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, conversationId) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    // Reconnect when app returns to foreground
                    viewModel.onForeground()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    // Pause voice playback and disconnect when app goes to background
                    // Requirement 11.9: Pause playback when user navigates away
                    viewModel.pauseVoicePlayback()
                    viewModel.onBackground()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            // Requirement 11.9: Pause playback when ChatScreen is disposed
            viewModel.pauseVoicePlayback()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Mark read when screen is shown
    LaunchedEffect(uiState.messages.size) {
        viewModel.markRead()
    }

    // ── Auto-scroll with 150dp threshold (Task 15.1, Requirements 23.3, 23.4) ──
    // When a new message arrives:
    // - If within 150dp of last visible: auto-scroll with 300ms decelerate animation
    // - If > 150dp above: show "new message" indicator instead
    LaunchedEffect(uiState.messages.size) {
        val currentCount = uiState.messages.size
        if (currentCount > 0 && previousMessageCount == 0) {
            // Initial load — scroll to bottom immediately
            listState.scrollToItem(currentCount - 1)
        } else if (currentCount > previousMessageCount && previousMessageCount > 0) {
            // New messages arrived — determine if we should auto-scroll or show indicator
            val newMessages = uiState.messages.takeLast(currentCount - previousMessageCount)
            val newIds = newMessages.map { it.id }.toSet()
            newMessageIds = newMessageIds + newIds

            val layoutInfo = listState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
            val totalItems = layoutInfo.totalItemsCount

            if (lastVisibleItem != null && totalItems > 0) {
                // Calculate distance from the bottom of the list
                val viewportEnd = layoutInfo.viewportEndOffset
                val lastItemEnd = lastVisibleItem.offset + lastVisibleItem.size
                val distanceFromBottom = (totalItems - 1 - lastVisibleItem.index) *
                        (lastVisibleItem.size) + (lastItemEnd - viewportEnd)

                val thresholdPx = with(density) {
                    MessageAnimationConstants.AUTO_SCROLL_THRESHOLD_DP.dp.roundToPx()
                }

                if (distanceFromBottom <= thresholdPx || lastVisibleItem.index >= totalItems - 3) {
                    // Within 150dp — auto-scroll with 300ms animation (Requirement 23.3)
                    if (reducedMotion) {
                        listState.scrollToItem(totalItems - 1)
                    } else {
                        listState.animateScrollToItem(
                            index = totalItems - 1
                        )
                    }
                    showNewMessageIndicator = false
                    newMessageUnreadCount = 0
                } else {
                    // > 150dp above — show "new message" indicator (Requirement 23.4)
                    showNewMessageIndicator = true
                    newMessageUnreadCount += newMessages.size
                }
            } else {
                // No visible items yet, just scroll to bottom
                if (totalItems > 0) {
                    listState.scrollToItem(totalItems - 1)
                }
            }
        }
        previousMessageCount = currentCount
    }

    // Hide new message indicator when user scrolls to bottom manually
    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
            val totalItems = layoutInfo.totalItemsCount
            if (lastVisibleItem != null && totalItems > 0) {
                lastVisibleItem.index >= totalItems - 3
            } else {
                true
            }
        }.distinctUntilChanged().collect { isAtBottom ->
            if (isAtBottom) {
                showNewMessageIndicator = false
                newMessageUnreadCount = 0
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

    // ── Activate search when requested by ConversationInfo ──────────────────
    LaunchedEffect(startInSearchMode) {
        if (startInSearchMode) {
            viewModel.activateSearch(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset
            )
        }
    }

    // ── Admin Overflow Menu: Invite Link handling (Task 10.1, Requirement 15.5) ──
    LaunchedEffect(Unit) {
        viewModel.inviteLinkEvent.collect { link ->
            // Copy to clipboard
            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Invite Link", link)
            clipboard.setPrimaryClip(clip)
            // Show "Link copied" toast for 2 seconds
            context.showToast("Link copied")
            // Present system share sheet
            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, link)
            }
            context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Invite Link"))
        }
    }

    // ── Admin Overflow Menu: Member Picker Dialog (Task 10.1, Requirement 15.2) ──
    if (uiState.showMemberPickerDialog) {
        com.ovi.where.presentation.chat.components.MemberPickerDialog(
            members = uiState.groupMembersForPicker,
            onDismiss = { viewModel.dismissMemberPickerDialog() },
            onConfirm = { memberId, displayName ->
                viewModel.muteMember(memberId, displayName)
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ChatHeader(
                conversation = uiState.conversation,
                onNavigateBack = onNavigateBack,
                onNavigateToGroupInfo = onNavigateToGroupInfo,
                onNavigateToConversationInfo = onNavigateToConversationInfo,
                onlineMemberCount = uiState.onlineMemberCount,
                isOtherUserFriend = uiState.isOtherUserFriend
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
            // ── Queued for sync banner (Requirement 7.6) ──────────────────────
            // Non-modal 48dp banner shown when a write action is attempted while offline.
            QueuedForSyncBanner(visible = uiState.showQueuedForSyncBanner)


            // ── Message Search Bar (Task 8.1, Requirements 13.1-13.7) ─────────
            if (uiState.isSearchActive) {
                // Requirement 13.7: Back gesture dismisses search and restores scroll position
                BackHandler {
                    val (scrollIndex, scrollOffset) = viewModel.dismissSearch()
                    coroutineScope.launch {
                        listState.scrollToItem(scrollIndex, scrollOffset)
                    }
                }

                MessageSearchBar(
                    query = uiState.searchQuery,
                    onQueryChange = viewModel::onSearchQueryChange,
                    resultCount = uiState.searchResultIds.size,
                    currentResultIndex = uiState.currentSearchResultIndex,
                    onNavigateUp = viewModel::navigateSearchPrevious,
                    onNavigateDown = viewModel::navigateSearchNext,
                    onDismiss = {
                        // Requirement 13.7: On dismiss, restore previous scroll position
                        val (scrollIndex, scrollOffset) = viewModel.dismissSearch()
                        coroutineScope.launch {
                            listState.scrollToItem(scrollIndex, scrollOffset)
                        }
                    }
                )
            }

            // Scroll to current search result when it changes
            // Requirement 13.3: Scroll to first match (oldest first)
            // Requirement 13.4/13.5: Scroll to next/previous match on arrow tap
            LaunchedEffect(uiState.currentSearchResultIndex, uiState.searchResultIds) {
                if (uiState.isSearchActive && uiState.searchResultIds.isNotEmpty() && uiState.currentSearchResultIndex >= 0) {
                    val targetMessageId = uiState.searchResultIds[uiState.currentSearchResultIndex]
                    // Find the index of this message in the flat message list
                    val messageIndex = uiState.messages.indexOfFirst { it.id == targetMessageId }
                    if (messageIndex >= 0) {
                        // Account for date separators and pagination items in the LazyColumn.
                        // Each message with showDateSeparator=true adds an extra item before it.
                        var lazyColumnIndex = 0
                        for (i in 0 until uiState.messages.size) {
                            val msg = uiState.messages[i]
                            if (msg.showDateSeparator) lazyColumnIndex++ // date separator item
                            if (msg.id == targetMessageId) break
                            lazyColumnIndex++ // message item
                        }
                        // Add offset for pagination loading/error items at top
                        if (uiState.isLoadingMore) lazyColumnIndex++
                        if (uiState.paginationError) lazyColumnIndex++

                        listState.animateScrollToItem(lazyColumnIndex)
                    }
                }
            }

            // ── Message list ──────────────────────────────────────────────────
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                if (uiState.isLoading || uiState.conversation == null) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (uiState.messages.isEmpty()) {
                    // Requirement 10.5: Centered empty state with illustration and "Say hi!" prompt
                    ChatEmptyState(modifier = Modifier.align(Alignment.Center))
                } else {
                    // Stable lambda references (Requirement 17.4, 19.6):
                    // Pre-create remembered lambdas outside the items block to avoid
                    // allocating new lambda instances on each recomposition/scroll frame.
                    val onLoadOlderMessages = remember { { viewModel.loadOlderMessages() } }
                    val onLocationTap = remember {
                        {
                            val gId = uiState.conversation?.groupId
                            if (gId != null) onNavigateToGroupMap(gId)
                        }
                    }
                    val isGroupConversation = uiState.conversation?.groupId != null
                    val searchQuery = if (uiState.isSearchActive) uiState.searchQuery else null
                    val searchResultIds = uiState.searchResultIds
                    val currentSearchResultIndex = uiState.currentSearchResultIndex
                    // Track new message IDs for entrance animation (Task 15.1)
                    val animatableNewMessageIds = newMessageIds

                    // Requirement 2.5: Maintain scroll position on prepend (pagination)
                    // LazyColumn with reverseLayout=false; items keyed by message ID ensures
                    // Compose maintains scroll position when items are prepended.
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            horizontal = Dimens.spaceLarge,
                            vertical = Dimens.spaceMedium
                        )
                    ) {
                        // ── Pagination loading indicator at top (Requirement 2.3) ──
                        // Requirement 19.1: Unique contentType "pagination_indicator" and stable key
                        if (uiState.isLoadingMore) {
                            item(
                                key = "__pagination_loading__",
                                contentType = "pagination_indicator"
                            ) {
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
                        // Requirement 19.1: Unique contentType "pagination_indicator" and stable key
                        if (uiState.paginationError) {
                            item(
                                key = "__pagination_error__",
                                contentType = "pagination_indicator"
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = Dimens.spaceSmall),
                                    contentAlignment = Alignment.Center
                                ) {
                                    TextButton(onClick = onLoadOlderMessages) {
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

                        // ── Messages with ChatBubble and grouping (Requirements 4.1-4.9, 10.3, 10.4) ──
                        // Flat iteration over messages using per-message grouping metadata.
                        // Date separators are inserted based on showDateSeparator flag.
                        // Spacing: 2dp between grouped messages, 8dp for non-grouped.
                        items(
                            items = uiState.messages,
                            key = { it.id },
                            contentType = { msg ->
                                when {
                                    msg.isVoice -> "voice_bubble"
                                    msg.isImage -> "image_bubble"
                                    msg.isLocation -> "location_bubble"
                                    else -> "text_bubble"
                                }
                            }
                        ) { message ->
                            // Determine if this message is a search result and if it's the current focused result
                            val isSearchHighlighted = searchQuery != null &&
                                    searchResultIds.contains(message.id)
                            val isCurrentSearchResult = searchQuery != null &&
                                    currentSearchResultIndex >= 0 &&
                                    currentSearchResultIndex < searchResultIds.size &&
                                    searchResultIds[currentSearchResultIndex] == message.id

                            // Requirement 17.4 / 19.6: Stable lambda references via remember
                            val onReply = remember(message.id) {
                                {
                                    viewModel.setReplyingTo(message)
                                    try {
                                        inputFocusRequester.requestFocus()
                                    } catch (_: Exception) {}
                                    Unit
                                }
                            }

                            // Task 19.1, Requirement 27.1: Long-press (500ms) → show ReactionPickerOverlay
                            val onLongPress = remember(message.id) {
                                { viewModel.showReactionPicker(message.id) }
                            }

                            // Task 19.1, Requirement 27.5: Retry on FAILED image messages
                            val onRetryMessage = remember(message.id) {
                                { viewModel.retryMessage(message.id) }
                            }

                            // Task 19.1: Reaction badge tap toggles reaction
                            val onReactionTapHandler = remember(message.id) {
                                { emoji: String -> viewModel.toggleReaction(message.id, emoji) }
                            }

                            val onVoicePlayPause = remember(message.id, message.voiceUrl, message.voiceDurationMs) {
                                if (message.isVoice && message.voiceUrl != null && message.voiceDurationMs != null) {
                                    {
                                        viewModel.toggleVoicePlayback(
                                            messageId = message.id,
                                            audioUrl = message.voiceUrl,
                                            durationMs = message.voiceDurationMs
                                        )
                                    }
                                } else null
                            }

                            val onVoiceSeek = remember(message.id, message.voiceDurationMs) {
                                if (message.isVoice && message.voiceDurationMs != null) {
                                    { progress: Float ->
                                        viewModel.seekVoiceMessage(
                                            messageId = message.id,
                                            progress = progress,
                                            durationMs = message.voiceDurationMs
                                        )
                                    }
                                } else null
                            }

                            Column {
                                // Skip rendering for images that are part of a collage (rendered by the first item)
                                if (message.isHiddenInCollage) return@Column

                                // Date separator above this message if day boundary (Requirement 10.3, 10.4)
                                if (message.showDateSeparator && message.dateSeparatorLabel != null) {
                                    DateSeparator(label = message.dateSeparatorLabel)
                                }

                                // Spacing: 2dp between grouped messages, 8dp for non-grouped (Requirement 4.6)
                                if (!message.isFirstInGroup) {
                                    Spacer(modifier = Modifier.height(Dimens.spaceXSmall)) // 2dp
                                } else if (!message.showDateSeparator) {
                                    // First in group but not first message overall (no date separator above)
                                    Spacer(modifier = Modifier.height(Dimens.spaceMedium)) // 8dp
                                }

                                // Requirement 8.1-8.7: Swipe-to-reply gesture on message bubbles
                                // Task 15.1: Wrap with entrance animation (Requirements 23.1, 23.2, 23.7)
                                AnimatedMessageBubble(
                                    messageId = message.id,
                                    direction = message.direction,
                                    isNewMessage = animatableNewMessageIds.contains(message.id)
                                ) {
                                    SwipeToReplyContainer(onReply = onReply) {
                                        // Voice playback state for this message
                                        val isThisVoicePlaying = voicePlaybackState.activeMessageId == message.id && voicePlaybackState.isPlaying
                                        val voiceProgress = if (voicePlaybackState.activeMessageId == message.id) voicePlaybackState.progress else 0f
                                        val voiceCurrentPos = if (voicePlaybackState.activeMessageId == message.id) voicePlaybackState.currentPositionMs else 0L

                                        // Single unified ChatBubble for ALL message types
                                        ChatBubble(
                                            message = message,
                                            isGroupChat = isGroupConversation,
                                            isFirstInGroup = message.isFirstInGroup,
                                            isLastInGroup = message.isLastInGroup,
                                            showSenderAvatar = message.isLastInGroup
                                                    && message.direction == BubbleDirection.RECEIVED
                                                    && isGroupConversation,
                                            themeColor = conversationThemeColor,
                                            onLongPress = onLongPress,
                                            onRetry = onRetryMessage,
                                            onLocationTap = onLocationTap,
                                            onReplyQuoteTap = { replyId ->
                                                coroutineScope.launch {
                                                    val index = uiState.messages.indexOfFirst { it.id == replyId }
                                                    if (index >= 0) {
                                                        listState.animateScrollToItem(index)
                                                    }
                                                }
                                            },
                                            onReactionTap = onReactionTapHandler,
                                            onVoicePlayPause = onVoicePlayPause,
                                            onVoiceSeek = onVoiceSeek,
                                            isVoicePlaying = isThisVoicePlaying,
                                            voiceProgress = voiceProgress,
                                            voiceCurrentPositionMs = voiceCurrentPos
                                        )
                                    }
                                }
                            }
                        }

                        // ── Typing indicator inside LazyColumn (Requirement 17.3, 27.4) ──
                        // Unique contentType "typing_indicator" and stable key
                        // Uses the TypingIndicator composable with animated 3-dot + formatted text
                        if (uiState.typingIndicatorText != null) {
                            item(
                                key = "__typing_indicator__",
                                contentType = "typing_indicator"
                            ) {
                                TypingIndicator(
                                    typingText = uiState.typingIndicatorText
                                )
                            }
                        }
                    }
                }

                // ── "New message" indicator (Task 15.1, Requirement 23.4) ─────────
                // Shown when user is scrolled > 150dp above last visible message and
                // a new message arrives. Tap scrolls to latest with 300ms animation.
                NewMessageIndicator(
                    visible = showNewMessageIndicator,
                    unreadCount = newMessageUnreadCount,
                    onClick = {
                        coroutineScope.launch {
                            val totalItems = listState.layoutInfo.totalItemsCount
                            if (totalItems > 0) {
                                if (reducedMotion) {
                                    // Requirement 23.7: Apply instantly with no transition
                                    listState.scrollToItem(totalItems - 1)
                                } else {
                                    // Requirement 23.4: 300ms decelerate animation
                                    listState.animateScrollToItem(totalItems - 1)
                                }
                            }
                            showNewMessageIndicator = false
                            newMessageUnreadCount = 0
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }

            // ── Typing indicator (outside LazyColumn for AnimatedVisibility, Requirement 27.4) ──
            // Show when typingIndicatorText non-null; animated 3-dot + formatted text
            TypingIndicator(
                typingText = if (uiState.typingUserName != null && uiState.typingIndicatorText == null) {
                    "${uiState.typingUserName} is typing\u2026"
                } else {
                    null
                }
            )

            // ── Reply preview bar (Requirements 8.1, 8.6, 8.7) ───────────────
            // Shown when the user swipes a message or taps Reply in context menu.
            // Requirement 8.6: Dismiss button removes the reply preview bar.
            // Requirement 8.7: Swiping a new message replaces the existing reply.
            val replyingTo = uiState.replyingToMessage
            if (replyingTo != null) {
                ReplyPreviewBar(
                    replyingToMessage = replyingTo,
                    onDismiss = { viewModel.clearReply() }
                )
            }

            // ── Mention suggestion popup (Requirement 14.1) ─────────────────
            if (uiState.isMentionPopupVisible && uiState.mentionSuggestions.isNotEmpty()) {
                MentionSuggestionPopup(
                    suggestions = uiState.mentionSuggestions,
                    onMemberSelected = { member -> viewModel.selectMention(member) }
                )
            }

            // ── Image size limit error (Requirement 6.7) ──────────────────────
            ImageSizeLimitError(visible = uiState.showImageSizeError)

            // ── Input bar (Messenger-style, Requirements 5.1-5.6) ──────────────
            ChatInputBar(
                text = uiState.inputText,
                onTextChange = viewModel::onInputChange,
                onSend = viewModel::sendMessage,
                onCameraTap = {
                    // Launch camera after checking permission
                    if (context.checkSelfPermission(Manifest.permission.CAMERA) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            java.io.File.createTempFile("camera_", ".jpg", context.cacheDir)
                        )
                        cameraImageUri = uri
                        cameraLauncher.launch(uri)
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                onAttachmentTap = {
                    // Launch system image picker (multi-select, max 5)
                    galleryLauncher.launch("image/*")
                },
                // Voice recording state & callbacks
                isVoiceRecording = uiState.isVoiceRecording,
                voiceRecordingDurationMs = uiState.voiceRecordingDurationMs,
                voiceWaveformAmplitudes = uiState.voiceWaveformAmplitudes,
                onVoiceRecordStart = {
                    if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        viewModel.startVoiceRecording()
                    } else {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onVoiceRecordStop = viewModel::stopVoiceRecordingAndSend,
                onVoiceRecordCancel = viewModel::cancelVoiceRecording,
                // Emoji shortcut
                emojiShortcut = uiState.conversation?.emojiShortcut,
                onEmojiShortcutSend = {
                    val emoji = uiState.conversation?.emojiShortcut
                    if (emoji != null) {
                        viewModel.onInputChange(emoji)
                        viewModel.sendMessage()
                    }
                },
                themeColor = conversationThemeColor,
                mentionRanges = uiState.mentionRanges
            )
        }
    }

    // ── Reaction Picker Overlay (Task 19.1, Requirement 27.1) ─────────────────
    // Long-press (500ms) message → ReactionPickerOverlay centered with scrim;
    // on emoji select call toggleReaction, dismiss.
    // Tap outside/back: dismiss without reaction.
    if (uiState.showReactionPicker) {
        BackHandler { viewModel.dismissReactionPicker() }
    }
    ReactionPickerOverlay(
        visible = uiState.showReactionPicker,
        onEmojiSelected = viewModel::onReactionSelected,
        onDismiss = viewModel::dismissReactionPicker
    )
}

