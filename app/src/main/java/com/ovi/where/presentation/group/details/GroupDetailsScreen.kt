package com.ovi.where.presentation.group.details

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.ovi.where.R
import com.ovi.where.core.common.UiEvent
import com.ovi.where.core.theme.AvatarColors
import com.ovi.where.core.theme.Dimens
import com.ovi.where.core.utils.IntentUtils
import com.ovi.where.core.utils.showToast
import com.ovi.where.presentation.common.WhereTopAppBar
import com.ovi.where.presentation.model.GroupMemberUiModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailsScreen(
    groupId: String,
    onNavigateBack: () -> Unit,
    onNavigateToMap: () -> Unit,
    onNavigateToChat: (String) -> Unit = {},
    onNavigateToEditGroup: () -> Unit = {},
    onNavigateToUserProfile: (String) -> Unit = {},
    viewModel: GroupDetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showMenu by remember { mutableStateOf(false) }
    var showLeaveDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var memberToKick by remember { mutableStateOf<GroupMemberUiModel?>(null) }

    LaunchedEffect(groupId) {
        viewModel.loadGroupDetails(groupId)
        viewModel.observeMembers(groupId)
    }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is UiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message.asString(context))
                is UiEvent.NavigateBack -> onNavigateBack()
                else -> Unit
            }
        }
    }

    val shareTitle = stringResource(R.string.action_share_invite_code)
    val shareMessage = stringResource(R.string.msg_share_group_invite, uiState.groupName, uiState.inviteCode)
    val inviteCodeCopied = stringResource(R.string.toast_invite_code_copied)

    // Scroll behavior for collapsing top app bar (Requirement 5.1)
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    // ── Confirmation Dialogs ────────────────────────────────────────────────────

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text(stringResource(R.string.action_leave_group)) },
            text = { Text(stringResource(R.string.msg_confirm_leave_group)) },
            confirmButton = {
                TextButton(onClick = {
                    showLeaveDialog = false
                    viewModel.leaveGroup(groupId)
                }) {
                    Text(
                        stringResource(R.string.action_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.action_delete_group)) },
            text = { Text(stringResource(R.string.msg_confirm_delete_group)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deleteGroup(groupId)
                }) {
                    Text(
                        stringResource(R.string.action_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    memberToKick?.let { member ->
        AlertDialog(
            onDismissRequest = { memberToKick = null },
            title = { Text(stringResource(R.string.action_kick_member)) },
            text = { Text(stringResource(R.string.msg_confirm_kick_member, member.displayName)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.kickMember(groupId, member.userId)
                    memberToKick = null
                }) {
                    Text(
                        stringResource(R.string.action_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { memberToKick = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // ── Screen Content ──────────────────────────────────────────────────────────

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            WhereTopAppBar(
                title = uiState.group?.name ?: stringResource(R.string.title_group_details),
                onNavigateBack = onNavigateBack,
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = onNavigateToMap) {
                        Icon(Icons.Default.Map, contentDescription = stringResource(R.string.cd_view_map))
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_more_options))
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            if (uiState.inviteCode.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_copy_invite_code)) },
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(uiState.inviteCode))
                                        context.showToast(inviteCodeCopied)
                                        showMenu = false
                                    },
                                    leadingIcon = { Icon(Icons.Default.ContentCopy, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_share_invite_code)) },
                                    onClick = {
                                        context.startActivity(IntentUtils.createShareIntent(context, shareTitle, shareMessage))
                                        showMenu = false
                                    },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Send, null) }
                                )
                            }
                            if (uiState.isCurrentUserAdmin) {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_edit_group)) },
                                    onClick = { showMenu = false; onNavigateToEditGroup() },
                                    leadingIcon = { Icon(Icons.Default.Edit, null) }
                                )
                            }
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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

            uiState.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = uiState.error ?: stringResource(R.string.error_loading_group),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(Dimens.spaceMedium))
                        TextButton(onClick = { viewModel.loadGroupDetails(groupId) }) {
                            Text(stringResource(R.string.action_retry))
                        }
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(bottom = Dimens.spaceXLarge)
                ) {
                    // ── Group Header (Avatar, Name, Description) ─────────────
                    item {
                        GroupDetailsHeader(
                            groupName = uiState.groupName,
                            groupDescription = uiState.groupDescription,
                            groupAvatarUrl = uiState.groupAvatarUrl
                        )
                    }

                    // ── Group Chat Button ────────────────────────────────────
                    item {
                        val conversationId = uiState.groupConversationId
                        androidx.compose.material3.FilledTonalButton(
                            onClick = {
                                if (conversationId != null) onNavigateToChat(conversationId)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Dimens.spaceLarge),
                            shape = MaterialTheme.shapes.medium,
                            enabled = conversationId != null
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = null,
                                modifier = Modifier.size(Dimens.iconSizeMedium)
                            )
                            Spacer(Modifier.width(Dimens.spaceSmall))
                            Text("Group Chat")
                        }
                        Spacer(Modifier.height(Dimens.spaceMedium))
                    }

                    // ── Invite Code Card ─────────────────────────────────────
                    if (uiState.inviteCode.isNotEmpty()) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Dimens.spaceLarge),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                shape = MaterialTheme.shapes.large
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(Dimens.spaceLarge)
                                ) {
                                    Text(
                                        text = stringResource(R.string.title_invite_code),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(Modifier.height(Dimens.spaceSmall))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = uiState.inviteCode,
                                            style = MaterialTheme.typography.headlineMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Row {
                                            IconButton(onClick = {
                                                clipboardManager.setText(AnnotatedString(uiState.inviteCode))
                                                context.showToast(inviteCodeCopied)
                                            }) {
                                                Icon(
                                                    Icons.Default.ContentCopy,
                                                    contentDescription = stringResource(R.string.cd_copy),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            IconButton(onClick = {
                                                context.startActivity(
                                                    IntentUtils.createShareIntent(context, shareTitle, shareMessage)
                                                )
                                            }) {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.Send,
                                                    contentDescription = stringResource(R.string.action_share),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = stringResource(R.string.msg_share_invite_instruction),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    )
                                }
                            }
                            Spacer(Modifier.height(Dimens.spaceMedium))
                        }
                    }

                    // ── Admin Actions (Delete Group) ─────────────────────────
                    if (uiState.isCurrentUserAdmin) {
                        item {
                            AdminActionsSection(
                                onDeleteGroup = { showDeleteDialog = true }
                            )
                        }
                    }

                    // ── Leave Group ──────────────────────────────────────────
                    item {
                        LeaveGroupSection(
                            isSoleAdmin = uiState.isSoleAdmin,
                            onLeaveGroup = { showLeaveDialog = true }
                        )
                    }

                    // ── Members Header ───────────────────────────────────────
                    item {
                        Text(
                            text = stringResource(R.string.msg_members_count, uiState.members.size),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(
                                horizontal = Dimens.spaceLarge,
                                vertical = Dimens.spaceMedium
                            )
                        )
                    }

                    // ── Member List ──────────────────────────────────────────
                    items(
                        items = uiState.members,
                        key = { it.id }
                    ) { member ->
                        GroupDetailsMemberItem(
                            member = member,
                            isCurrentUser = member.userId == viewModel.currentUserId,
                            isCurrentUserAdmin = uiState.isCurrentUserAdmin,
                            onKick = { memberToKick = member },
                            onPromote = { viewModel.promoteMember(groupId, member.userId) },
                            onTap = { onNavigateToUserProfile(member.userId) }
                        )
                    }

                    // ── Shared Media Gallery ────────────────────────────────
                    if (uiState.sharedMedia.isNotEmpty()) {
                        item {
                            SharedMediaSection(
                                mediaUrls = uiState.sharedMedia.mapNotNull { it.imageUrl }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Group Details Header ────────────────────────────────────────────────────────

@Composable
private fun GroupDetailsHeader(
    groupName: String,
    groupDescription: String,
    groupAvatarUrl: String?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimens.spaceXLarge),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Avatar (96dp)
        if (!groupAvatarUrl.isNullOrEmpty()) {
            AsyncImage(
                model = groupAvatarUrl,
                contentDescription = "Group avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(Dimens.avatarSizeXLarge)
                    .clip(CircleShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(Dimens.avatarSizeXLarge)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = groupName.take(1).uppercase(),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.height(Dimens.spaceLarge))

        // Name
        Text(
            text = groupName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        // Description
        if (groupDescription.isNotBlank()) {
            Spacer(Modifier.height(Dimens.spaceMedium))
            Text(
                text = groupDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Admin Actions Section ───────────────────────────────────────────────────────

@Composable
private fun AdminActionsSection(
    onDeleteGroup: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spaceLarge)
    ) {
        HorizontalDivider(
            thickness = Dimens.dividerThickness,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
        ListItem(
            headlineContent = {
                Text(
                    text = stringResource(R.string.action_delete_group),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium
                )
            },
            leadingContent = {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            modifier = Modifier.clickable(onClick = onDeleteGroup),
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
        )
    }
}

// ── Leave Group Section ─────────────────────────────────────────────────────────

@Composable
private fun LeaveGroupSection(
    isSoleAdmin: Boolean,
    onLeaveGroup: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spaceLarge)
    ) {
        HorizontalDivider(
            thickness = Dimens.dividerThickness,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
        ListItem(
            headlineContent = {
                Text(
                    text = stringResource(R.string.action_leave_group),
                    color = if (isSoleAdmin)
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    else
                        MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium
                )
            },
            supportingContent = if (isSoleAdmin) {
                {
                    Text(
                        text = stringResource(R.string.msg_sole_admin_cannot_leave),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            } else null,
            leadingContent = {
                Icon(
                    Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = null,
                    tint = if (isSoleAdmin)
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    else
                        MaterialTheme.colorScheme.error
                )
            },
            modifier = Modifier.clickable(enabled = !isSoleAdmin, onClick = onLeaveGroup),
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
        )
        HorizontalDivider(
            thickness = Dimens.dividerThickness,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    }
}

// ── Member Item ─────────────────────────────────────────────────────────────────

@Composable
private fun GroupDetailsMemberItem(
    member: GroupMemberUiModel,
    isCurrentUser: Boolean,
    isCurrentUserAdmin: Boolean,
    onKick: () -> Unit,
    onPromote: () -> Unit,
    onTap: () -> Unit
) {
    val color = AvatarColors[member.userId.hashCode().and(0x7FFFFFFF) % AvatarColors.size]
    var showMenu by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isCurrentUser) "${member.displayName} (you)" else member.displayName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (member.isAdmin) {
                    Icon(
                        Icons.Default.AdminPanelSettings, null,
                        modifier = Modifier.size(Dimens.iconSizeXSmall),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(Dimens.spaceSmall))
                }
                Text(
                    text = member.roleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (member.isAdmin) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(Dimens.avatarSizeMedium)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = member.displayName.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.surface
                )
            }
        },
        trailingContent = {
            if (isCurrentUserAdmin && !isCurrentUser) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Member actions")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        if (!member.isAdmin) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_promote_admin)) },
                                onClick = {
                                    showMenu = false
                                    onPromote()
                                },
                                leadingIcon = { Icon(Icons.Default.AdminPanelSettings, null) }
                            )
                        }
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.action_kick_member),
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                showMenu = false
                                onKick()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.PersonRemove, null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        )
                    }
                }
            }
        },
        modifier = Modifier
            .clickable(onClick = onTap)
            .padding(horizontal = Dimens.spaceMedium),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
    )
}

// ── Shared Media Gallery ────────────────────────────────────────────────────────

@Composable
private fun SharedMediaSection(
    mediaUrls: List<String>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Dimens.spaceLarge)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.spaceLarge),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.label_shared_media),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            TextButton(onClick = { /* See All action placeholder */ }) {
                Text(stringResource(R.string.action_see_all))
            }
        }

        Spacer(Modifier.height(Dimens.spaceMedium))

        LazyRow(
            contentPadding = PaddingValues(horizontal = Dimens.spaceLarge),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMedium)
        ) {
            items(mediaUrls.take(20)) { url ->
                AsyncImage(
                    model = url,
                    contentDescription = "Shared media",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(Dimens.cornerSmall))
                )
            }
        }
    }
}
