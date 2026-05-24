package com.ovi.where.presentation.group

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ovi.where.core.theme.Dimens
import com.ovi.where.core.utils.showToast
import com.ovi.where.presentation.chat.GroupInfoViewModel
import com.ovi.where.presentation.chat.components.ConversationAvatar
import com.ovi.where.presentation.common.WhereTopAppBar
import com.ovi.where.presentation.model.GroupMemberUiModel

/**
 * Group Members screen — shows all members with admin management.
 * Tapping a member navigates to their profile.
 * Admins can make others admin or remove members via overflow menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupMembersScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAddMembers: () -> Unit,
    onNavigateToUserProfile: (String) -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    viewModel: GroupInfoViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val currentUserId = viewModel.currentUserId

    Scaffold(
        topBar = {
            WhereTopAppBar(
                title = "Members (${uiState.memberCount})",
                onNavigateBack = onNavigateBack,
                actions = {
                    // Any group member can invite others — admin gating removed
                    // to match the action button in [GroupInfoScreen].
                    IconButton(onClick = onNavigateToAddMembers) {
                        Icon(
                            Icons.Default.PersonAdd,
                            contentDescription = "Add members",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val admins = uiState.members.filter { it.isAdmin }
            val members = uiState.members.filter { !it.isAdmin }

            if (admins.isNotEmpty()) {
                item {
                    Text(
                        text = "Admins",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            start = Dimens.spaceLarge,
                            top = Dimens.spaceLarge,
                            bottom = Dimens.spaceMedium
                        )
                    )
                }
                items(admins, key = { it.userId }) { member ->
                    MemberListRow(
                        member = member,
                        isCurrentUser = member.userId == currentUserId,
                        isCurrentUserAdmin = uiState.isCurrentUserAdmin,
                        onlyAdmin = admins.size == 1,
                        onClick = {
                            if (member.userId == currentUserId) onNavigateToProfile()
                            else onNavigateToUserProfile(member.userId)
                        },
                        onMakeAdmin = { viewModel.makeAdmin(member.userId) },
                        onDemoteAdmin = { viewModel.demoteAdmin(member.userId) },
                        onRemoveMember = { viewModel.removeMember(member.userId) }
                    )
                }
            }

            if (members.isNotEmpty()) {
                item {
                    Text(
                        text = "Members",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            start = Dimens.spaceLarge,
                            top = Dimens.spaceXLarge,
                            bottom = Dimens.spaceMedium
                        )
                    )
                }
                items(members, key = { it.userId }) { member ->
                    MemberListRow(
                        member = member,
                        isCurrentUser = member.userId == currentUserId,
                        isCurrentUserAdmin = uiState.isCurrentUserAdmin,
                        onlyAdmin = false,
                        onClick = {
                            if (member.userId == currentUserId) onNavigateToProfile()
                            else onNavigateToUserProfile(member.userId)
                        },
                        onMakeAdmin = { viewModel.makeAdmin(member.userId) },
                        onDemoteAdmin = { viewModel.demoteAdmin(member.userId) },
                        onRemoveMember = { viewModel.removeMember(member.userId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MemberListRow(
    member: GroupMemberUiModel,
    isCurrentUser: Boolean,
    isCurrentUserAdmin: Boolean,
    onlyAdmin: Boolean,
    onClick: () -> Unit,
    onMakeAdmin: () -> Unit,
    onDemoteAdmin: () -> Unit,
    onRemoveMember: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var pendingAction by remember {
        mutableStateOf<PendingAdminAction?>(null)
    }
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.spaceLarge, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ConversationAvatar(
            name = member.displayName,
            photoUrl = member.photoUrl,
            isOnline = member.isOnline,
            size = 48.dp,
            indicatorSize = 12.dp,
            indicatorBorderWidth = 2.dp
        )

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isCurrentUser) "${member.displayName} (You)" else member.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (member.isAdmin) {
                    Spacer(Modifier.width(8.dp))
                    AdminChip()
                }
            }
            if (member.username.isNotBlank()) {
                Text(
                    text = "@${member.username}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Admin actions — only visible when the local user is admin AND
        // the row isn't the local user themselves. Self-management
        // (leave / promote-someone-else-then-step-down) belongs on the
        // group info screen, not the member row.
        val showOverflow = isCurrentUserAdmin && !isCurrentUser
        if (showOverflow) {
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Options for ${member.displayName}",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    if (member.isAdmin) {
                        // Last-admin guard: never let an admin demote the
                        // sole remaining admin — the group would be left
                        // ungoverned. Disabled menu item makes the rule
                        // visible instead of silently failing.
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (onlyAdmin) "Last admin (can't demote)" else "Remove admin"
                                )
                            },
                            enabled = !onlyAdmin,
                            onClick = {
                                showMenu = false
                                if (!onlyAdmin) {
                                    pendingAction = PendingAdminAction.Demote
                                }
                            }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Make admin") },
                            onClick = {
                                showMenu = false
                                pendingAction = PendingAdminAction.Promote
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Remove from group", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            pendingAction = PendingAdminAction.Remove
                        }
                    )
                }
            }
        }
    }

    // ── Confirm sheets ────────────────────────────────────────────────────
    // Make-admin and demote are quick, non-destructive — single-confirm
    // dialog inside the sheet feels right. Remove is destructive and gets
    // the full DestructiveConfirmSheet with consequence bullets.
    when (pendingAction) {
        PendingAdminAction.Promote -> {
            com.ovi.where.presentation.chat.components.DestructiveConfirmSheet(
                title = "Make ${member.displayName} an admin?",
                message = "Admins can change group info, add or remove members, and promote others.",
                consequences = emptyList(),
                confirmLabel = "Make admin",
                photoUrl = member.photoUrl,
                icon = Icons.Default.PersonAdd,
                onConfirm = {
                    onMakeAdmin()
                    context.showToast("${member.displayName} is now admin")
                    pendingAction = null
                },
                onDismiss = { pendingAction = null }
            )
        }
        PendingAdminAction.Demote -> {
            com.ovi.where.presentation.chat.components.DestructiveConfirmSheet(
                title = "Remove ${member.displayName} as admin?",
                message = "They'll go back to a regular member and lose admin permissions.",
                consequences = emptyList(),
                confirmLabel = "Remove admin",
                photoUrl = member.photoUrl,
                icon = Icons.Default.MoreVert,
                onConfirm = {
                    onDemoteAdmin()
                    context.showToast("${member.displayName} is no longer admin")
                    pendingAction = null
                },
                onDismiss = { pendingAction = null }
            )
        }
        PendingAdminAction.Remove -> {
            com.ovi.where.presentation.chat.components.DestructiveConfirmSheet(
                title = "Remove ${member.displayName}?",
                message = "They'll lose access to this group's chat, location updates, and meetups.",
                consequences = listOf(
                    "Other members see a 'removed' system message",
                    "${member.displayName} can rejoin only with a new invite",
                    "Their messages stay in the chat history"
                ),
                confirmLabel = "Remove",
                photoUrl = member.photoUrl,
                icon = Icons.Default.MoreVert,
                onConfirm = {
                    onRemoveMember()
                    context.showToast("${member.displayName} removed")
                    pendingAction = null
                },
                onDismiss = { pendingAction = null }
            )
        }
        null -> Unit
    }
}

/**
 * Pending admin action awaiting user confirmation. Holds the row-local
 * state so the confirm sheet can render outside the IconButton-scoped
 * DropdownMenu (which closes on touch).
 */
private enum class PendingAdminAction { Promote, Demote, Remove }

/**
 * Compact "Admin" pill rendered next to the member's display name.
 * Lifts the role out of the username subtitle so it's scannable in a
 * long member list.
 */
@Composable
private fun AdminChip() {
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    ) {
        Text(
            text = "Admin",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}
