package com.ovi.where.presentation.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.ovi.where.presentation.chat.components.ConversationAvatar
import com.ovi.where.presentation.model.GroupInfoUiState
import com.ovi.where.presentation.model.GroupMemberUiModel
import com.ovi.where.presentation.model.MediaThumbnail

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
    groupId: String,
    onNavigateBack: () -> Unit,
    onNavigateToMediaGallery: () -> Unit,
    onNavigateToAddMembers: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GroupInfoViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    GroupInfoScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onNavigateToMediaGallery = onNavigateToMediaGallery,
        onNavigateToAddMembers = onNavigateToAddMembers,
        onRetry = { viewModel.retry() },
        onToggleMute = { viewModel.toggleMute() },
        onMakeAdmin = { userId -> viewModel.makeAdmin(userId) },
        onRemoveMember = { userId -> viewModel.removeMember(userId) },
        onLeaveGroup = { viewModel.leaveGroup { onNavigateBack() } },
        onDeleteGroup = { viewModel.deleteGroup { onNavigateBack() } },
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
    onRetry: () -> Unit,
    onToggleMute: () -> Unit,
    onMakeAdmin: (String) -> Unit,
    onRemoveMember: (String) -> Unit,
    onLeaveGroup: () -> Unit,
    onDeleteGroup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var showLeaveDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

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
                    groupPhotoUrl = uiState.groupPhotoUrl,
                    memberCount = uiState.memberCount
                )

                Spacer(modifier = Modifier.height(24.dp))

                // ── Action Button Row ────────────────────────────────────────
                ActionButtonRow(
                    isAdmin = uiState.isCurrentUserAdmin,
                    isMuted = uiState.isMuted,
                    onAddMembersTap = onNavigateToAddMembers,
                    onAudioCallTap = { /* placeholder */ },
                    onVideoCallTap = { /* placeholder */ },
                    onMuteTap = onToggleMute,
                    onSearchTap = { /* placeholder */ }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // ── Members Section ──────────────────────────────────────────
                MembersSection(
                    members = uiState.members,
                    isCurrentUserAdmin = uiState.isCurrentUserAdmin,
                    onAddMembersTap = onNavigateToAddMembers,
                    onMakeAdmin = onMakeAdmin,
                    onRemoveMember = onRemoveMember
                )

                Spacer(modifier = Modifier.height(16.dp))

                // ── Customize Chat Section ───────────────────────────────────
                CustomizeChatSection()

                Spacer(modifier = Modifier.height(16.dp))

                // ── Shared Media Section ─────────────────────────────────────
                if (uiState.sharedMedia.isNotEmpty()) {
                    SharedMediaSection(
                        media = uiState.sharedMedia,
                        onSeeAllTap = onNavigateToMediaGallery
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

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
}

// ── Header: Large avatar + group name + member count ─────────────────────────

@Composable
private fun GroupInfoHeader(
    groupName: String,
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
    }
}

// ── Action Button Row ────────────────────────────────────────────────────────

@Composable
private fun ActionButtonRow(
    isAdmin: Boolean,
    isMuted: Boolean,
    onAddMembersTap: () -> Unit,
    onAudioCallTap: () -> Unit,
    onVideoCallTap: () -> Unit,
    onMuteTap: () -> Unit,
    onSearchTap: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        if (isAdmin) {
            ActionButton(
                icon = Icons.Default.PersonAdd,
                label = "Add",
                contentDescription = "Add Members",
                onClick = onAddMembersTap
            )
        }
        ActionButton(
            icon = Icons.Default.Phone,
            label = "Audio",
            contentDescription = "Audio Call",
            onClick = onAudioCallTap
        )
        ActionButton(
            icon = Icons.Default.Videocam,
            label = "Video",
            contentDescription = "Video Call",
            onClick = onVideoCallTap
        )
        ActionButton(
            icon = Icons.Default.VolumeOff,
            label = if (isMuted) "Unmute" else "Mute",
            contentDescription = if (isMuted) "Unmute notifications" else "Mute notifications",
            onClick = onMuteTap
        )
        ActionButton(
            icon = Icons.Default.Search,
            label = "Search",
            contentDescription = "Search in conversation",
            onClick = onSearchTap
        )
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    contentDescription: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp)
            .semantics { this.contentDescription = contentDescription }
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
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Members Section ──────────────────────────────────────────────────────────

@Composable
private fun MembersSection(
    members: List<GroupMemberUiModel>,
    isCurrentUserAdmin: Boolean,
    onAddMembersTap: () -> Unit,
    onMakeAdmin: (String) -> Unit,
    onRemoveMember: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // Section header
        Text(
            text = "Members",
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // "Add Members" row at top for admins (Requirement 9.5)
        if (isCurrentUserAdmin) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onAddMembersTap)
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Add Members",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Member list
        members.forEach { member ->
            MemberRow(
                member = member,
                isCurrentUserAdmin = isCurrentUserAdmin,
                onMakeAdmin = { onMakeAdmin(member.userId) },
                onRemoveMember = { onRemoveMember(member.userId) }
            )
        }
    }
}

