package com.ovi.where.presentation.chat.components

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Premium chat input bar.
 *
 * Layout (collapsed):  [+] [📍] │ Message...  [🎤] │ [➤/👍]
 * Layout (expanded):   [✕] [📷] [🖼️] [📍] │ Message...  [🎤] │ [➤/👍]
 *
 * - Plus expands inline to show Camera + Gallery icons (no dropdown/popup)
 * - Location button is always visible next to + for fast access
 * - Mic inside the pill for voice recording
 * - Right: Send (when typing) or Emoji shortcut (when set)
 */
@Composable
fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onCameraTap: () -> Unit,
    onAttachmentTap: () -> Unit,
    onLocationTap: () -> Unit = {},
    isVoiceRecording: Boolean = false,
    voiceRecordingDurationMs: Long = 0L,
    voiceWaveformAmplitudes: List<Float> = emptyList(),
    onVoiceRecordStart: () -> Unit = {},
    onVoiceRecordStop: () -> Unit = {},
    onVoiceRecordCancel: () -> Unit = {},
    emojiShortcut: String? = null,
    onEmojiShortcutSend: () -> Unit = {},
    themeColor: Color? = null,
    mentionRanges: List<IntRange> = emptyList(),
    isSharingLocation: Boolean = false,
    modifier: Modifier = Modifier
) {
    val hasText = text.isNotBlank()
    val accent = themeColor ?: MaterialTheme.colorScheme.primary
    val textStyle = MaterialTheme.typography.bodyLarge
    val lineHeightDp = with(LocalDensity.current) { (textStyle.lineHeight.value * density).toDp() }
    val maxFieldHeight = remember(lineHeightDp) { lineHeightDp * 5 + 20.dp }

    if (isVoiceRecording) {
        VoiceRecordingBar(
            durationMs = voiceRecordingDurationMs,
            waveformAmplitudes = voiceWaveformAmplitudes,
            onCancel = onVoiceRecordCancel,
            onStop = onVoiceRecordStop,
            accent = accent,
            modifier = modifier
        )
    } else {
        var expanded by remember { mutableStateOf(false) }

        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Left: Plus or expanded Camera+Gallery, then Location pill ────────────────
            AnimatedContent(
                targetState = expanded,
                transitionSpec = {
                    (scaleIn(tween(120)) + fadeIn(tween(120)))
                        .togetherWith(scaleOut(tween(80)) + fadeOut(tween(80)))
                        .using(SizeTransform(clip = false))
                },
                label = "attach"
            ) { isExpanded ->
                if (isExpanded) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { expanded = false },
                            modifier = Modifier.size(38.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            Icon(Icons.Filled.Close, "Close", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(4.dp))
                        IconButton(
                            onClick = { expanded = false; onCameraTap() },
                            modifier = Modifier.size(38.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            Icon(Icons.Filled.CameraAlt, "Camera", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(4.dp))
                        IconButton(
                            onClick = { expanded = false; onAttachmentTap() },
                            modifier = Modifier.size(38.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            Icon(Icons.Filled.Image, "Gallery", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { expanded = true },
                            modifier = Modifier.size(42.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Icon(Icons.Filled.Add, "Attach", Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(4.dp))
                        // Standalone location button — opens live meetup sheet
                        IconButton(
                            onClick = onLocationTap,
                            modifier = Modifier.size(42.dp).semantics { contentDescription = "Live location" },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (isSharingLocation) accent.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = if (isSharingLocation) accent else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Icon(Icons.Filled.LocationOn, null, Modifier.size(22.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.width(6.dp))

            // ── Text field pill ──────────────────────────────────────
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val mentionColor = MaterialTheme.colorScheme.primary
                    BasicTextField(
                        value = text,
                        onValueChange = onTextChange,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 38.dp, max = maxFieldHeight)
                            .padding(vertical = 9.dp),
                        textStyle = textStyle.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 20.sp
                        ),
                        cursorBrush = SolidColor(accent),
                        maxLines = 5,
                        visualTransformation = androidx.compose.ui.text.input.VisualTransformation { annotatedString ->
                            if (mentionRanges.isEmpty()) {
                                androidx.compose.ui.text.input.TransformedText(annotatedString, androidx.compose.ui.text.input.OffsetMapping.Identity)
                            } else {
                                val builder = androidx.compose.ui.text.AnnotatedString.Builder(annotatedString)
                                for (range in mentionRanges) {
                                    val safeStart = range.first.coerceIn(0, annotatedString.length)
                                    val safeEnd = (range.last + 1).coerceIn(0, annotatedString.length)
                                    if (safeEnd > safeStart) {
                                        builder.addStyle(
                                            androidx.compose.ui.text.SpanStyle(color = mentionColor, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                                            safeStart,
                                            safeEnd
                                        )
                                    }
                                }
                                androidx.compose.ui.text.input.TransformedText(builder.toAnnotatedString(), androidx.compose.ui.text.input.OffsetMapping.Identity)
                            }
                        },
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (text.isEmpty()) {
                                    Text(
                                        text = "Message",
                                        style = textStyle.copy(lineHeight = 20.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )

                    // Mic inside pill
                    IconButton(
                        onClick = onVoiceRecordStart,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = "Voice",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.width(6.dp))

            // ── Right: Send or Emoji shortcut ────────────────────────
            AnimatedContent(
                targetState = hasText,
                transitionSpec = {
                    (scaleIn(tween(150)) + fadeIn(tween(150)))
                        .togetherWith(scaleOut(tween(100)) + fadeOut(tween(100)))
                        .using(SizeTransform(clip = false))
                },
                label = "send_btn"
            ) { typing ->
                when {
                    typing -> {
                        IconButton(
                            onClick = onSend,
                            modifier = Modifier.size(42.dp).semantics { contentDescription = "Send" },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = accent,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(20.dp))
                        }
                    }
                    emojiShortcut != null -> {
                        IconButton(
                            onClick = onEmojiShortcutSend,
                            modifier = Modifier.size(42.dp).semantics { contentDescription = "Send $emojiShortcut" },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Transparent)
                        ) {
                            Text(text = emojiShortcut, fontSize = 26.sp)
                        }
                    }
                    else -> {
                        IconButton(
                            onClick = {},
                            modifier = Modifier.size(42.dp),
                            enabled = false,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = accent.copy(alpha = 0.3f),
                                contentColor = Color.White.copy(alpha = 0.5f)
                            )
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(20.dp))
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
    accent: Color,
    modifier: Modifier = Modifier
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
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onCancel, modifier = Modifier.size(42.dp)) {
            Icon(Icons.Filled.Close, "Cancel", Modifier.size(22.dp), tint = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.error.copy(alpha = pulseAlpha))
        )

        Spacer(Modifier.width(8.dp))

        Text(
            text = timerText,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.width(12.dp))

        WaveformVisualization(
            amplitudes = waveformAmplitudes,
            color = accent,
            modifier = Modifier.weight(1f).height(28.dp)
        )

        Spacer(Modifier.width(8.dp))

        IconButton(
            onClick = onStop,
            modifier = Modifier.size(42.dp).semantics { contentDescription = "Send voice" },
            colors = IconButtonDefaults.iconButtonColors(containerColor = accent, contentColor = Color.White)
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(20.dp))
        }
    }
}

// ── Waveform ─────────────────────────────────────────────────────────────────

@Composable
private fun WaveformVisualization(
    amplitudes: List<Float>,
    color: Color,
    modifier: Modifier = Modifier
) {
    val maxBars = 24
    val displayAmps = if (amplitudes.size > maxBars) amplitudes.takeLast(maxBars) else amplitudes

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        displayAmps.forEach { amp ->
            val h = (amp.coerceIn(0.1f, 1f) * 22).dp
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(h)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(color.copy(alpha = 0.5f + amp * 0.5f))
            )
        }
    }
}
