package com.ovi.where.presentation.group.details

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.ExitToApp
// TopBar map icon → Icons.Default.Map (more accurate)
import androidx.compose.material.icons.filled.LocationOn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
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
    viewModel: GroupDetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showMenu by remember { mutableStateOf(false) }
    var showLeaveDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

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

    // Confirmation dialogs
    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text(stringResource(R.string.action_leave_group)) },
            text = { Text(stringResource(R.string.msg_confirm_leave_group)) },
            confirmButton = {
                TextButton(onClick = {
                    showLeaveDialog = false
                    viewModel.leaveGroup(groupId)
                }) { Text(stringResource(R.string.action_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) { Text(stringResource(R.string.action_cancel)) }
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
                }) { Text(stringResource(R.string.action_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    Scaffold(
        topBar = {
            WhereTopAppBar(
                title = uiState.group?.name ?: stringResource(R.string.title_group_details),
                onNavigateBack = onNavigateBack,
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
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_delete_group), color = MaterialTheme.colorScheme.error) },
                                    onClick = { showMenu = false; showDeleteDialog = true },
                                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_leave_group), color = MaterialTheme.colorScheme.error) },
                                onClick = { showMenu = false; showLeaveDialog = true },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null, tint = MaterialTheme.colorScheme.error) }
                            )
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
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }

            uiState.error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.error_loading_group),
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

            uiState.group != null -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    verticalArrangement = Arrangement.spacedBy(Dimens.spaceSmall)
                ) {
                    // Invite code card
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Dimens.spaceLarge),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(Dimens.spaceLarge)
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
                                            context.startActivity(IntentUtils.createShareIntent(context, shareTitle, shareMessage))
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
                    }

                    // Group Chat button
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
                    }

                    // Members header
                    item {
                        Text(
                            text = stringResource(R.string.msg_members_count, uiState.members.size),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = Dimens.spaceLarge, vertical = Dimens.spaceSmall)
                        )
                    }

                    // Member items
                    items(items = uiState.members, key = { it.id }) { member ->
                        MemberItem(
                            member = member,
                            isCurrentUser = member.userId == viewModel.currentUserId,
                            isCurrentUserAdmin = uiState.isCurrentUserAdmin,
                            onKick = { viewModel.kickMember(groupId, member.userId) },
                            onPromote = { viewModel.promoteMember(groupId, member.userId) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Dimens.spaceLarge)
                        )
                    }

                    item { Spacer(Modifier.height(Dimens.spaceLarge)) }
                }
            }
        }
    }
}

private val memberColors = AvatarColors

@Composable
fun MemberItem(
    member: GroupMemberUiModel,
    isCurrentUser: Boolean,
    isCurrentUserAdmin: Boolean,
    onKick: () -> Unit,
    onPromote: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = memberColors[member.userId.hashCode().and(0x7FFFFFFF) % memberColors.size]
    var showMenu by remember { mutableStateOf(false) }
    var showKickDialog by remember { mutableStateOf(false) }

    if (showKickDialog) {
        AlertDialog(
            onDismissRequest = { showKickDialog = false },
            title = { Text(stringResource(R.string.action_kick_member)) },
            text = { Text(stringResource(R.string.msg_confirm_kick_member, member.displayName)) },
            confirmButton = {
                TextButton(onClick = {
                    showKickDialog = false
                    onKick()
                }) { Text(stringResource(R.string.action_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showKickDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(Dimens.cardElevationSubtle),
        shape     = MaterialTheme.shapes.medium
    ) {
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
                        color = MaterialTheme.colorScheme.surface  // on-colour for avatar
                    )
                }
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (member.isSharingLocation) {
                        Icon(
                            Icons.Default.LocationOn, null,
                            modifier = Modifier.size(Dimens.iconSizeSmall),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(Modifier.width(Dimens.spaceSmall))
                    }
                    if (isCurrentUserAdmin && !isCurrentUser) {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, null)
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                if (!member.isAdmin) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_promote_admin)) },
                                        onClick = { showMenu = false; onPromote() },
                                        leadingIcon = { Icon(Icons.Default.AdminPanelSettings, null) }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_kick_member), color = MaterialTheme.colorScheme.error) },
                                    onClick = { showMenu = false; showKickDialog = true },
                                    leadingIcon = { Icon(Icons.Default.PersonRemove, null, tint = MaterialTheme.colorScheme.error) }
                                )
                            }
                        }
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
        )
    }
}
