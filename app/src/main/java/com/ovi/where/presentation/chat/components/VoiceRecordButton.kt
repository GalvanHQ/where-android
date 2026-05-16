package com.ovi.where.presentation.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ovi.where.core.theme.Dimens
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Microphone button with gesture handling for voice recording.
 *
 * Gestures:
 * - Long-press (300ms): starts recording
 * - Slide left > 100dp: cancel recording
 * - Slide up > 48dp: lock into hands-free mode
 * - Normal release (< 20dp movement): stop recording and send
 *
 * Requirements: 11.1, 11.2, 11.3, 11.4, 11.5, 11.11
 */
@Composable
fun VoiceRecordButton(
    isRecording: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    onLockRecording: () -> Unit,
    showTooltip: Boolean,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val cancelThresholdPx = with(density) { 100.dp.toPx() }
    val lockThresholdPx = with(density) { 48.dp.toPx() }
    val normalReleaseThresholdPx = with(density) { 20.dp.toPx() }

    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var hasLocked by remember { mutableStateOf(false) }

    // Scale animation when recording
    val scale by animateFloatAsState(
        targetValue = if (isRecording && !hasLocked) 1.4f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "micScale"
    )

    // Lock icon opacity based on upward drag
    val lockIconAlpha by animateFloatAsState(
        targetValue = if (isRecording && !hasLocked) {
            (abs(offsetY) / lockThresholdPx).coerceIn(0f, 1f)
        } else 0f,
        animationSpec = tween(durationMillis = 100),
        label = "lockAlpha"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        // Lock icon shown above the button during recording
        AnimatedVisibility(
            visible = isRecording && !hasLocked && lockIconAlpha > 0.1f,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-48).dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Slide up to lock",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = lockIconAlpha),
                modifier = Modifier.size(Dimens.iconSizeMedium)
            )
        }

        // Microphone button
        FloatingActionButton(
            onClick = { /* Handled by gesture detector */ },
            modifier = Modifier
                .size(44.dp)
                .scale(scale)
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            isDragging = true
                            hasLocked = false
                            offsetX = 0f
                            offsetY = 0f
                            onStartRecording()
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            offsetX += dragAmount.x
                            offsetY += dragAmount.y

                            // Check for cancel (slide left > 100dp)
                            if (offsetX < -cancelThresholdPx) {
                                isDragging = false
                                offsetX = 0f
                                offsetY = 0f
                                onCancelRecording()
                            }

                            // Check for lock (slide up > 48dp)
                            if (offsetY < -lockThresholdPx && !hasLocked) {
                                hasLocked = true
                                isDragging = false
                                offsetX = 0f
                                offsetY = 0f
                                onLockRecording()
                            }
                        },
                        onDragEnd = {
                            isDragging = false
                            val totalMovement = abs(offsetX) + abs(offsetY)
                            offsetX = 0f
                            offsetY = 0f

                            if (!hasLocked) {
                                // Normal release: stop and send if movement < 20dp
                                if (totalMovement < normalReleaseThresholdPx) {
                                    onStopRecording()
                                } else {
                                    // Moved but not enough to cancel or lock — still stop
                                    onStopRecording()
                                }
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                            offsetX = 0f
                            offsetY = 0f
                            if (!hasLocked) {
                                onCancelRecording()
                            }
                        }
                    )
                }
                .semantics { contentDescription = "Hold to record voice message" },
            shape = CircleShape,
            containerColor = if (isRecording) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (isRecording) {
                MaterialTheme.colorScheme.onError
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 2.dp,
                pressedElevation = 4.dp
            )
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
                modifier = Modifier.size(Dimens.iconSizeMedium)
            )
        }

        // "Hold to record" tooltip (Requirement 11.5)
        AnimatedVisibility(
            visible = showTooltip,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-56).dp)
        ) {
            TooltipBubble(text = "Hold to record")
        }
    }
}

/**
 * Small tooltip bubble displayed above the microphone button.
 */
@Composable
private fun TooltipBubble(text: String) {
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        tonalElevation = 4.dp
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.inverseOnSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
