package com.ovi.where.presentation.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ovi.where.presentation.model.BubbleDirection
import com.ovi.where.presentation.model.MessageUiModel

/**
 * Unified chat bubble composable — handles text, image, voice, and location messages.
 * Uses Messenger-style grouped corners for consecutive messages from the same sender.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(
    message: MessageUiModel,
    isGroupChat: Boolean,
    isFirstInGroup: Boolean,
    isLastInGroup: Boolean,
    showSenderAvatar: Boolean,
    themeColor: Color? = null,
    onLongPress: () -> Unit = {},
    onRetry: () -> Unit = {},
    onLocationTap: () -> Unit = {},
    onReplyQuoteTap: (String) -> Unit = {},
    onReactionTap: (String) -> Unit = {},
    onVoicePlayPause: (() -> Unit)? = null,
    onVoiceSeek: ((Float) -> Unit)? = null,
    isVoicePlaying: Boolean = false,
    voiceProgress: Float = 0f,
    voiceCurrentPositionMs: Long = 0L,
    modifier: Modifier = Modifier
) {
    val isSent = message.direction == BubbleDirection.SENT
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val maxBubbleWidth = screenWidth * 0.75f

    val bubbleShape = computeBubbleShape(
        isSent = isSent,
        isFirstInGroup = isFirstInGroup,
        isLastInGroup = isLastInGroup
    )

    val backgroundColor = if (isSent) {
        themeColor ?: MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val textColor = if (isSent) Color.White else MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = if (isSent) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant

    val resolvedSenderName = message.senderName.ifBlank { "Unknown" }
    val topPadding = if (isFirstInGroup) 0.dp else 2.dp

    // Full-screen image viewer state
    var showFullScreen by remember { mutableStateOf(false) }
    var fullScreenUrl by remember { mutableStateOf("") }
    var fullScreenUrls by remember { mutableStateOf(emptyList<String>()) }
    var fullScreenInitialIndex by remember { mutableStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = topPadding)
            .semantics { contentDescription = "Chat message from $resolvedSenderName" },
        horizontalAlignment = if (isSent) Alignment.End else Alignment.Start
    ) {
        // Sender name (group chats, received, first in group)
        if (!isSent && isGroupChat && isFirstInGroup) {
            Text(
                text = resolvedSenderName,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 36.dp, bottom = 2.dp)
            )
        }

        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = if (isSent) Arrangement.End else Arrangement.Start,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Avatar (last bubble only, received, group chat)
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

            // ── Bubble content based on message type ─────────────────────
            when {
                // IMAGE COLLAGE (multiple consecutive images)
                message.isImage && message.imageCollageUrls.size > 1 -> {
                    ImageCollageGrid(
                        imageUrls = message.imageCollageUrls,
                        onImageTap = { url ->
                            fullScreenUrl = url
                            fullScreenUrls = message.imageCollageUrls
                            fullScreenInitialIndex = message.imageCollageUrls.indexOf(url).coerceAtLeast(0)
                            showFullScreen = true
                        },
                        onLongPress = onLongPress
                    )
                }

                // SINGLE IMAGE
                message.isImage -> {
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
                                        fullScreenUrl = message.imageUrl!!
                                        fullScreenUrls = listOf(message.imageUrl!!)
                                        fullScreenInitialIndex = 0
                                        showFullScreen = true
                                    }
                                },
                                onLongPress = { onLongPress() }
                            )
                        }
                    )
                }

                // VOICE
                message.isVoice && message.voiceDurationMs != null -> {
                    Surface(
                        shape = bubbleShape,
                        color = backgroundColor,
                        modifier = Modifier
                            .widthIn(max = maxBubbleWidth, min = 200.dp)
                            .pointerInput(message.id) {
                                detectTapGestures(onLongPress = { onLongPress() })
                            }
                    ) {
                        VoiceMessageBubble(
                            durationMs = message.voiceDurationMs,
                            isPlaying = isVoicePlaying,
                            progress = voiceProgress,
                            currentPositionMs = voiceCurrentPositionMs,
                            onPlayPause = { onVoicePlayPause?.invoke() },
                            onSeek = { p -> onVoiceSeek?.invoke(p) },
                            accentColor = if (isSent) Color.White else MaterialTheme.colorScheme.primary,
                            textColor = secondaryTextColor,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                // LOCATION
                message.isLocation -> {
                    Surface(
                        shape = bubbleShape,
                        color = backgroundColor,
                        modifier = Modifier
                            .widthIn(max = maxBubbleWidth)
                            .clip(bubbleShape)
                            .clickable { onLocationTap() }
                            .pointerInput(message.id) {
                                detectTapGestures(onLongPress = { onLongPress() })
                            }
                    ) {
                        LocationMessageBubble(
                            latitude = message.latitude ?: 0.0,
                            longitude = message.longitude ?: 0.0
                        )
                    }
                }

                // TEXT (default)
                else -> {
                    Surface(
                        shape = bubbleShape,
                        color = backgroundColor,
                        modifier = Modifier
                            .widthIn(max = maxBubbleWidth)
                            .combinedClickable(
                                onClick = { },
                                onLongClick = { onLongPress() }
                            )
                    ) {
                        Column(
                            modifier = Modifier.padding(
                                start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp
                            )
                        ) {
                            // Reply quote block
                            if (message.replyToText != null) {
                                ReplyQuoteBlock(
                                    senderName = message.replyToSenderName ?: "",
                                    text = message.replyToText,
                                    isSent = isSent,
                                    onClick = {
                                        message.replyToId?.let { onReplyQuoteTap(it) }
                                    }
                                )
                            }

                            if (message.text.isNotEmpty()) {
                                if (message.mentionedUserIds.isNotEmpty()) {
                                    MentionStyledText(
                                        text = message.text,
                                        mentionedUserIds = message.mentionedUserIds,
                                        userDisplayNames = emptyMap(),
                                        modifier = Modifier
                                    )
                                } else {
                                    LinkableText(
                                        text = message.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = textColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Link preview outside the Row (proper placement below bubble)
        if (message.hasLinkPreview && message.linkPreviewUrl != null && message.linkPreviewTitle != null) {
            LinkPreviewCard(
                url = message.linkPreviewUrl,
                title = message.linkPreviewTitle,
                description = message.linkPreviewDescription,
                imageUrl = message.linkPreviewImageUrl,
                domain = message.linkPreviewDomain ?: "",
                modifier = Modifier
                    .widthIn(max = maxBubbleWidth)
                    .padding(start = if (!isSent && isGroupChat) 36.dp else 0.dp)
            )
        }

        // Reaction badges below the bubble
        if (message.reactions.isNotEmpty()) {
            ReactionBadges(
                reactions = message.reactions,
                onReactionTap = onReactionTap,
                modifier = Modifier.padding(
                    start = if (!isSent && isGroupChat) 36.dp else 0.dp
                )
            )
        }

        // Read receipt indicator (sent messages with readers)
        if (isSent && message.readBy.isNotEmpty()) {
            ReadReceiptIndicator(
                readBy = message.readBy,
                readByPhotoUrls = message.readByPhotoUrls,
                direction = message.direction,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        // Timestamp outside bubble (only after time gaps)
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

    // Full-screen image viewer
    if (showFullScreen && fullScreenUrl.isNotBlank()) {
        FullScreenImageViewer(
            imageUrl = fullScreenUrl,
            onDismiss = { showFullScreen = false },
            imageUrls = fullScreenUrls.ifEmpty { listOf(fullScreenUrl) },
            initialIndex = fullScreenInitialIndex
        )
    }
}

/**
 * Computes Messenger-style bubble shape based on direction and group position.
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
        !isSent && isSingle -> RoundedCornerShape(full, full, full, tight)
        !isSent && isFirstInGroup -> RoundedCornerShape(full, full, full, tight)
        !isSent && isMiddle -> RoundedCornerShape(tight, full, full, tight)
        !isSent && isLastInGroup -> RoundedCornerShape(tight, full, full, tight)
        else -> RoundedCornerShape(full)
    }
}

/**
 * Reply quote block shown inside the bubble above the message text.
 * Shows a vertical accent bar + sender name + quoted text.
 */
@Composable
private fun ReplyQuoteBlock(
    senderName: String,
    text: String,
    isSent: Boolean,
    onClick: () -> Unit = {}
) {
    val accentColor = if (isSent) {
        Color.White.copy(alpha = 0.6f)
    } else {
        MaterialTheme.colorScheme.primary
    }
    val nameColor = if (isSent) Color.White else MaterialTheme.colorScheme.primary
    val textColor = if (isSent) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(bottom = 6.dp)
    ) {
        // Vertical accent bar
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(36.dp)
                .background(accentColor, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            if (senderName.isNotBlank()) {
                Text(
                    text = senderName,
                    style = MaterialTheme.typography.labelSmall,
                    color = nameColor,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = textColor,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}