@Composable
private fun MemberRow(
    member: GroupMemberUiModel,
    isCurrentUserAdmin: Boolean,
    onMakeAdmin: () -> Unit,
    onRemoveMember: () -> Unit
) {
    var showOverflowMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 40dp avatar with online indicator (Requirement 9.11)
        ConversationAvatar(
            name = member.displayName,
            photoUrl = member.photoUrl,
            isOnline = member.isOnline,
            size = 40.dp,
            indicatorSize = 10.dp,
            indicatorBorderWidth = 1.5.dp
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Name + Admin chip
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = member.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                // "Admin" chip (Requirement 9.3)
                if (member.isAdmin) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Admin",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // Overflow menu for non-admin members (Requirement 9.4)
        if (isCurrentUserAdmin && !member.isAdmin) {
            Box {
                IconButton(
                    onClick = { showOverflowMenu = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options for ${member.displayName}",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DropdownMenu(
                    expanded = showOverflowMenu,
                    onDismissRequest = { showOverflowMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Make Admin") },
                        onClick = {
                            showOverflowMenu = false
                            onMakeAdmin()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Remove from Group",
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            showOverflowMenu = false
                            onRemoveMember()
                        }
                    )
                }
            }
        }
    }
}

// ── Customize Chat Section ───────────────────────────────────────────────────

@Composable
private fun CustomizeChatSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // Section header
        Text(
            text = "Customize Chat",
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Group name edit
        SectionListItem(
            icon = Icons.Default.Edit,
            label = "Edit Group Name",
            onClick = { /* placeholder */ }
        )

        // Group photo change
        SectionListItem(
            icon = Icons.Default.PhotoCamera,
            label = "Change Group Photo",
            onClick = { /* placeholder */ }
        )

        // Theme color
        SectionListItem(
            icon = Icons.Default.Palette,
            label = "Theme Color",
            onClick = { /* placeholder */ }
        )
    }
}

// ── Shared Media Section ─────────────────────────────────────────────────────

@Composable
private fun SharedMediaSection(
    media: List<MediaThumbnail>,
    onSeeAllTap: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // Section header with "See All" action
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Shared Media",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            TextButton(onClick = onSeeAllTap) {
                Text("See All")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Horizontal thumbnail row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 8.dp)
        ) {
            items(media) { thumbnail ->
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
    }
}

// ── Invite Link Section ──────────────────────────────────────────────────────

@Composable
private fun InviteLinkSection(
    inviteLink: String,
    context: Context
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // Section header
        Text(
            text = "Invite Link",
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Link text
        Text(
            text = inviteLink,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Copy + Share buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Copy button
            TextButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Invite Link", inviteLink))
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

            // Share button
            TextButton(
                onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, inviteLink)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share Invite Link"))
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
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
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
