package com.ovi.where.presentation.chat.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ovi.where.core.utils.LocalReducedMotion
import com.ovi.where.presentation.model.BubbleDirection

/**
 * Animation constants for message send/receive animations.
 *
 * Requirements: 23.1, 23.2, 23.3, 23.4, 23.5, 23.6, 23.7
 */
object MessageAnimationConstants {
    /** Sent message slide-up distance in dp (Requirement 23.1). */
    const val SENT_SLIDE_UP_DP = 48f

    /** Sent message animation duration in ms (Requirement 23.1). */
    const val SENT_ANIMATION_DURATION_MS = 200

    /** Received message slide-in distance from left in dp (Requirement 23.2). */
    const val RECEIVED_SLIDE_LEFT_DP = 32f

    /** Received message animation duration in ms (Requirement 23.2). */
    const val RECEIVED_ANIMATION_DURATION_MS = 250

    /** Auto-scroll animation duration in ms (Requirement 23.3). */
    const val AUTO_SCROLL_DURATION_MS = 300

    /** Distance threshold for auto-scroll in dp (Requirement 23.3, 23.4). */
    const val AUTO_SCROLL_THRESHOLD_DP = 150f

    /** Status indicator crossfade duration in ms (Requirement 23.5). */
    const val STATUS_CROSSFADE_DURATION_MS = 150

    /** Reaction picker scale-up animation duration in ms (Requirement 23.6). */
    const val REACTION_PICKER_DURATION_MS = 200

    /** Reaction picker initial scale (Requirement 23.6). */
    const val REACTION_PICKER_INITIAL_SCALE = 0.8f
}

/**
 * Wraps a message bubble with entrance animation based on direction.
 *
 * - Sent messages: slide up 48dp + fade-in 0→100%, 200ms decelerate easing (Requirement 23.1)
 * - Received messages: slide in 32dp from left + fade-in 0→100%, 250ms decelerate easing (Requirement 23.2)
 * - Reduced motion: skip all slide animations, apply instantly (Requirement 23.7)
 *
 * @param messageId Unique message ID used as key for animation state.
 * @param direction Whether the message is SENT or RECEIVED.
 * @param isNewMessage Whether this message just appeared (triggers animation).
 * @param content The message bubble composable content.
 */
@Composable
fun AnimatedMessageBubble(
    messageId: String,
    direction: BubbleDirection,
    isNewMessage: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val reducedMotion = LocalReducedMotion.current

    // If reduced motion is enabled, skip all animations (Requirement 23.7)
    if (reducedMotion || !isNewMessage) {
        Box(modifier = modifier) {
            content()
        }
        return
    }

    val alpha = remember(messageId) { Animatable(0f) }
    val offsetY = remember(messageId) {
        Animatable(
            if (direction == BubbleDirection.SENT) MessageAnimationConstants.SENT_SLIDE_UP_DP else 0f
        )
    }
    val offsetX = remember(messageId) {
        Animatable(
            if (direction == BubbleDirection.RECEIVED) -MessageAnimationConstants.RECEIVED_SLIDE_LEFT_DP else 0f
        )
    }

    val durationMs = if (direction == BubbleDirection.SENT) {
        MessageAnimationConstants.SENT_ANIMATION_DURATION_MS
    } else {
        MessageAnimationConstants.RECEIVED_ANIMATION_DURATION_MS
    }

    LaunchedEffect(messageId) {
        // Animate fade-in
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = durationMs,
                easing = FastOutSlowInEasing
            )
        )
    }

    LaunchedEffect(messageId) {
        // Animate slide (Y for sent, X for received)
        if (direction == BubbleDirection.SENT) {
            offsetY.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = durationMs,
                    easing = FastOutSlowInEasing
                )
            )
        } else {
            offsetX.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = durationMs,
                    easing = FastOutSlowInEasing
                )
            )
        }
    }

    Box(
        modifier = modifier
            .alpha(alpha.value)
            .offset {
                // offsetX.value and offsetY.value are in dp units
                IntOffset(
                    x = offsetX.value.dp.roundToPx(),
                    y = offsetY.value.dp.roundToPx()
                )
            }
    ) {
        content()
    }
}
