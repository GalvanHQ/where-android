package com.ovi.where.presentation.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.ovi.where.core.utils.showToast
import com.ovi.where.presentation.chat.components.ConversationAvatar
import com.ovi.where.presentation.model.GroupInfoUiState
import com.ovi.where.presentation.model.MediaThumbnail
import kotlinx.coroutines.launch

/**
 * Messenger-style Group Info Screen composable.
 *
 * Displays group details, member list with admin actions, shared media,
 * invite link, and group management options (leave/delete).
 *
 * Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6, 9.7, 9.8, 9.9, 9.10, 9.11
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupInfoScreen(
    onNavigateBack: () -> Unit,
    onNavigateToMediaGallery: () -> Unit,
    onNavigateToAddMembers: () -> Unit,
    onNavigateToMembers: () -> Unit = {},
    onNavigateToNicknames: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToGroupMap: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: GroupInfoViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    // Image picker for group photo
    val imagePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            coroutineScope.launch {
                val result = com.ovi.where.core.utils.ImageUploadUtil.uploadProfilePicture(
                    context = context,
                    imageUri = it
                )
                if (result.isSuccess) {
                    val url = result.getOrNull()
                    if (url != null) {
                        viewModel.updateGroupPhoto(url)
                        context.showToast("Group photo updated")
                    }
                } else {
                    context.showToast("Failed to upload photo")
                }
            }
        }
    }

    GroupInfoScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onNavigateToMediaGallery = onNavigateToMediaGallery,
        onNavigateToAddMembers = onNavigateToAddMembers,
        onNavigateToMembers = onNavigateToMembers,
        onNavigateToNicknames = onNavigateToNicknames,
        onNavigateToSearch = onNavigateToSearch,
        onRetry = { viewModel.retry() },
        onToggleMute = { viewModel.toggleMute() },
        onLeaveGroup = { viewModel.leaveGroup { onNavigateBack() } },
        onDeleteGroup = { viewModel.deleteGroup { onNavigateBack() } },
        onUpdateGroupDetails = { name, description -> viewModel.updateGroupDetails(name, description) },
        onUpdateThemeColor = { viewModel.updateThemeColor(it) },
        onUpdateEmojiShortcut = { viewModel.updateEmojiShortcut(it) },
        onChangeGroupPhoto = { imagePickerLauncher.launch("image/*") },
        modifier = modifier
    )
}

/**
 * Stateless content composable for GroupInfoScreen, testable without ViewModel.
 *
 * Renders the group info UI based on the provided [uiState] and delegates
 * user actions to the provided callbacks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GroupInfoScreenContent(
    uiState: GroupInfoUiState,
    onNavigateBack: () -> Unit,
    onNavigateToMediaGallery: () -> Unit,
    onNavigateToAddMembers: () -> Unit,
    onNavigateToMembers: () -> Unit = {},
    onNavigateToNicknames: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onRetry: () -> Unit,
    onToggleMute: () -> Unit,
    onLeaveGroup: () -> Unit,
    onDeleteGroup: () -> Unit,
    onUpdateGroupDetails: (name: String, description: String) -> Unit = { _, _ -> },
    onUpdateThemeColor: (String?) -> Unit = {},
    onUpdateEmojiShortcut: (String?) -> Unit = {},
    onChangeGroupPhoto: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var showLeaveDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMuteDialog by remember { mutableStateOf(false) }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var showThemeColorDialog by remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Group Info") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.error != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = uiState.error ?: "An error occurred",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = onRetry) {
                        Text("Retry")
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                // ── Header: Large avatar + group name + member count ─────────
                GroupInfoHeader(
                    groupName = uiState.groupName,
                    groupDescription = uiState.groupDescription,
                    groupPhotoUrl = uiState.groupPhotoUrl,
                    memberCount = uiState.memberCount
                )

                Spacer(modifier = Modifier.height(24.dp))

                // ── Action Button Row ────────────────────────────────────────
                ActionButtonRow(
                    isMuted = uiState.isMuted,
                    onAddMembersTap = onNavigateToAddMembers,
                    onMuteTap = { showMuteDialog = true },
                    onSearchTap = onNavigateToSearch
                )

                Spacer(modifier = Modifier.height(24.dp))

                // ── Members Section ──────────────────────────────────────────
                MembersSection(
                    memberCount = uiState.memberCount,
                    onViewAllMembers = onNavigateToMembers
                )

                Spacer(modifier = Modifier.height(16.dp))

                // ── Customize Chat Section ───────────────────────────────────
                CustomizeChatSection(
                    themeColor = uiState.themeColor,
                    emojiShortcut = uiState.emojiShortcut,
                    onEditGroupName = { showEditNameDialog = true },
                    onChangeGroupPhoto = onChangeGroupPhoto,
                    onThemeColor = { showThemeColorDialog = true },
                    onEmojiShortcut = { showEmojiPicker = true },
                    onNicknames = onNavigateToNicknames
                )

                Spacer(modifier = Modifier.height(16.dp))

                // ── Shared Media Section ─────────────────────────────────────
                if (uiState.sharedMedia.isNotEmpty()) {
                    SharedMediaSection(
                        media = uiState.sharedMedia,
                        onSeeAllTap = onNavigateToMediaGallery
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // ── View Media & Files ───────────────────────────────────────
                MoreActionsSection(onNavigateToMediaGallery = onNavigateToMediaGallery)

                Spacer(modifier = Modifier.height(16.dp))

                // ── Invite Link Section (admin only) ─────────────────────────
                if (uiState.isCurrentUserAdmin && uiState.inviteLink != null) {
                    InviteLinkSection(
                        inviteLink = uiState.inviteLink!!,
                        context = context
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // ── Leave Group ──────────────────────────────────────────────
                LeaveGroupRow(onClick = { showLeaveDialog = true })

                // ── Delete Group (admin only) ────────────────────────────────
                if (uiState.isCurrentUserAdmin) {
                    DeleteGroupRow(onClick = { showDeleteDialog = true })
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // ── Leave Group Confirmation Dialog ──────────────────────────────────────
    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text("Leave Group") },
            text = { Text("Are you sure you want to leave this group? You won't be able to see messages or participate anymore.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLeaveDialog = false
                        onLeaveGroup()
                    }
                ) {
                    Text(
                        "Leave",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ── Delete Group Confirmation Dialog ─────────────────────────────────────
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Group") },
            text = { Text("Are you sure you want to delete this group? This action cannot be undone and all messages will be lost.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteGroup()
                    }
                ) {
                    Text(
                        "Delete",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ── Mute Confirmation Dialog ─────────────────────────────────────────────
    if (showMuteDialog) {
        AlertDialog(
            onDismissRequest = { showMuteDialog = false },
            title = {
                Text(if (uiState.isMuted) "Unmute group?" else "Mute group?")
            },
            text = {
                Text(
                    if (uiState.isMuted) "You will start receiving notifications from this group again."
                    else "You will no longer receive notifications from this group."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showMuteDialog = false
                        onToggleMute()
                    }
                ) {
                    Text(if (uiState.isMuted) "Unmute" else "Mute")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMuteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ── Edit Group Name & Description Dialog ─────────────────────────────────
    if (showEditNameDialog) {
        var newName by remember { mutableStateOf(uiState.groupName) }
        var newDescription by remember { mutableStateOf(uiState.groupDescription) }
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text("Edit Group Details") },
            text = {
                Column {
                    Text(
                        text = "Update the group's name and description.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextField(
                        value = newName,
                        onValueChange = { newName = it },
                        singleLine = true,
                        label = { Text("Name") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                            unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                    TextField(
                        value = newDescription,
                        onValueChange = {
                            // Cap description length to avoid Firestore bloat;
                            // matches CreateGroup's 200-char limit.
                            if (it.length <= 200) newDescription = it
                        },
                        label = { Text("Description") },
                        placeholder = { Text("What's this group about?") },
                        supportingText = { Text("${newDescription.length} / 200") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                            unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEditNameDialog = false
                        val trimmedName = newName.trim()
                        val trimmedDescription = newDescription.trim()
                        val nameChanged = trimmedName.isNotBlank() && trimmedName != uiState.groupName
                        val descChanged = trimmedDescription != uiState.groupDescription
                        if (nameChanged || descChanged) {
                            // Send the current value for whichever didn't change so
                            // the VM doesn't blank it out on Firestore.
                            onUpdateGroupDetails(
                                if (nameChanged) trimmedName else uiState.groupName,
                                trimmedDescription
                            )
                            context.showToast("Group details updated")
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ── Theme Color Picker Dialog ────────────────────────────────────────────
    if (showThemeColorDialog) {
        val themeColors = listOf(
            "#5170FF", "#006878", "#8E3A8C", "#6B5E00",
            "#006E2C", "#BA1A1A", "#006491", "#8B4513"
        )
        AlertDialog(
            onDismissRequest = { showThemeColorDialog = false },
            title = { Text("Theme Color") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose a color for this conversation", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        themeColors.take(4).forEach { hex ->
                            val color = try { androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { MaterialTheme.colorScheme.primary }
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .clickable {
                                        showThemeColorDialog = false
                                        onUpdateThemeColor(hex)
                                        context.showToast("Theme color updated")
                                    }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        themeColors.drop(4).forEach { hex ->
                            val color = try { androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { MaterialTheme.colorScheme.primary }
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .clickable {
                                        showThemeColorDialog = false
                                        onUpdateThemeColor(hex)
                                        context.showToast("Theme color updated")
                                    }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeColorDialog = false }) {
                    Text("Cancel")
                }
            },
            dismissButton = {}
        )
    }

    // ── Emoji Shortcut Picker Dialog ─────────────────────────────────────────
    if (showEmojiPicker) {
        val emojiOptions = listOf("👍", "❤️", "😂", "😮", "😢", "🔥", "🎉", "👏", "💯", "🙏", "😊", "🥰")
        AlertDialog(
            onDismissRequest = { showEmojiPicker = false },
            title = { Text("Emoji Shortcut") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Choose a quick-react emoji for this conversation",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
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
                                        .clickable {
                                            showEmojiPicker = false
                                            onUpdateEmojiShortcut(emoji)
                                            context.showToast("Emoji shortcut updated")
                                        }
                                        .padding(8.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showEmojiPicker = false
                    onUpdateEmojiShortcut(null)
                    context.showToast("Emoji shortcut reset")
                }) {
                    Text("Reset to Default")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmojiPicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ── Header: Large avatar + group name + member count ─────────────────────────

@Composable
private fun GroupInfoHeader(
    groupName: String,
    groupDescription: String,
    groupPhotoUrl: String?,
    memberCount: Int
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        // Large centered group avatar (80dp)
        ConversationAvatar(
            name = groupName,
            photoUrl = groupPhotoUrl,
            isOnline = false,
            size = 80.dp,
            indicatorSize = 0.dp,
            indicatorBorderWidth = 0.dp
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Group name
        Text(
            text = groupName.ifBlank { "Unnamed Group" },
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Member count
        Text(
            text = "$memberCount members",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Description (only when set — keep the header tight when empty)
        if (groupDescription.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = groupDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

// ── Action Button Row ────────────────────────────────────────────────────────

@Composable
private fun ActionButtonRow(
    isMuted: Boolean,
    onAddMembersTap: () -> Unit,
    onMuteTap: () -> Unit,
    onSearchTap: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Any group member can invite others — admin gating removed.
        GroupInfoActionButton(
            icon = Icons.Filled.PersonAdd,
            label = "Add",
            modifier = Modifier.weight(1f),
            onClick = onAddMembersTap
        )
        GroupInfoActionButton(
            icon = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
            label = if (isMuted) "Unmute" else "Mute",
            modifier = Modifier.weight(1f),
            onClick = onMuteTap
        )
        GroupInfoActionButton(
            icon = Icons.Filled.Search,
            label = "Search",
            modifier = Modifier.weight(1f),
            onClick = onSearchTap
        )
    }
}

@Composable
private fun GroupInfoActionButton(
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

// ── Members Section ──────────────────────────────────────────────────────────

@Composable
private fun MembersSection(
    memberCount: Int,
    onViewAllMembers: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "$memberCount members",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onViewAllMembers)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Group,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "See chat members",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ── Customize Chat Section ───────────────────────────────────────────────────

@Composable
private fun CustomizeChatSection(
    themeColor: String? = null,
    emojiShortcut: String? = null,
    onEditGroupName: () -> Unit = {},
    onChangeGroupPhoto: () -> Unit = {},
    onThemeColor: () -> Unit = {},
    onEmojiShortcut: () -> Unit = {},
    onNicknames: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "Customize Chat",
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        SectionListItem(
            icon = Icons.Default.Edit,
            label = "Edit Group Details",
            onClick = onEditGroupName
        )

        SectionListItem(
            icon = Icons.Default.PhotoCamera,
            label = "Change Group Photo",
            onClick = onChangeGroupPhoto
        )

        SectionListItem(
            icon = Icons.Default.Palette,
            label = "Theme Color",
            subtitle = themeColor ?: "Default",
            onClick = onThemeColor
        )

        SectionListItem(
            icon = Icons.Filled.EmojiEmotions,
            label = "Emoji Shortcut",
            subtitle = emojiShortcut ?: "👍",
            onClick = onEmojiShortcut
        )

        SectionListItem(
            icon = Icons.Default.PersonAdd,
            label = "Nicknames",
            onClick = onNicknames
        )
    }
}

@Composable
private fun MoreActionsSection(
    onNavigateToMediaGallery: () -> Unit
){
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "More Actions",
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        SectionListItem(
            icon = Icons.Default.Photo,
            label = "View Media & Files",
            onClick = onNavigateToMediaGallery
        )
        SectionListItem(
            icon = Icons.Filled.Notifications,
            label = "Notification Settings",
            onClick = { /* Notification settings — not yet implemented */ }
        )
    }
}

