package com.ovi.where.presentation.chat.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ovi.where.core.theme.Dimens

/**
 * Voice message playback bubble composable.
 *
 * Renders a play/pause button, seekable progress bar, and duration label formatted as "m:ss".
 * Designed to be placed inside a message bubble Surface with appropriate styling.
 *
 * Requirements: 11.8, 11.9, 11.10
 *
 * @param durationMs Total duration of the voice message in milliseconds.
 * @param isPlaying Whether this voice message is currently playing.
 * @param progress Current playback progress from 0.0 to 1.0.
 * @param currentPositionMs Current playback position in milliseconds (used for time display during playback).
 * @param onPlayPause Callback when the play/pause button is tapped.
 * @param onSeek Callback when the user seeks to a new position (0.0 to 1.0).
 * @param accentColor The accent color for the slider and play button (adapts to bubble direction).
 * @param textColor The text color for the duration label (adapts to bubble direction).
 */
@Composable
fun VoiceMessageBubble(
    durationMs: Long,
    isPlaying: Boolean,
    progress: Float,
    currentPositionMs: Long,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    // Determine which time to display:
    // - While playing or paused mid-way: show current position
    // - At rest (progress == 0): show total duration
    val displayTimeMs = if (progress > 0f || isPlaying) currentPositionMs else durationMs
    val formattedTime = formatVoiceDuration(displayTimeMs)

    val playPauseDescription = if (isPlaying) "Pause voice message" else "Play voice message"

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        // Play/Pause button
        IconButton(
            onClick = onPlayPause,
            modifier = Modifier
                .size(36.dp)
                .semantics { contentDescription = playPauseDescription }
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null, // Handled by parent semantics
                tint = accentColor,
                modifier = Modifier.size(Dimens.iconSizeMedium)
            )
        }

        Spacer(Modifier.width(4.dp))

        // Seekable progress bar
        Slider(
            value = progress,
            onValueChange = onSeek,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = accentColor.copy(alpha = 0.3f)
            )
        )

        Spacer(Modifier.width(Dimens.spaceMedium))

        // Duration label formatted as "m:ss" (Requirement 11.8)
        Text(
            text = formattedTime,
            style = MaterialTheme.typography.labelSmall,
            color = textColor
        )
    }
}

/**
 * Formats a duration in milliseconds to "m:ss" format.
 *
 * Examples:
 * - 0ms → "0:00"
 * - 5000ms → "0:05"
 * - 65000ms → "1:05"
 * - 300000ms → "5:00"
 *
 * Requirement 11.8: Duration label formatted as "m:ss".
 */
fun formatVoiceDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
