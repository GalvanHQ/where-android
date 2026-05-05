package com.ovi.where.presentation.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.ovi.where.core.theme.Dimens
import com.ovi.where.domain.model.Message
import com.ovi.where.domain.model.MessageType
import com.ovi.where.presentation.common.WhereTopAppBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: String,
    onNavigateBack: () -> Unit = {},
    onNavigateToUserProfile: (String) -> Unit = {},
    onNavigateToGroupMap: (String) -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val currentUserId = viewModel.currentUserId
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp

    LaunchedEffect(conversationId) {
        viewModel.init(conversationId)
    }

    // Mark read when screen is shown
    LaunchedEffect(uiState.messages.size) {
        viewModel.markRead()
    }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            WhereTopAppBar(
                title = uiState.conversation?.name ?: "Chat",
                onNavigateBack = onNavigateBack,
                actions = {
                    val groupId = uiState.conversation?.groupId
                    if (groupId != null) {
                        IconButton(onClick = { onNavigateToGroupMap(groupId) }) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = "Group Map",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .navigationBarsPadding()
        ) {
            // ── Message list ──────────────────────────────────────────────────
            Box(modifier = Modifier.weight(1f)) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            horizontal = Dimens.spaceLarge,
                            vertical = Dimens.spaceMedium
                        ),
                        verticalArrangement = Arrangement.spacedBy(Dimens.spaceSmall)
                    ) {
                        // Group messages by date
                        val grouped = uiState.messages.groupBy { msg ->
                            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(msg.timestamp))
                        }
                        grouped.forEach { (date, messages) ->
                            item {
                                DateSeparator(date = formatDateHeader(messages.first().timestamp))
                            }
                            items(items = messages, key = { it.id }) { message ->
                                val isSent = message.senderId == currentUserId
                                MessageBubble(
                                    message = message,
                                    isSent = isSent,
                                    showAvatar = !isSent && uiState.conversation?.groupId != null,
                                    maxWidth = screenWidth * 0.75f,
                                    onLocationTap = {
                                        val gId = uiState.conversation?.groupId
                                        if (gId != null) onNavigateToGroupMap(gId)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // ── Typing indicator ──────────────────────────────────────────────
            AnimatedVisibility(
                visible = uiState.typingUserName != null,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                Text(
                    text = "${uiState.typingUserName} is typing…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Dimens.spaceLarge, vertical = Dimens.spaceSmall)
                )
            }

            // ── Input bar ─────────────────────────────────────────────────────
            InputBar(
                text = uiState.inputText,
                onTextChange = viewModel::onInputChange,
                onSend = viewModel::sendMessage,
                onLocationSend = { /* TODO: get current location and call viewModel.sendLocation */ }
            )
        }
    }
}

// ── Message bubble ────────────────────────────────────────────────────────────

@Composable
private fun MessageBubble(
    message: Message,
    isSent: Boolean,
    showAvatar: Boolean,
    maxWidth: androidx.compose.ui.unit.Dp,
    onLocationTap: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isSent) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        // Avatar for received group messages
        if (!isSent && showAvatar) {
            if (!message.senderPhotoUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = message.senderPhotoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(28.dp).clip(CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = message.senderName.take(1).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.width(Dimens.spaceSmall))
        } else if (!isSent) {
            Spacer(Modifier.width(36.dp))
        }

        Column(
            horizontalAlignment = if (isSent) Alignment.End else Alignment.Start
        ) {
            // Sender name for group chat received messages
            if (!isSent && showAvatar) {
                Text(
                    text = message.senderName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = Dimens.spaceSmall, bottom = 2.dp)
                )
            }

            if (message.type == MessageType.LOCATION) {
                // Location message bubble
                Surface(
                    shape = bubbleShape(isSent),
                    color = if (isSent) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .widthIn(max = maxWidth)
                        .clip(bubbleShape(isSent))
                        .run {
                            clickableIf(true) { onLocationTap() }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(Dimens.spaceLarge),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.LocationOn, null,
                            modifier = Modifier.size(Dimens.iconSizeMedium),
                            tint = if (isSent) MaterialTheme.colorScheme.onPrimary
                                   else MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(Dimens.spaceSmall))
                        Column {
                            Text(
                                text = "Shared a location",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isSent) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurface
                            )
                            if (message.latitude != null && message.longitude != null) {
                                Text(
                                    text = "%.4f, %.4f".format(message.latitude, message.longitude),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSent) MaterialTheme.colorScheme.onPrimary.copy(0.7f)
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                // Text message bubble
                Surface(
                    shape = bubbleShape(isSent),
                    color = if (isSent) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.widthIn(max = maxWidth)
                ) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSent) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(
                            horizontal = Dimens.spaceLarge,
                            vertical = Dimens.spaceMedium
                        )
                    )
                }
            }

            // Timestamp
            Text(
                text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Dimens.spaceMedium, vertical = 2.dp)
            )
        }
    }
}

private fun bubbleShape(isSent: Boolean) = if (isSent) {
    RoundedCornerShape(
        topStart = 18.dp, topEnd = 18.dp,
        bottomStart = 18.dp, bottomEnd = 4.dp   // "tail" on sent side
    )
} else {
    RoundedCornerShape(
        topStart = 18.dp, topEnd = 18.dp,
        bottomStart = 4.dp, bottomEnd = 18.dp   // "tail" on received side
    )
}

private fun Modifier.clickableIf(enabled: Boolean, onClick: () -> Unit): Modifier =
    if (enabled) this.clickable { onClick() } else this

@Composable
private fun DateSeparator(date: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.spaceMedium),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = date,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Dimens.spaceLarge, vertical = Dimens.spaceSmall)
            )
        }
    }
}

@Composable
private fun InputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onLocationSend: () -> Unit
) {
    Surface(
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.spaceMedium, vertical = Dimens.spaceSmall),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Location button
            IconButton(onClick = onLocationSend) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = "Send location",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Text field (pill shape)
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = { Text("Message") },
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.extraLarge,
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f)
                )
            )

            Spacer(Modifier.width(Dimens.spaceMedium))

            // Send FAB — only shown when text non-empty
            AnimatedVisibility(
                visible = text.isNotBlank(),
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                FloatingActionButton(
                    onClick = onSend,
                    modifier = Modifier.size(40.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    shape = CircleShape
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "Send",
                        modifier = Modifier.size(Dimens.iconSizeMedium),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

private fun formatDateHeader(timestamp: Long): String {
    val sdf = SimpleDateFormat("EEEE, d MMM", Locale.getDefault())
    return when {
        isToday(timestamp) -> "Today"
        isYesterday(timestamp) -> "Yesterday"
        else -> sdf.format(Date(timestamp))
    }
}

private fun isToday(timestamp: Long): Boolean {
    val now = java.util.Calendar.getInstance()
    val cal = java.util.Calendar.getInstance().apply { time = Date(timestamp) }
    return now.get(java.util.Calendar.DATE) == cal.get(java.util.Calendar.DATE)
}

private fun isYesterday(timestamp: Long): Boolean {
    val now = java.util.Calendar.getInstance()
    val cal = java.util.Calendar.getInstance().apply { time = Date(timestamp) }
    return now.get(java.util.Calendar.DATE) - cal.get(java.util.Calendar.DATE) == 1
}
