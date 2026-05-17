package com.ovi.where.presentation.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block

import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Search

import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
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
    conversationId: String,
    onNavigateBack: () -> Unit,
    onNavigateToMediaGallery: () -> Unit,
    onNavigateToUserProfile: (String) -> Unit = {},
    onNavigateToChat: () -> Unit = {},
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
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
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
                    onToggleMute = { viewModel.toggleMute() },
                    onNavigateToMediaGallery = onNavigateToMediaGallery,
                    onNavigateToUserProfile = {
                        uiState.otherUserId?.let { onNavigateToUserProfile(it) }
                    },
                    onNavigateToChat = onNavigateToChat,
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}

@Composable
internal fun ConversationInfoContent(
    uiState: ConversationInfoUiState,
    onToggleMute: () -> Unit,
    onNavigateToMediaGallery: () -> Unit,
    onNavigateToUserProfile: () -> Unit = {},
    onNavigateToChat: () -> Unit = {},
    modifier: Modifier = Modifier
) {
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

        // Conversation title
        Text(
            text = uiState.conversationTitle,
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
            onToggleMute = onToggleMute,
            onProfileTap = onNavigateToUserProfile,
            onSearchTap = onNavigateToChat
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
        CustomizeChatSection()

        // More Actions section
        MoreActionsSection()

        // Privacy & Support section (DM only — this screen is always DM)
        PrivacySupportSection()

        Spacer(modifier = Modifier.height(32.dp))
    }
}

/**
 * Horizontal row of circular action buttons with labels.
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
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        ActionButton(
            icon = Icons.Filled.Person,
            label = "Profile",
            onClick = onProfileTap
        )
        ActionButton(
            icon = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
            label = if (isMuted) "Unmute" else "Mute",
            onClick = onToggleMute
        )
        ActionButton(
            icon = Icons.Filled.Search,
            label = "Search",
            onClick = onSearchTap
        )
    }
}

/**
 * Single circular action button with icon and label below.
 * Icon circle is 40dp with surfaceContainerHigh background.
 */
@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(4.dp)
            .semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
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
    Column(modifier = modifier.fillMaxWidth()) {
        // Section header
        SectionHeader(title = "Shared Media")

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            media.take(6).forEach { thumbnail ->
                AsyncImage(
                    model = thumbnail.thumbnailUrl,
                    contentDescription = "Shared media",
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        }

        // "See All" action
        TextButton(
            onClick = onSeeAll,
            modifier = Modifier.padding(start = 8.dp)
        ) {
            Text(
                text = "See All",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * "Customize Chat" section with theme color, emoji shortcut, and nicknames options.
 */
@Composable
private fun CustomizeChatSection(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = "Customize Chat")

        InfoListItem(
            icon = Icons.Filled.ColorLens,
            title = "Theme Color",
            onClick = { /* TODO */ }
        )
        InfoListItem(
            icon = Icons.Filled.EmojiEmotions,
            title = "Emoji Shortcut",
            onClick = { /* TODO */ }
        )
        InfoListItem(
            icon = Icons.Filled.PersonOutline,
            title = "Nicknames",
            onClick = { /* TODO */ }
        )
    }
}

/**
 * "More Actions" section with Search in Conversation, View Media & Files,
 * and Notification Settings options.
 */
@Composable
private fun MoreActionsSection(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = "More Actions")

        InfoListItem(
            icon = Icons.Filled.Search,
            title = "Search in Conversation",
            onClick = { /* TODO */ }
        )
        InfoListItem(
            icon = Icons.Filled.Image,
            title = "View Media & Files",
            onClick = { /* TODO */ }
        )
        InfoListItem(
            icon = Icons.Filled.Notifications,
            title = "Notification Settings",
            onClick = { /* TODO */ }
        )
    }
}

/**
 * "Privacy & Support" section with Block and Report options in error color.
 * Only shown for direct message conversations.
 */
@Composable
private fun PrivacySupportSection(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = "Privacy & Support")

        InfoListItem(
            icon = Icons.Filled.Block,
            title = "Block",
            onClick = { /* TODO */ },
            tintColor = MaterialTheme.colorScheme.error
        )
        InfoListItem(
            icon = Icons.Filled.Flag,
            title = "Report",
            onClick = { /* TODO */ },
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
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = tintColor
        )
    }
}
