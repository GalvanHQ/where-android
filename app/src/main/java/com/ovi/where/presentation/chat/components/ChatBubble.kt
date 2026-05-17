package com.ovi.where.presentation.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ovi.where.domain.model.MessageStatus
import com.ovi.where.presentation.model.BubbleDirection
import com.ovi.where.presentation.model.MessageUiModel

/**
 * Material 3 styled chat bubble composable.
 *
 * Renders sent messages right-aligned with primary color background and white text,
 * and received messages left-aligned with surfaceContainerHigh background and onSurface text.
 * Applies asymmetric corner radii to create a "tail" effect on the last bubble in a group.
 *
 * Timestamp and status indicator are displayed inline at the bottom-end of the bubble
 * in a compact row for a modern, clean chat UX.
 *
 * Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.8, 4.9, 2.2, 2.3, 2.4
 */
@Composable
fun ChatBubble(
    message: MessageUiModel,
    isGroupChat: Boolean,
    isFirstInGroup: Boolean,
    isLastInGroup: Boolean,
    showSenderAvatar: Boolean,
    modifier: Modifier = Modifier
) {
    val isSent = message.direction == BubbleDirection.SENT
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val maxBubbleWidth = screenWidth * 0.75f

    // Corner radius: 18dp all corners, 4dp on tail corner for last bubble in group
    val bubbleShape = computeBubbleShape(isSent = isSent, isLastInGroup = isLastInGroup)

    // Bubble colors
    val backgroundColor = if (isSent) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val textColor = if (isSent) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val metaColor = if (isSent) {
        Color.White.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    // Resolve sender name: show "Unknown" when blank (Requirement 2.4)
    val resolvedSenderName = message.senderName.ifBlank { "Unknown" }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Chat message from $resolvedSenderName" },
        horizontalAlignment = if (isSent) Alignment.End else Alignment.Start
    ) {
        // Sender name label above received bubbles in group chats (Requirement 2.2, 2.3, 2.4)
        if (!isSent && isGroupChat && isFirstInGroup) {
            Text(
                text = resolvedSenderName,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold
                ),
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
            // Sender avatar for first received bubble in group chats (Requirement 4.8)
            if (!isSent && isGroupChat) {
                if (isFirstInGroup && showSenderAvatar) {
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
                    // Reserve space for alignment when avatar is not shown
                    Spacer(modifier = Modifier.width(36.dp))
                }
            }

            // Bubble content
            Surface(
                shape = bubbleShape,
                color = backgroundColor,
                modifier = Modifier.widthIn(max = maxBubbleWidth)
            ) {
                Column(
                    modifier = Modifier.padding(
                        start = 12.dp,
                        end = 12.dp,
                        top = 8.dp,
                        bottom = 6.dp
                    )
                ) {
                    // Message text
                    if (message.text.isNotEmpty()) {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor
                        )
                    }

                    // Timestamp + Status indicator row — aligned to end
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        // Formatted time (e.g. "2:32 PM")
                        Text(
                            text = message.formattedTime,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp
                            ),
                            color = metaColor
                        )

                        // Status indicator for sent messages only
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
        }
    }
}

/**
 * Computes the bubble shape based on direction and group position.
 *
 * - Intermediate bubbles (not last in group): 18dp all corners
 * - Last bubble in group: 18dp on all corners except the tail corner (4dp)
 *   - Sent: tail at bottom-right
 *   - Received: tail at bottom-left
 *
 * Requirements: 4.3, 4.7
 */
internal fun computeBubbleShape(isSent: Boolean, isLastInGroup: Boolean): RoundedCornerShape {
    val fullRadius = 18.dp
    val tailRadius = 4.dp

    return if (!isLastInGroup) {
        // Intermediate bubble: all corners 18dp
        RoundedCornerShape(fullRadius)
    } else if (isSent) {
        // Last sent bubble: tail at bottom-right
        RoundedCornerShape(
            topStart = fullRadius,
            topEnd = fullRadius,
            bottomStart = fullRadius,
            bottomEnd = tailRadius
        )
    } else {
        // Last received bubble: tail at bottom-left
        RoundedCornerShape(
            topStart = fullRadius,
            topEnd = fullRadius,
            bottomStart = tailRadius,
            bottomEnd = fullRadius
        )
    }
}
