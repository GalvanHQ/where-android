package com.ovi.where.presentation.chat.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ovi.where.presentation.model.BubbleDirection
import com.ovi.where.presentation.model.MessageUiModel

/**
 * Image chat bubble with Messenger-style grouping.
 *
 * Consecutive image messages from the same sender stack tightly (2dp gap)
 * with no repeated sender name or avatar — only the last image in the group
 * shows the avatar and timestamp. Tap opens full-screen viewer.
 */
@Composable
fun ImageChatBubble(
    message: MessageUiModel,
    isGroupChat: Boolean,
    isFirstInGroup: Boolean,
    isLastInGroup: Boolean,
    showSenderAvatar: Boolean,
    onLongPress: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSent = message.direction == BubbleDirection.SENT
    val resolvedSenderName = message.senderName.ifBlank { "Unknown" }
    val topPadding = if (isFirstInGroup) 0.dp else 2.dp

    // Full-screen image viewer state
    var showFullScreen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = topPadding),
        horizontalAlignment = if (isSent) Alignment.End else Alignment.Start
    ) {
        // Sender name only on first in group (group chats, received)
        if (!isSent && isGroupChat && isFirstInGroup) {
            Text(
                text = resolvedSenderName,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = if (showSenderAvatar) 36.dp else 0.dp,
                    bottom = 2.dp
                )
            )
        }

        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = if (isSent) Arrangement.End else Arrangement.Start,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Avatar on last bubble only (Messenger style)
            if (!isSent && isGroupChat) {
                if (isLastInGroup && showSenderAvatar) {
                    ConversationAvatar(
                        name = resolvedSenderName,
                        photoUrl = message.senderPhotoUrl,
                        isOnline = false,
                        size = 28.dp,
                        indicatorSize = 0.dp,
                        indicatorBorderWidth = 0.dp,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                } else {
                    Spacer(modifier = Modifier.width(36.dp))
                }
            }

            // The image bubble — tap to view full screen, long press for actions
            ImageMessageBubble(
                imageUrl = message.imageUrl,
                thumbnailUrl = message.thumbnailUrl,
                uploadProgress = message.uploadProgress,
                status = message.status,
                onRetry = onRetry,
                modifier = Modifier.pointerInput(message.id) {
                    detectTapGestures(
                        onTap = {
                            if (!message.imageUrl.isNullOrBlank()) {
                                showFullScreen = true
                            }
                        },
                        onLongPress = { onLongPress() }
                    )
                }
            )
        }

        // Timestamp outside bubble, only after time gaps
        if (message.showTimestamp) {
            Row(
                modifier = Modifier.padding(
                    start = if (!isSent && isGroupChat) 36.dp else 0.dp,
                    top = 2.dp
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = message.formattedTime,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isSent) {
                    Spacer(modifier = Modifier.width(3.dp))
                    MessageStatusIndicator(
                        status = message.status,
                        direction = message.direction
                    )
                }
            }
        }
    }

    // Full-screen image viewer dialog
    if (showFullScreen && !message.imageUrl.isNullOrBlank()) {
        FullScreenImageViewer(
            imageUrl = message.imageUrl!!,
            onDismiss = { showFullScreen = false }
        )
    }
}
