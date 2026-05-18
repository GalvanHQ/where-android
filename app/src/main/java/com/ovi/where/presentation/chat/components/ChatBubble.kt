package com.ovi.where.presentation.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ovi.where.presentation.model.BubbleDirection
import com.ovi.where.presentation.model.MessageUiModel

/**
 * Material 3 styled chat bubble composable with Messenger-style grouped corners.
 *
 * When consecutive messages are from the same sender (within 2 min threshold),
 * bubbles use adaptive corner radii to create a visually connected group:
 * - Single message: full rounded with tail
 * - First in group: rounded top, tight bottom on sender side
 * - Middle in group: tight corners on sender side
 * - Last in group: tight top on sender side, tail at bottom
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
    themeColor: Color? = null,
    modifier: Modifier = Modifier
) {
    val isSent = message.direction == BubbleDirection.SENT
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val maxBubbleWidth = screenWidth * 0.75f

    // Messenger-style adaptive corner radii
    val bubbleShape = computeBubbleShape(
        isSent = isSent,
        isFirstInGroup = isFirstInGroup,
        isLastInGroup = isLastInGroup
    )

    // Bubble colors — use themeColor for sent bubbles if provided
    val backgroundColor = if (isSent) {
        themeColor ?: MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val textColor = if (isSent) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    // Resolve sender name: show "Unknown" when blank (Requirement 2.4)
    val resolvedSenderName = message.senderName.ifBlank { "Unknown" }

    // Spacing between grouped messages is tighter than between groups
    val topPadding = if (isFirstInGroup) 0.dp else 2.dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = topPadding)
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
                    start = 36.dp,
                    bottom = 2.dp
                )
            )
        }

        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = if (isSent) Arrangement.End else Arrangement.Start,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Sender avatar for last received bubble in group chats (Messenger style)
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
                        bottom = 8.dp
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
                }
            }
        }

        // Messenger-style: timestamp + status shown outside bubble, only after time gaps
        if (message.showTimestamp) {
            Row(
                modifier = Modifier.padding(
                    start = if (!isSent && isGroupChat) 36.dp else 0.dp,
                    top = 2.dp
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    text = message.formattedTime,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp
                    ),
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
}

/**
 * Computes Messenger-style bubble shape based on direction and group position.
 *
 * Corner logic (18dp = full round, 4dp = tight/connected):
 *
 * SENT (right-aligned):
 * - Single:  [18, 18, 18, 4]  — tail bottom-right
 * - First:   [18, 18, 18, 4]  — rounded top, tight bottom-right
 * - Middle:  [18, 4, 18, 4]   — tight right side
 * - Last:    [18, 4, 18, 4]   — tight top-right, tail bottom-right
 *
 * RECEIVED (left-aligned):
 * - Single:  [18, 18, 4, 18]  — tail bottom-left
 * - First:   [18, 18, 4, 18]  — rounded top, tight bottom-left
 * - Middle:  [4, 18, 4, 18]   — tight left side
 * - Last:    [4, 18, 4, 18]   — tight top-left, tail bottom-left
 */
internal fun computeBubbleShape(
    isSent: Boolean,
    isFirstInGroup: Boolean = true,
    isLastInGroup: Boolean = true
): RoundedCornerShape {
    val full = 18.dp
    val tight = 4.dp

    val isSingle = isFirstInGroup && isLastInGroup
    val isMiddle = !isFirstInGroup && !isLastInGroup

    return when {
        isSent && isSingle -> RoundedCornerShape(full, full, tight, full)
        isSent && isFirstInGroup -> RoundedCornerShape(full, full, tight, full)
        isSent && isMiddle -> RoundedCornerShape(full, tight, tight, full)
        isSent && isLastInGroup -> RoundedCornerShape(full, tight, tight, full)
        // Received
        !isSent && isSingle -> RoundedCornerShape(full, full, full, tight)
        !isSent && isFirstInGroup -> RoundedCornerShape(full, full, full, tight)
        !isSent && isMiddle -> RoundedCornerShape(tight, full, full, tight)
        !isSent && isLastInGroup -> RoundedCornerShape(tight, full, full, tight)
        else -> RoundedCornerShape(full)
    }
}
