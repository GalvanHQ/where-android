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
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Premium chat input bar with:
 * - Pill-shaped text field with inline attachment icons
 * - Emoji shortcut FAB when set (Messenger-style like button)
 * - Morphing send/mic FAB with smooth transitions
 * - Voice recording overlay with waveform visualization
 */
@Composable
fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onCameraTap: () -> Unit,
    onAttachmentTap: () -> Unit,
    isVoiceRecording: Boolean = false,
    voiceRecordingDurationMs: Long = 0L,
    voiceWaveformAmplitudes: List<Float> = emptyList(),
    onVoiceRecordStart: () -> Unit = {},
    onVoiceRecordStop: () -> Unit = {},
    onVoiceRecordCancel: () -> Unit = {},
    emojiShortcut: String? = null,
    onEmojiShortcutSend: () -> Unit = {},
    themeColor: Color? = null,
    modifier: Modifier = Modifier
) {
    val hasText = text.isNotBlank()
    val accentColor = themeColor ?: MaterialTheme.colorScheme.primary
    val textStyle = MaterialTheme.typography.bodyLarge
    val lineHeightDp = with(LocalDensity.current) {
        (textStyle.lineHeight.value * density).toDp()
    }
    val maxFieldHeight = remember(lineHeightDp) { lineHeightDp * 5 + 16.dp }

    Column(modifier = modifier.fillMaxWidth()) {
        if (isVoiceRecording) {
            VoiceRecordingBar(
                durationMs = voiceRecordingDurationMs,
                waveformAmplitudes = voiceWaveformAmplitudes,
                onCancel = onVoiceRecordCancel,
                onStop = onVoiceRecordStop,
                accentColor = accentColor
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // ── Text field pill ───────────────────────────────────────
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        BasicTextField(
                            value = text,
                            onValueChange = onTextChange,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 36.dp, max = maxFieldHeight)
                                .padding(vertical = 8.dp),
                            textStyle = textStyle.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(accentColor),
                            maxLines = 5,
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (text.isEmpty()) {
                                        Text(
                                            text = "Message",
                                            style = textStyle,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )

                        // Inline icons (collapse when typing)
                        AnimatedVisibility(
                            visible = !hasText,
                            enter = fadeIn(tween(120)),
                            exit = fadeOut(tween(120))
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = onAttachmentTap,
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AttachFile,
                                        contentDescription = "Attach",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                IconButton(
                                    onClick = onCameraTap,
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CameraAlt,
                                        contentDescription = "Camera",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.width(6.dp))

                // ── Action FAB area ──────────────────────────────────────
                AnimatedContent(
                    targetState = hasText,
                    transitionSpec = {
                        (scaleIn(tween(180)) + fadeIn(tween(180)))
                            .togetherWith(scaleOut(tween(120)) + fadeOut(tween(120)))
                            .using(SizeTransform(clip = false))
                    },
                    label = "input_fab"
                ) { showSend ->
                    when {
                        showSend -> {
                            // Send FAB
                            FloatingActionButton(
                                onClick = onSend,
                                modifier = Modifier
                                    .size(46.dp)
                                    .semantics { contentDescription = "Send message" },
                                shape = CircleShape,
                                containerColor = accentColor,
                                contentColor = Color.White,
                                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        emojiShortcut != null -> {
                            // Emoji shortcut + mic
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = onVoiceRecordStart,
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Mic,
                                        contentDescription = "Voice",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                FloatingActionButton(
                                    onClick = onEmojiShortcutSend,
                                    modifier = Modifier
                                        .size(46.dp)
                                        .semantics { contentDescription = "Send $emojiShortcut" },
                                    shape = CircleShape,
                                    containerColor = accentColor,
                                    contentColor = Color.White,
                                    elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
                                ) {
                                    Text(
                                        text = emojiShortcut,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }
                        }
                        else -> {
                            // Mic FAB
                            FloatingActionButton(
                                onClick = onVoiceRecordStart,
                                modifier = Modifier
                                    .size(46.dp)
                                    .semantics { contentDescription = "Record voice" },
                                shape = CircleShape,
                                containerColor = accentColor,
                                contentColor = Color.White,
                                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Mic,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Voice Recording Bar ──────────────────────────────────────────────────────

@Composable
private fun VoiceRecordingBar(
    durationMs: Long,
    waveformAmplitudes: List<Float>,
    onCancel: () -> Unit,
    onStop: () -> Unit,
    accentColor: Color
) {
    val seconds = (durationMs / 1000).toInt()
    val minutes = seconds / 60
    val secs = seconds % 60
    val timerText = "%d:%02d".format(minutes, secs)

    val pulseAlpha by animateFloatAsState(
        targetValue = if (seconds % 2 == 0) 1f else 0.3f,
        animationSpec = tween(500),
        label = "pulse"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Cancel
        IconButton(onClick = onCancel, modifier = Modifier.size(42.dp)) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Cancel",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(Modifier.width(4.dp))

        // Recording indicator
        Box(
            modifier = Modifier
                .size(8.dp)
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

        // Waveform
        WaveformVisualization(
            amplitudes = waveformAmplitudes,
            color = accentColor,
            modifier = Modifier
                .weight(1f)
                .height(28.dp)
        )

        Spacer(Modifier.width(8.dp))

        // Send
        FloatingActionButton(
            onClick = onStop,
            modifier = Modifier
                .size(46.dp)
                .semantics { contentDescription = "Send voice" },
            shape = CircleShape,
            containerColor = accentColor,
            contentColor = Color.White,
            elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ── Waveform Visualization ───────────────────────────────────────────────────

@Composable
private fun WaveformVisualization(
    amplitudes: List<Float>,
    color: Color,
    modifier: Modifier = Modifier
) {
    val maxBars = 28
    val displayAmps = if (amplitudes.size > maxBars) amplitudes.takeLast(maxBars) else amplitudes

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        displayAmps.forEach { amp ->
            val barHeight = (amp.coerceIn(0.08f, 1f) * 24).dp
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(barHeight)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(color.copy(alpha = 0.6f + amp * 0.4f))
            )
        }
    }
}
