package com.ovi.where.presentation.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.ovi.where.core.utils.showToast
import com.ovi.where.presentation.chat.components.ConversationAvatar
import com.ovi.where.presentation.model.ConversationInfoUiState
import com.ovi.where.presentation.model.MediaThumbnail

/**
 * Messenger-style Conversation Info screen for direct message conversations.
 *
 * Displays a large centered avatar, user name, online status, action buttons,
 * customization options, shared media, and privacy/support actions.
 *
 * Uses a scrollable Column layout with section headers in labelLarge + FontWeight.SemiBold
 * and 16dp top margin between sections.
 *
 * Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7, 8.8
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationInfoScreen(
    onNavigateBack: () -> Unit,
    onNavigateToMediaGallery: () -> Unit,
    onNavigateToUserProfile: (String) -> Unit = {},
    onNavigateToChat: () -> Unit = {},
    onNavigateToNicknames: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ConversationInfoViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val errorState by viewModel.errorState.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            errorState != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = errorState ?: "An error occurred",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { viewModel.retry() }) {
                            Text("Retry")
                        }
                    }
                }
            }

            else -> {
                ConversationInfoContent(
                    uiState = uiState,
                    currentUserId = viewModel.currentUserId ?: "",
                    onToggleMute = { viewModel.toggleMute() },
                    onMuteFor = { option -> viewModel.muteFor(option) },
                    onUpdateThemeColor = { viewModel.updateThemeColor(it) },
                    onUpdateEmojiShortcut = { viewModel.updateEmojiShortcut(it) },
                    onNavigateToNicknames = onNavigateToNicknames,
                    onNavigateToMediaGallery = onNavigateToMediaGallery,
                    onNavigateToUserProfile = {
                        uiState.otherUserId?.let { onNavigateToUserProfile(it) }
                    },
                    onNavigateToChat = onNavigateToChat,
                    onBlockUser = { viewModel.blockUser() },
                    onUnblockUser = { viewModel.unblockUser() },
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}

@Composable
internal fun ConversationInfoContent(
    uiState: ConversationInfoUiState,
    currentUserId: String = "",
    onToggleMute: () -> Unit,
    onMuteFor: (com.ovi.where.domain.model.MuteOption) -> Unit = {},
    onUpdateThemeColor: (String?) -> Unit = {},
    onUpdateEmojiShortcut: (String?) -> Unit = {},
    onNavigateToNicknames: () -> Unit = {},
    onNavigateToMediaGallery: () -> Unit,
    onNavigateToUserProfile: () -> Unit = {},
    onNavigateToChat: () -> Unit = {},
    onBlockUser: () -> Unit = {},
    onUnblockUser: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showMuteDialog by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showBlockDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Large centered avatar (80dp)
        ConversationAvatar(
            name = uiState.conversationTitle,
            photoUrl = uiState.photoUrl,
            isOnline = uiState.isOnline,
            size = 80.dp,
            indicatorSize = 16.dp,
            indicatorBorderWidth = 2.5.dp
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Conversation title (use nickname if set)
        val displayTitle = uiState.nicknames[uiState.otherUserId]?.takeIf { it.isNotBlank() }
            ?: uiState.conversationTitle
        Text(
            text = displayTitle,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        // Online status
        val statusText = when {
            uiState.isOnline -> "Active now"
            uiState.lastActiveTime != null -> uiState.lastActiveTime
            else -> null
        }
        if (statusText != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = if (uiState.isOnline) {
                    Color(0xFF44B700)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action button row
        ActionButtonRow(
            isMuted = uiState.isMuted,
            onToggleMute = { showMuteDialog = true },
            onProfileTap = onNavigateToUserProfile,
            onSearchTap = {
                // Navigate back to chat — search will be triggered via savedStateHandle
                onNavigateToChat()
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Shared media section
        if (uiState.sharedMedia.isNotEmpty()) {
            SharedMediaSection(
                media = uiState.sharedMedia,
                onSeeAll = onNavigateToMediaGallery
            )
        }

        // Customize Chat section
        CustomizeChatSection(
            themeColor = uiState.themeColor,
            emojiShortcut = uiState.emojiShortcut,
            onThemeColorTap = { showColorPicker = true },
            onEmojiShortcutTap = { showEmojiPicker = true },
            onNicknamesTap = onNavigateToNicknames
        )

        // More Actions section
        MoreActionsSection(
            isMuted = uiState.isMuted,
            onSearchInConversation = onNavigateToChat,
            onViewMedia = onNavigateToMediaGallery,
            // Tapping the row reuses the same mute flow as the top action bar
            // so the user gets one consistent UI for "manage notifications".
            onOpenNotificationSettings = { showMuteDialog = true }
        )

        // Privacy & Support section (DM only — this screen is always DM)
        PrivacySupportSection(
            isBlocked = uiState.isBlocked,
            onBlockTap = { showBlockDialog = true },
            onReportTap = { showReportDialog = true }
        )

        Spacer(modifier = Modifier.height(32.dp))
    }

    // ── Mute flow ────────────────────────────────────────────────────────
    // Already muted → quick unmute confirmation (the user only needs to
    // confirm the destructive-ish reverse). Not muted yet → duration sheet.
    if (showMuteDialog) {
        if (uiState.isMuted) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showMuteDialog = false },
                title = { Text("Unmute conversation?") },
                text = {
                    Text("You will start receiving notifications from this conversation again.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showMuteDialog = false
                            onToggleMute()
                        }
                    ) { Text("Unmute") }
                },
                dismissButton = {
                    TextButton(onClick = { showMuteDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        } else {
            com.ovi.where.presentation.chat.components.MuteDurationSheet(
                onDismiss = { showMuteDialog = false },
                onSelect = { option ->
                    showMuteDialog = false
                    onMuteFor(option)
                }
            )
        }
    }

    // ── Theme Color Picker Dialog ────────────────────────────────────────
    if (showColorPicker) {
        ThemeColorPickerDialog(
            currentColor = uiState.themeColor,
            onColorSelected = { color ->
                showColorPicker = false
                onUpdateThemeColor(color)
            },
            onDismiss = { showColorPicker = false }
        )
    }

    // ── Emoji Shortcut Picker Dialog ─────────────────────────────────────
    if (showEmojiPicker) {
        EmojiShortcutPickerDialog(
            currentEmoji = uiState.emojiShortcut,
            onEmojiSelected = { emoji ->
                showEmojiPicker = false
                onUpdateEmojiShortcut(emoji)
            },
            onDismiss = { showEmojiPicker = false }
        )
    }

    // ── Block / Unblock Confirmation Sheet ────────────────────────────────
    // Replaces the previous AlertDialog (which only showed a toast and did
    // not actually block the user). Real call now flows through
    // ConversationInfoViewModel.blockUser / unblockUser → FriendshipRepository
    // → blockUser / unblockUser Cloud Functions.
    if (showBlockDialog) {
        val title = uiState.conversationTitle.ifBlank { "this user" }
        if (uiState.isBlocked) {
            com.ovi.where.presentation.chat.components.DestructiveConfirmSheet(
                title = "Unblock $title?",
                message = "They'll be able to message you and see your shared updates again.",
                consequences = emptyList(),
                confirmLabel = "Unblock",
                photoUrl = uiState.photoUrl,
                icon = androidx.compose.material.icons.Icons.Filled.Block,
                onConfirm = {
                    showBlockDialog = false
                    onUnblockUser()
                    context.showToast("User unblocked")
                },
                onDismiss = { showBlockDialog = false }
            )
        } else {
            com.ovi.where.presentation.chat.components.DestructiveConfirmSheet(
                title = "Block $title?",
                message = "They won't know you blocked them.",
                consequences = listOf(
                    "You won't see their messages or location",
                    "They can't message you or invite you to groups",
                    "You can unblock them anytime from this menu"
                ),
                confirmLabel = "Block",
                photoUrl = uiState.photoUrl,
                icon = androidx.compose.material.icons.Icons.Filled.Block,
                onConfirm = {
                    showBlockDialog = false
                    onBlockUser()
                    context.showToast("User blocked")
                },
                onDismiss = { showBlockDialog = false }
            )
        }
    }

    // ── Report Confirmation Dialog ───────────────────────────────────────
    if (showReportDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text("Report user?") },
            text = {
                Text("This will send a report to our team for review. The user won't be notified.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showReportDialog = false
                        context.showToast("Report submitted")
                    }
                ) {
                    Text("Report", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Horizontal row of card-style action buttons with labels.
 * Actions: Profile, Mute, Search
 */
