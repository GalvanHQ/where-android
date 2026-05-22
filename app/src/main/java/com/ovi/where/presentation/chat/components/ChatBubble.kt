package com.ovi.where.presentation.chat.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ovi.where.core.theme.AvatarColors
import com.ovi.where.presentation.model.BubbleDirection
import com.ovi.where.presentation.model.MessageUiModel

// ─── Layout constants ────────────────────────────────────────────────────────
private val AvatarSize = 28.dp
private val AvatarTrack = 36.dp           // avatar size + spacing — keeps received text aligned
private val BubbleVerticalGap = 2.dp      // tight gap between consecutive bubbles in a group
private val BubbleGroupGap = 8.dp         // larger gap between message groups
private val FullCorner = 18.dp
private val TightCorner = 6.dp
private val BubbleHorizontalPadding = 14.dp
private val BubbleVerticalPadding = 9.dp
private val ReactionOverlap = 20.dp       // how far reaction badges overlap the bubble

/**
 * Unified, professional chat bubble — handles text, image, voice, and location messages
 * with Messenger / Telegram-grade polish.
 *
 * Highlights:
 *   - Smooth press feedback (subtle scale-down on press)
 *   - Messenger-style grouped corners that adapt to first / middle / last in group
 *   - Per-sender colored sender name in groups (Telegram-style)
 *   - Reaction badges that overlap the bubble corner
 *   - Failed messages get a soft error border + retry-on-tap affordance
 *   - Read receipts only render under the LAST sent bubble in a group
 *   - Timestamp + status indicator stay outside the bubble for clean readability
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
    onReactionLongPress: () -> Unit = {},
    onBubbleTap: () -> Unit = {},
    isTapped: Boolean = false,
    onAvatarClick: () -> Unit = {},
    onVoicePlayPause: (() -> Unit)? = null,
    onVoiceSeek: ((Float) -> Unit)? = null,
    isVoicePlaying: Boolean = false,
    voiceProgress: Float = 0f,
    voiceCurrentPositionMs: Long = 0L,
    modifier: Modifier = Modifier
) {
    val isSent = message.direction == BubbleDirection.SENT
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val maxBubbleWidth = screenWidth * 0.78f
    val isFailed = message.status == com.ovi.where.domain.model.MessageStatus.FAILED

    val bubbleShape = computeBubbleShape(
        isSent = isSent,
        isFirstInGroup = isFirstInGroup,
        isLastInGroup = isLastInGroup
    )

    val sentBubbleColor = themeColor ?: MaterialTheme.colorScheme.primary
    val receivedBubbleColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val backgroundColor = if (isSent) sentBubbleColor else receivedBubbleColor
    val textColor = if (isSent) Color.White else MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = if (isSent) {
        Color.White.copy(alpha = 0.78f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val resolvedSenderName = message.senderName.ifBlank { "Unknown" }

    // Tighter spacing: minimal gap inside a group, larger gap between groups.
    val topPadding = when {
        isFirstInGroup && !isSent && isGroupChat -> BubbleGroupGap
        isFirstInGroup -> BubbleGroupGap
        else -> BubbleVerticalGap
    }

    // Press-down scale feedback for tactile, professional feel.
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = 90),
        label = "bubblePressScale"
    )

    // Full-screen image viewer state (preserved from previous impl).
    var showFullScreen by remember { mutableStateOf(false) }
    var fullScreenUrl by remember { mutableStateOf("") }
    var fullScreenUrls by remember { mutableStateOf(emptyList<String>()) }
    var fullScreenInitialIndex by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = topPadding)
            .semantics { contentDescription = "Chat message from $resolvedSenderName" },
        horizontalAlignment = if (isSent) Alignment.End else Alignment.Start
    ) {
        // ── Sender name (group chats, received, first in group) ────────────
        // Telegram-style: per-sender colored name for quick visual scanning.
        if (!isSent && isGroupChat && isFirstInGroup) {
            Text(
                text = resolvedSenderName,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                ),
                color = senderNameColor(message.senderId),
                modifier = Modifier.padding(start = AvatarTrack, bottom = 3.dp)
            )
        }

        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = if (isSent) Arrangement.End else Arrangement.Start,
            modifier = Modifier.fillMaxWidth()
        ) {
            // ── Avatar (group chats, received, last bubble) ────────────────
            if (!isSent && isGroupChat) {
                if (isLastInGroup && showSenderAvatar) {
                    ConversationAvatar(
                        name = resolvedSenderName,
                        photoUrl = message.senderPhotoUrl,
                        isOnline = false,
                        size = AvatarSize,
                        indicatorSize = 0.dp,
                        indicatorBorderWidth = 0.dp,
                        modifier = Modifier
                            .size(AvatarSize)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .clickable { onAvatarClick() }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                } else {
                    Spacer(modifier = Modifier.width(AvatarTrack))
                }
            }

            // ── Bubble + reactions overlay container ───────────────────────
            Box {
                val bubbleModifier = Modifier.scale(scale)

                // Bubble content based on message type.
                when {
                    // IMAGE COLLAGE (multiple consecutive images)
                    message.isImage && message.imageCollageUrls.size > 1 -> {
                        Box(modifier = bubbleModifier) {
                            ImageCollageGrid(
                                imageUrls = message.imageCollageUrls,
                                onImageTap = { url ->
                                    fullScreenUrl = url
                                    fullScreenUrls = message.imageCollageUrls
                                    fullScreenInitialIndex = message.imageCollageUrls
                                        .indexOf(url).coerceAtLeast(0)
                                    showFullScreen = true
                                },
                                onLongPress = onLongPress
                            )
                        }
                    }

                    // SINGLE IMAGE
                    message.isImage -> {
                        Box(modifier = bubbleModifier) {
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
                    }

                    // VOICE
                    message.isVoice && message.voiceDurationMs != null -> {
                        Surface(
                            shape = bubbleShape,
                            color = backgroundColor,
                            modifier = bubbleModifier
                                .widthIn(max = maxBubbleWidth, min = 220.dp)
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
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Note: LOCATION and LIVE_LOCATION are no longer rendered
                    // as bubbles. This is a location-first app, so a map
                    // bubble inside chat duplicates information that's already
                    // surfaced via:
                    //   1. A SYSTEM info line ("X shared their location" /
                    //      "X started sharing their live location") authored
                    //      when the action happens.
                    //   2. The persistent meetup sheet, header pill, FAB
                    //      countdown, and the global friends map.
                    // Historical LOCATION / LIVE_LOCATION rows are coerced
                    // into the system-message branch by the toUiModel mapper.

                    // TEXT (default)
                    else -> {
                        val errorBorder = if (isFailed) {
                            Modifier.border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                                shape = bubbleShape
                            )
                        } else Modifier

                        Surface(
                            shape = bubbleShape,
                            color = backgroundColor,
                            modifier = bubbleModifier
                                .widthIn(max = maxBubbleWidth)
                                .then(errorBorder)
                                .clip(bubbleShape)
                                .combinedClickable(
                                    interactionSource = interactionSource,
                                    indication = androidx.compose.foundation.LocalIndication.current,
                                    onClick = { if (isFailed) onRetry() else onBubbleTap() },
                                    onLongClick = { onLongPress() }
                                )
                        ) {
                            Column(
                                modifier = Modifier.padding(
                                    horizontal = BubbleHorizontalPadding,
                                    vertical = BubbleVerticalPadding
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
                                        val mentionRegex = Regex("""@\w+""")
                                        val ranges = mentionRegex.findAll(message.text)
                                            .map { it.range }
                                            .toList()
                                        val styledText = buildMentionAnnotatedString(
                                            text = message.text,
                                            mentionRanges = ranges,
                                            primaryColor = if (isSent) Color.White
                                            else MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = styledText,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontSize = 15.sp,
                                                lineHeight = 20.sp
                                            ),
                                            color = textColor
                                        )
                                    } else {
                                        LinkableText(
                                            text = message.text,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontSize = 15.sp,
                                                lineHeight = 20.sp
                                            ),
                                            color = textColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Reactions: pinned to the bubble's bottom-corner, overlapping ─
                if (message.reactions.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(if (isSent) Alignment.BottomStart else Alignment.BottomEnd)
                            .offset(
                                x = if (isSent) 8.dp else (-8).dp,
                                y = ReactionOverlap
                            )
                    ) {
                        ReactionBadges(
                            reactions = message.reactions,
                            onReactionTap = onReactionTap,
                            onLongPress = onReactionLongPress
                        )
                    }
                }
            }
        }

        // Add bottom space when reactions overlap so the next message doesn't collide.
        if (message.reactions.isNotEmpty()) {
            Spacer(Modifier.height(ReactionOverlap + 4.dp))
        }

        // ── Link preview (proper placement below bubble) ─────────────────
        if (message.hasLinkPreview && message.linkPreviewUrl != null && message.linkPreviewTitle != null) {
            LinkPreviewCard(
                url = message.linkPreviewUrl,
                title = message.linkPreviewTitle,
                description = message.linkPreviewDescription,
                imageUrl = message.linkPreviewImageUrl,
                domain = message.linkPreviewDomain ?: "",
                modifier = Modifier
                    .widthIn(max = maxBubbleWidth)
                    .padding(
                        top = 4.dp,
                        start = if (!isSent && isGroupChat) AvatarTrack else 0.dp
                    )
            )
        }

        // ── Read receipts: only on the LAST sent bubble in a group ───────
        // (handled by the unified MessageDeliveryIndicator below)

        // ── Failure helper text ──────────────────────────────────────────
        if (isSent && isFailed) {
            Text(
                text = "Tap to retry",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 2.dp, end = 2.dp)
            )
        }

        // ── Delivery indicator only (no per-bubble timestamp) ─────────────
        // Messenger shows timestamps in the centered separator, not per-bubble.
        // Only the last sent message shows status/receipt indicator.
        // Tapping any sent bubble reveals its status inline.
        if (message.showReadReceipt || message.showStatusIndicator || (isTapped && isSent)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when {
                    message.showReadReceipt -> {
                        ReadReceiptIndicator(
                            readBy = message.readBy,
                            readByPhotoUrls = message.readByPhotoUrls,
                            direction = message.direction
                        )
                    }
                    else -> {
                        MessageStatusIndicator(
                            status = message.status,
                            direction = message.direction
                        )
                    }
                }
            }
        }
    }

    // Full-screen image viewer.
    if (showFullScreen && fullScreenUrl.isNotBlank()) {
        FullScreenImageViewer(
            imageUrl = fullScreenUrl,
            onDismiss = { showFullScreen = false },
            imageUrls = fullScreenUrls.ifEmpty { listOf(fullScreenUrl) },
            initialIndex = fullScreenInitialIndex
        )
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

/**
 * Telegram-style per-sender accent color for the sender name above received bubbles.
 * Deterministic — same userId always gets the same hue.
 */
