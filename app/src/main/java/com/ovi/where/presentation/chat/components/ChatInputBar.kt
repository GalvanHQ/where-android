package com.ovi.where.presentation.chat.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Material 3 chat input bar — WhatsApp-style with:
 * - Rounded text field with camera + gallery icons
 * - Morphing FAB: Mic (empty) ↔ Send (has text)
 * - Voice recording overlay with waveform, timer, cancel/send
 * - Smooth animated transitions between all states
 *
 * Requirements: 5.1-5.6, 11.1-11.5
 */
@Composable
fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onCameraTap: () -> Unit,
    onAttachmentTap: () -> Unit,
    // Voice recording
    isVoiceRecording: Boolean = false,
    voiceRecordingDurationMs: Long = 0L,
    voiceWaveformAmplitudes: List<Float> = emptyList(),
    onVoiceRecordStart: () -> Unit = {},
    onVoiceRecordStop: () -> Unit = {},
    onVoiceRecordCancel: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val hasText = text.isNotBlank()

    // Calculate max height for 5 lines
    val textStyle = MaterialTheme.typography.bodyLarge
    val lineHeightDp = with(LocalDensity.current) {
        (textStyle.lineHeight.value * density).toDp()
    }
    val maxFieldHeight = remember(lineHeightDp) { lineHeightDp * 5 + 16.dp }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        if (isVoiceRecording) {
            // ── Voice Recording Mode ──────────────────────────────────────
            VoiceRecordingBar(
                durationMs = voiceRecordingDurationMs,
                waveformAmplitudes = voiceWaveformAmplitudes,
                onCancel = onVoiceRecordCancel,
                onStop = onVoiceRecordStop
            )
        } else {
            // ── Normal Input Mode ─────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                // Rounded text field container
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // Text input
                        BasicTextField(
                            value = text,
                            onValueChange = onTextChange,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 40.dp, max = maxFieldHeight)
                                .padding(vertical = 8.dp),
                            textStyle = textStyle.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            maxLines = 5,
                            decorationBox = { innerTextField ->
                                Box(
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (text.isEmpty()) {
                                        Text(
                                            text = "Message",
                                            style = textStyle,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )

                        // Trailing icons: camera + attachment (always visible)
                        AnimatedVisibility(
                            visible = !hasText,
                            enter = fadeIn(tween(150)),
                            exit = fadeOut(tween(150))
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = onAttachmentTap,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AttachFile,
                                        contentDescription = "Attach photo",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                IconButton(
                                    onClick = onCameraTap,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CameraAlt,
                                        contentDescription = "Camera",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))

                // ── Morphing FAB: Mic ↔ Send ──────────────────────────────
                AnimatedContent(
                    targetState = hasText,
                    transitionSpec = {
                        (scaleIn(tween(200)) + fadeIn(tween(200)))
                            .togetherWith(scaleOut(tween(150)) + fadeOut(tween(150)))
                            .using(SizeTransform(clip = false))
                    },
                    label = "fab_morph"
                ) { showSend ->
                    FloatingActionButton(
                        onClick = {
                            if (showSend) onSend() else onVoiceRecordStart()
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .semantics {
                                contentDescription = if (showSend) "Send message" else "Record voice"
                            },
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        elevation = FloatingActionButtonDefaults.elevation(
                            defaultElevation = 2.dp,
                            pressedElevation = 4.dp
                        )
                    ) {
                        Icon(
                            imageVector = if (showSend) Icons.AutoMirrored.Filled.Send else Icons.Filled.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Voice recording overlay bar — replaces normal input during recording.
 * Shows: cancel button | recording indicator + timer + waveform | stop/send button
 */
@Composable
private fun VoiceRecordingBar(
    durationMs: Long,
    waveformAmplitudes: List<Float>,
    onCancel: () -> Unit,
    onStop: () -> Unit
) {
    val seconds = (durationMs / 1000).toInt()
    val minutes = seconds / 60
    val secs = seconds % 60
    val timerText = "%d:%02d".format(minutes, secs)

    // Pulsing recording dot
    val pulseAlpha by animateFloatAsState(
        targetValue = if (seconds % 2 == 0) 1f else 0.4f,
        animationSpec = tween(500),
        label = "pulse"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Cancel button
        IconButton(
            onClick = onCancel,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Cancel recording",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(Modifier.width(4.dp))

        // Recording indicator + timer
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            // Pulsing red dot
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = pulseAlpha))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = timerText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(12.dp))

            // Mini waveform visualization
            WaveformVisualization(
                amplitudes = waveformAmplitudes,
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
            )
        }

        Spacer(Modifier.width(8.dp))

        // Stop & send button
        FloatingActionButton(
            onClick = onStop,
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = "Stop and send voice message" },
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 2.dp,
                pressedElevation = 4.dp
            )
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * Simple waveform visualization — renders amplitude bars.
 */
@Composable
private fun WaveformVisualization(
    amplitudes: List<Float>,
    modifier: Modifier = Modifier
) {
    val barColor = MaterialTheme.colorScheme.primary
    val maxBars = 32

    // Take the last N amplitudes to show recent waveform
    val displayAmps = if (amplitudes.size > maxBars) {
        amplitudes.takeLast(maxBars)
    } else {
        amplitudes
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        displayAmps.forEach { amp ->
            val barHeight = (amp.coerceIn(0.05f, 1f) * 28).dp
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(barHeight)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(barColor.copy(alpha = 0.7f + amp * 0.3f))
            )
        }
    }
}