@Composable
internal fun ActionButtonRow(
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    onProfileTap: () -> Unit = {},
    onSearchTap: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        InfoActionButton(
            icon = Icons.Filled.Person,
            label = "Profile",
            modifier = Modifier.weight(1f),
            onClick = onProfileTap
        )
        InfoActionButton(
            icon = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
            label = if (isMuted) "Unmute" else "Mute",
            modifier = Modifier.weight(1f),
            onClick = onToggleMute
        )
        InfoActionButton(
            icon = Icons.Filled.Search,
            label = "Search",
            modifier = Modifier.weight(1f),
            onClick = onSearchTap
        )
    }
}

/**
 * Card-style action button with icon and label.
 * Rounded 16dp corners, surface color background.
 */
@Composable
private fun InfoActionButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    androidx.compose.material3.Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Shared media horizontal scrollable row with "See All" trailing action.
 * Shows up to 3 visible thumbnails.
 */
@Composable
private fun SharedMediaSection(
    media: List<MediaThumbnail>,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        SectionHeader(title = "Shared Media")
        Spacer(modifier = Modifier.height(8.dp))

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            val spacing = 8.dp
            val itemSize = (maxWidth - spacing * 2) / 3

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onSeeAll),
                horizontalArrangement = Arrangement.spacedBy(spacing)
            ) {
                media.take(3).forEach { thumbnail ->
                    val imageUrl = thumbnail.thumbnailUrl

                    if (imageUrl.isNotBlank()) {
                        val imageRequest = remember(imageUrl) {
                            ImageRequest.Builder(context)
                                .data(imageUrl)
                                .size(240, 240)
                                .crossfade(true)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .networkCachePolicy(CachePolicy.ENABLED)
                                .memoryCacheKey(imageUrl)
                                .diskCacheKey(imageUrl)
                                .build()
                        }

                        AsyncImage(
                            model = imageRequest,
                            contentDescription = "Shared media",
                            modifier = Modifier
                                .size(itemSize)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(itemSize)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        )
                    }
                }
            }
        }
    }
}