private fun senderNameColor(senderId: String): Color {
    if (senderId.isBlank()) return AvatarColors.first()
    val index = senderId.hashCode().and(0x7FFFFFFF) % AvatarColors.size
    return AvatarColors[index]
}

/**
 * Computes Messenger-style bubble shape based on direction and group position.
 *
 * - Outer corners (away from sender side): always full radius
 * - Inner corners (toward sender side): tight when grouped with adjacent bubble
 */
internal fun computeBubbleShape(
    isSent: Boolean,
    isFirstInGroup: Boolean = true,
    isLastInGroup: Boolean = true
): RoundedCornerShape {
    val full = FullCorner
    val tight = TightCorner
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
 * Compact, with an accent bar + sender name + truncated quoted text.
 *
 * - For sent bubbles: white-tinted translucent background to set it off from the bubble fill
 * - For received bubbles: primary-tinted background for the same separation effect
 */
@Composable
private fun ReplyQuoteBlock(
    senderName: String,
    text: String,
    isSent: Boolean,
    onClick: () -> Unit = {}
) {
    val accentColor = if (isSent) {
        Color.White
    } else {
        MaterialTheme.colorScheme.primary
    }
    val nameColor = if (isSent) Color.White else MaterialTheme.colorScheme.primary
    val textColor = if (isSent) {
        Color.White.copy(alpha = 0.85f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val backgroundTint = if (isSent) {
        Color.White.copy(alpha = 0.18f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundTint)
            .clickable(onClick = onClick)
            .padding(start = 0.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)
    ) {
        // Vertical accent bar at the leading edge.
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(36.dp)
                .background(accentColor, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.padding(end = 4.dp)) {
            if (senderName.isNotBlank()) {
                Text(
                    text = senderName,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                    color = nameColor,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = textColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
    Spacer(modifier = Modifier.height(6.dp))
}
