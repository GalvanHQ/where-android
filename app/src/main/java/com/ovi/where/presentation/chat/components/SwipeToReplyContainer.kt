package com.ovi.where.presentation.chat.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Container that wraps a message bubble and provides swipe-to-reply gesture support.
 *
 * Behavior:
 * - Detects horizontal left-to-right drag on the message bubble
 * - Shows a reply arrow icon that fades in proportionally to drag distance (full opacity at 48dp)
 * - Limits horizontal displacement to 100dp max
 * - On release beyond 48dp: triggers haptic feedback and invokes [onReply]
 * - On release before 48dp: animates back to original position within 200ms
 * - Suppresses vertical scrolling while swipe is in progress via consuming pointer changes
 *
 * Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7
 *
 * @param onReply Callback invoked when the swipe exceeds the 48dp threshold and is released.
 *               The caller should populate the reply preview bar and focus the input field.
 * @param enabled Whether the swipe gesture is enabled (default true).
 * @param content The message bubble composable to wrap.
 */
@Composable
fun SwipeToReplyContainer(
    onReply: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // Threshold at which reply is triggered (48dp)
    val thresholdPx = with(density) { 48.dp.toPx() }
    // Maximum horizontal displacement (100dp)
    val maxDragPx = with(density) { 100.dp.toPx() }

    // Animatable for smooth offset transitions
    val offsetX = remember { Animatable(0f) }

    // Calculate reply arrow alpha: proportional to drag distance, full at threshold
    val replyIconAlpha = (offsetX.value / thresholdPx).coerceIn(0f, 1f)

    Box(
        contentAlignment = Alignment.CenterStart
    ) {
        // Reply arrow icon — positioned behind the message, fades in with drag
        // Requirement 8.2: Reply arrow icon fading in proportionally to drag distance
        Icon(
            imageVector = Icons.Default.Reply,
            contentDescription = "Reply",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(start = 12.dp)
                .size(24.dp)
                .alpha(replyIconAlpha)
                .semantics { contentDescription = "Swipe to reply" }
        )

        // Message bubble content with horizontal offset
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .then(
                    if (enabled) {
                        Modifier.pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    // Requirement 8.5: Suppress vertical scrolling while swipe in progress
                                    // By consuming horizontal drag events, the parent LazyColumn's
                                    // vertical scroll is suppressed during the gesture.
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    // Consume the change to prevent vertical scroll conflict
                                    // Requirement 8.5: Suppress vertical scrolling
                                    change.consume()

                                    // Requirement 8.2: Limit horizontal displacement to 100dp max
                                    val newOffset = (offsetX.value + dragAmount)
                                        .coerceIn(0f, maxDragPx)
                                    scope.launch {
                                        offsetX.snapTo(newOffset)
                                    }
                                },
                                onDragEnd = {
                                    scope.launch {
                                        if (offsetX.value >= thresholdPx) {
                                            // Requirement 8.3: Trigger haptic feedback on release beyond 48dp
                                            hapticFeedback.performHapticFeedback(
                                                HapticFeedbackType.LongPress
                                            )
                                            // Requirement 8.1, 8.7: Populate reply preview bar
                                            onReply()
                                        }
                                        // Requirement 8.4: Animate back to original position within 200ms
                                        offsetX.animateTo(
                                            targetValue = 0f,
                                            animationSpec = tween(durationMillis = 200)
                                        )
                                    }
                                },
                                onDragCancel = {
                                    // Animate back on cancel as well
                                    scope.launch {
                                        offsetX.animateTo(
                                            targetValue = 0f,
                                            animationSpec = tween(durationMillis = 200)
                                        )
                                    }
                                }
                            )
                        }
                    } else {
                        Modifier
                    }
                )
        ) {
            content()
        }
    }
}