/**
 * "Customize Chat" section with theme color, emoji shortcut, and nicknames options.
 */
@Composable
private fun CustomizeChatSection(
    themeColor: String? = null,
    emojiShortcut: String? = null,
    onThemeColorTap: () -> Unit = {},
    onEmojiShortcutTap: () -> Unit = {},
    onNicknamesTap: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = "Customize Chat")

        InfoListItem(
            icon = Icons.Filled.ColorLens,
            title = "Theme Color",
            subtitle = themeColor ?: "Default",
            onClick = onThemeColorTap
        )
        InfoListItem(
            icon = Icons.Filled.EmojiEmotions,
            title = "Emoji Shortcut",
            subtitle = emojiShortcut ?: "👍",
            onClick = onEmojiShortcutTap
        )
        InfoListItem(
            icon = Icons.Filled.PersonOutline,
            title = "Nicknames",
            onClick = onNicknamesTap
        )
    }
}

/**
 * "More Actions" section with Search in Conversation, View Media & Files,
 * and Notification Settings options.
 *
 * The Notification Settings row opens the same mute flow used by the top-of-
 * screen mute toggle — a duration picker for not-yet-muted chats, an
 * "Unmute" confirmation when already muted. This keeps a single UX for
 * notification preferences regardless of which entry point the user picks.
 */
@Composable
private fun MoreActionsSection(
    isMuted: Boolean = false,
    onSearchInConversation: () -> Unit = {},
    onViewMedia: () -> Unit = {},
    onOpenNotificationSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = "More Actions")

        InfoListItem(
            icon = Icons.Filled.Search,
            title = "Search in Conversation",
            onClick = onSearchInConversation
        )
        InfoListItem(
            icon = Icons.Filled.Image,
            title = "View Media & Files",
            onClick = onViewMedia
        )
        InfoListItem(
            icon = Icons.Filled.Notifications,
            title = "Notification Settings",
            // Subtitle gives the user a status glance without making them tap
            // through. "On" / "Muted" is enough — duration detail is shown
            // inside the picker if they want to change it.
            subtitle = if (isMuted) "Muted" else "On",
            onClick = onOpenNotificationSettings
        )
    }
}

/**
 * "Privacy & Support" section with Block and Report options in error color.
 * Only shown for direct message conversations.
 */
