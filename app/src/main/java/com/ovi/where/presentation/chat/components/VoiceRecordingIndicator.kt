package com.ovi.where.presentation.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ovi.where.core.theme.Dimens

/**
 * Displays the voice recording indicator with elapsed time and waveform visualization.
 *
 * Shown when the user is actively recording a voice message.
 * Includes:
 * - Pulsing red recording dot
 * - Elapsed time in "m:ss" format
 * - Waveform visualization from amplitude data
 * - "Slide left to cancel" hint (when not locked)
 * - Stop and Send buttons (when locked in hands-free mode)
 *
 * Requirements: 11.1, 11.2, 11.3
 */
@Composable
fun VoiceRecordingIndicator(
    isVisible: Boolean,
    durationMs: Long,
    waveformAmplitudes: List<Float>,
    isLocked: Boolean,
    onStop: () -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.spaceLarge, vertical = Dimens.spaceMedium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isLocked) {
                    // Hands-free mode: show cancel, waveform, timer, stop, send
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .size(36.dp)
                            .semantics { contentDescription = "Cancel recording" }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(Modifier.width(Dimens.spaceMedium))

                    // Waveform
                    WaveformVisualization(
                        amplitudes = waveformAmplitudes,
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                    )

                    Spacer(Modifier.width(Dimens.spaceMedium))

                    // Timer
                    RecordingTimer(durationMs = durationMs)

                    Spacer(Modifier.width(Dimens.spaceMedium))

                    // Stop button
                    IconButton(
                        onClick = onStop,
                        modifier = Modifier
                            .size(36.dp)
                            .semantics { contentDescription = "Stop recording" }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(Modifier.width(Dimens.spaceSmall))

                    // Send button
                    IconButton(
                        onClick = onSend,
                        modifier = Modifier
                            .size(36.dp)
                            .semantics { contentDescription = "Send voice message" }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    // Normal recording mode: pulsing dot, timer, waveform, slide hint
                    PulsingRecordingDot()

                    Spacer(Modifier.width(Dimens.spaceMedium))

                    RecordingTimer(durationMs = durationMs)

                    Spacer(Modifier.width(Dimens.spaceMedium))

                    // Waveform
                    WaveformVisualization(
                        amplitudes = waveformAmplitudes,
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                    )

                    Spacer(Modifier.width(Dimens.spaceMedium))

                    // Slide to cancel hint
                    Text(
                        text = "◀ Slide to cancel",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Pulsing red dot indicating active recording.
 */
@Composable
private fun PulsingRecordingDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "recordingPulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier
            .size(12.dp)
            .background(
                color = Color.Red.copy(alpha = alpha),
                shape = CircleShape
            )
            .semantics { contentDescription = "Recording in progress" }
    )
}

/**
 * Displays the recording duration in "m:ss" format.
 */
@Composable
private fun RecordingTimer(durationMs: Long) {
    val totalSeconds = (durationMs / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val formattedTime = "$minutes:${seconds.toString().padStart(2, '0')}"

    Text(
        text = formattedTime,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.semantics {
            contentDescription = "Recording duration: $minutes minutes $seconds seconds"
        }
    )
}

/**
 * Waveform visualization drawn from normalized amplitude samples.
 * Each amplitude is rendered as a vertical bar.
 */
@Composable
fun WaveformVisualization(
    amplitudes: List<Float>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary
) {
    Canvas(modifier = modifier) {
        if (amplitudes.isEmpty()) return@Canvas

        val barWidth = 3.dp.toPx()
        val barSpacing = 2.dp.toPx()
        val totalBarWidth = barWidth + barSpacing
        val maxBars = (size.width / totalBarWidth).toInt()
        val displayAmplitudes = if (amplitudes.size > maxBars) {
            amplitudes.takeLast(maxBars)
        } else {
            amplitudes
        }

        val centerY = size.height / 2f
        val maxBarHeight = size.height * 0.8f
        val minBarHeight = 2.dp.toPx()

        displayAmplitudes.forEachIndexed { index, amplitude ->
            val barHeight = (amplitude * maxBarHeight).coerceAtLeast(minBarHeight)
            val x = index * totalBarWidth + barWidth / 2f
            val topY = centerY - barHeight / 2f
            val bottomY = centerY + barHeight / 2f

            drawLine(
                color = barColor,
                start = Offset(x, topY),
                end = Offset(x, bottomY),
                strokeWidth = barWidth
            )
        }
    }
}
