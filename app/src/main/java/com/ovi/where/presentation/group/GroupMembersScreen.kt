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
    groupId: String,
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
                    if (uiState.isCurrentUserAdmin) {
                        IconButton(onClick = onNavigateToAddMembers) {
                            Icon(
                                Icons.Default.PersonAdd,
                                contentDescription = "Add members",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
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
                        isCurrentUserAdmin = uiState.isCurrentUserAdmin,
                        onClick = {
                            if (member.userId == currentUserId) onNavigateToProfile()
                            else onNavigateToUserProfile(member.userId)
                        },
                        onMakeAdmin = {
                            viewModel.makeAdmin(member.userId)
                            context.showToast("${member.displayName} is now admin")
                        },
                        onRemoveMember = {
                            viewModel.removeMember(member.userId)
                            context.showToast("${member.displayName} removed")
                        }
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
                        isCurrentUserAdmin = uiState.isCurrentUserAdmin,
                        onClick = {
                            if (member.userId == currentUserId) onNavigateToProfile()
                            else onNavigateToUserProfile(member.userId)
                        },
                        onMakeAdmin = {
                            viewModel.makeAdmin(member.userId)
                            context.showToast("${member.displayName} is now admin")
                        },
                        onRemoveMember = {
                            viewModel.removeMember(member.userId)
                            context.showToast("${member.displayName} removed")
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MemberListRow(
    member: GroupMemberUiModel,
    isCurrentUserAdmin: Boolean,
    onClick: () -> Unit,
    onMakeAdmin: () -> Unit,
    onRemoveMember: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

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
            Text(
                text = member.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (member.username.isNotBlank()) "@${member.username} • ${member.roleText}"
                       else member.roleText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Admin actions for non-admin members
        if (isCurrentUserAdmin && !member.isAdmin) {
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Options",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Make Admin") },
                        onClick = { showMenu = false; onMakeAdmin() }
                    )
                    DropdownMenuItem(
                        text = { Text("Remove", color = MaterialTheme.colorScheme.error) },
                        onClick = { showMenu = false; onRemoveMember() }
                    )
                }
            }
        }
    }
}
