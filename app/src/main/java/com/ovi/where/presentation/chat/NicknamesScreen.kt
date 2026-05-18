package com.ovi.where.presentation.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ovi.where.core.utils.showToast
import com.ovi.where.presentation.chat.components.ConversationAvatar
import com.ovi.where.presentation.common.WhereTopAppBar

/**
 * Nicknames screen — Messenger-style.
 *
 * Shows a list of participants with their current nickname (or "Set nickname").
 * Tapping a participant opens an edit dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NicknamesScreen(
    conversationId: String,
    onNavigateBack: () -> Unit,
    viewModel: ConversationInfoViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentUserId = viewModel.currentUserId ?: ""
    val context = LocalContext.current

    val otherUserId = uiState.otherUserId ?: ""
    val otherUserRealName = uiState.conversationTitle
    val otherUserNickname = uiState.nicknames[otherUserId]
    val selfNickname = uiState.nicknames[currentUserId]

    var editingUserId by remember { mutableStateOf<String?>(null) }
    var editingName by remember { mutableStateOf("") }
    var editingRealName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            WhereTopAppBar(
                title = "Nicknames",
                onNavigateBack = onNavigateBack
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Other user row
            NicknameRow(
                photoUrl = uiState.photoUrl,
                displayName = otherUserRealName,
                nickname = otherUserNickname,
                onClick = {
                    editingUserId = otherUserId
                    editingName = otherUserNickname ?: ""
                    editingRealName = otherUserRealName
                }
            )

            // Current user row
            NicknameRow(
                photoUrl = null,
                displayName = "You",
                nickname = selfNickname,
                onClick = {
                    editingUserId = currentUserId
                    editingName = selfNickname ?: ""
                    editingRealName = "You"
                }
            )
        }
    }

    // Edit nickname dialog
    if (editingUserId != null) {
        EditNicknameDialog(
            currentValue = editingName,
            realName = editingRealName,
            onValueChange = { editingName = it },
            onConfirm = {
                editingUserId?.let { uid ->
                    viewModel.updateNickname(uid, editingName)
                    context.showToast("Nickname updated")
                }
                editingUserId = null
            },
            onDismiss = { editingUserId = null }
        )
    }
}

@Composable
private fun NicknameRow(
    photoUrl: String?,
    displayName: String,
    nickname: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ConversationAvatar(
            name = displayName,
            photoUrl = photoUrl,
            isOnline = false,
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
                text = displayName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EditNicknameDialog(
    currentValue: String,
    realName: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit nickname") },
        text = {
            Column {
                Text(
                    text = "$realName will only see this in this conversation.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextField(
                    value = currentValue,
                    onValueChange = onValueChange,
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
            TextButton(onClick = onConfirm) {
                Text("Set")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
