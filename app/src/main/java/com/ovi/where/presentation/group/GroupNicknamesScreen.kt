package com.ovi.where.presentation.group

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ovi.where.core.theme.Dimens
import com.ovi.where.core.utils.showToast
import com.ovi.where.presentation.chat.GroupInfoViewModel
import com.ovi.where.presentation.chat.components.ConversationAvatar
import com.ovi.where.presentation.common.WhereTopAppBar
import com.ovi.where.presentation.model.GroupMemberUiModel

/**
 * Group Nicknames screen — shows all group members with their nicknames.
 * Tapping a member opens an edit dialog to set/change their nickname.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupNicknamesScreen(
    onNavigateBack: () -> Unit,
    viewModel: GroupInfoViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var editingMember by remember { mutableStateOf<GroupMemberUiModel?>(null) }
    var editingNickname by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            WhereTopAppBar(
                title = "Nicknames",
                onNavigateBack = onNavigateBack
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            items(uiState.members, key = { it.userId }) { member ->
                val nickname = uiState.nicknames[member.userId]
                NicknameRow(
                    member = member,
                    nickname = nickname,
                    onClick = {
                        editingMember = member
                        editingNickname = nickname ?: ""
                    }
                )
            }
        }
    }

    // Edit nickname dialog
    editingMember?.let { member ->
        AlertDialog(
            onDismissRequest = { editingMember = null },
            title = { Text("Edit nickname") },
            text = {
                Column {
                    Text(
                        text = "${member.displayName} will only see this in this conversation.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextField(
                        value = editingNickname,
                        onValueChange = { editingNickname = it },
                        singleLine = true,
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
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateNickname(member.userId, editingNickname)
                    context.showToast("Nickname updated")
                    editingMember = null
                }) {
                    Text("Set")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingMember = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun NicknameRow(
    member: GroupMemberUiModel,
    nickname: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.spaceLarge, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ConversationAvatar(
            name = member.displayName,
            photoUrl = member.photoUrl,
            isOnline = member.isOnline,
            size = 40.dp,
            indicatorSize = 0.dp,
            indicatorBorderWidth = 0.dp
        )

        Spacer(Modifier.width(12.dp))

        Column {
            Text(
                text = nickname ?: "Set nickname",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (nickname != null) FontWeight.Medium else FontWeight.Normal,
                color = if (nickname != null) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = member.displayName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