// ── Shared Media Section ─────────────────────────────────────────────────────
@Composable
private fun SharedMediaSection(
    media: List<MediaThumbnail>,
    onSeeAllTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        Text(
            text = "Shared Media",
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
        )

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
                    .clickable(onClick = onSeeAllTap),
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

// ── Invite Link Section ──────────────────────────────────────────────────────

@Composable
private fun InviteLinkSection(
    inviteLink: String,
    context: Context
) {
    // Extract just the invite code (last segment of the link, or the whole string if no slashes)
    val inviteCode = inviteLink.substringAfterLast("/").takeIf { it.isNotBlank() } ?: inviteLink

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .border(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(40.dp)
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Invite Code",
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Code display
        Text(
            text = inviteCode,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "Share this code with others to let them join the group.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Copy + Share buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            TextButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Invite Code", inviteCode))
                    context.showToast("Code copied")
                }
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Copy")
            }
            Spacer(modifier = Modifier.width(48.dp))

            TextButton(
                onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "Join my group on Where! Use invite code: $inviteCode")
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share Invite Code"))
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Share")
            }
        }
    }
}

// ── Leave Group Row ──────────────────────────────────────────────────────────

@Composable
private fun LeaveGroupRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "Leave Group",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error
        )
    }
}

// ── Delete Group Row ─────────────────────────────────────────────────────────

@Composable
private fun DeleteGroupRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "Delete Group",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error
        )
    }
}

// ── Reusable Section List Item ───────────────────────────────────────────────

@Composable
private fun SectionListItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    subtitle: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
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