@Composable
private fun PrivacySupportSection(
    isBlocked: Boolean = false,
    onBlockTap: () -> Unit = {},
    onReportTap: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = "Privacy & Support")

        InfoListItem(
            icon = Icons.Filled.Block,
            title = if (isBlocked) "Unblock" else "Block",
            onClick = onBlockTap,
            tintColor = if (isBlocked) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.error
            }
        )
        InfoListItem(
            icon = Icons.Filled.Flag,
            title = "Report",
            onClick = onReportTap,
            tintColor = MaterialTheme.colorScheme.error
        )
    }
}

/**
 * Section header with labelLarge typography, FontWeight.SemiBold, and 16dp top margin.
 */
@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge.copy(
            fontWeight = FontWeight.SemiBold
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
    )
}

/**
 * A tappable list item with a leading icon and title text.
 * Used in Customize Chat, More Actions, and Privacy & Support sections.
 */
@Composable
private fun InfoListItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    tintColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = tintColor
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = tintColor
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Theme Color Picker Dialog ────────────────────────────────────────────────

private val themeColorOptions = com.ovi.where.core.theme.ConversationThemeColors.OPTIONS

@Composable
private fun ThemeColorPickerDialog(
    currentColor: String?,
    onColorSelected: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Theme Color") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Choose a color for this conversation",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Color grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    themeColorOptions.take(4).forEach { (hex, name) ->
                        ColorCircle(
                            colorHex = hex,
                            isSelected = currentColor == hex,
                            onClick = { onColorSelected(hex) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    themeColorOptions.drop(4).forEach { (hex, name) ->
                        ColorCircle(
                            colorHex = hex,
                            isSelected = currentColor == hex,
                            onClick = { onColorSelected(hex) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onColorSelected(null) }) {
                Text("Reset to Default")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ColorCircle(
    colorHex: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val color = try {
        Color(android.graphics.Color.parseColor(colorHex))
    } catch (_: Exception) {
        MaterialTheme.colorScheme.primary
    }

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .clickable(onClick = onClick)
            .then(
                if (isSelected) Modifier.padding(2.dp) else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(if (isSelected) 36.dp else 40.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .then(
                    Modifier.clickable(onClick = onClick)
                )
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(color = color)
            }
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.ColorLens,
                contentDescription = "Selected",
                modifier = Modifier.size(16.dp),
                tint = Color.White
            )
        }
    }
}

// ── Emoji Shortcut Picker Dialog ─────────────────────────────────────────────

private val emojiOptions = listOf("👍", "❤️", "😂", "😮", "😢", "🔥", "🎉", "👏", "💯", "🙏", "😊", "🥰")

@Composable
private fun EmojiShortcutPickerDialog(
    currentEmoji: String?,
    onEmojiSelected: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Emoji Shortcut") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Choose a quick-react emoji for this conversation",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Emoji grid (3 rows of 4)
                for (row in emojiOptions.chunked(4)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        row.forEach { emoji ->
                            Text(
                                text = emoji,
                                style = MaterialTheme.typography.headlineMedium,
                                modifier = Modifier
                                    .clickable { onEmojiSelected(emoji) }
                                    .padding(8.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onEmojiSelected(null) }) {
                Text("Reset to Default")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// ── Nickname Editor Dialog ────────────────────────────────────────────────────

@Composable
private fun NicknameEditorDialog(
    otherUserId: String,
    otherUserName: String,
    currentNickname: String,
    currentUserId: String = "",
    currentUserName: String = "You",
    currentUserNickname: String = "",
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var otherNickname by remember { mutableStateOf(currentNickname) }
    var selfNickname by remember { mutableStateOf(currentUserNickname) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nicknames") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "Set nicknames for this conversation",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Other user's nickname
                androidx.compose.material3.OutlinedTextField(
                    value = otherNickname,
                    onValueChange = { otherNickname = it },
                    label = { Text(otherUserName) },
                    placeholder = { Text("Set a nickname") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Current user's nickname
                androidx.compose.material3.OutlinedTextField(
                    value = selfNickname,
                    onValueChange = { selfNickname = it },
                    label = { Text(currentUserName) },
                    placeholder = { Text("Set your nickname") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // Save both nicknames
                if (otherUserId.isNotBlank()) onSave(otherUserId, otherNickname)
                if (currentUserId.isNotBlank()) onSave(currentUserId, selfNickname)
                onDismiss()
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
