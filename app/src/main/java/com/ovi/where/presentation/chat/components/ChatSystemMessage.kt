package com.ovi.where.presentation.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Centered grey "info line" rendered inline in the chat timeline for events
 * like group renames, member additions, and theme color changes. Has no
 * avatar, bubble, status, or reactions — pure read-only signal.
 *
 * Tapping the line briefly reveals a small floating timestamp (auto-hides
 * after 2s), matching the existing [ChatBubble] tap-to-reveal pattern.
 *
 * The text is pre-rendered by `SystemMessageRenderer` and lives on
 * [com.ovi.where.presentation.model.MessageUiModel.systemText], so this
 * composable stays purely declarative.
 *
 * See `.kiro/specs/group-system-messages/` (Requirement 5).
 */
@Composable
fun ChatSystemMessage(
    text: String,
    timestamp: Long,
    modifier: Modifier = Modifier
) {
    var showTimestamp by remember { mutableStateOf(false) }

    // Auto-hide the timestamp after the user taps it.
    LaunchedEffect(showTimestamp) {
        if (showTimestamp) {
            delay(TIMESTAMP_AUTO_HIDE_MS)
            showTimestamp = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 24.dp)
            .clickable(
                onClick = { showTimestamp = !showTimestamp },
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        AnimatedVisibility(
            visible = showTimestamp,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Text(
                text = formatTimestamp(timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

private const val TIMESTAMP_AUTO_HIDE_MS = 2_000L

private fun formatTimestamp(epochMs: Long): String {
    val now = Calendar.getInstance()
    val msg = Calendar.getInstance().apply { timeInMillis = epochMs }
    val sameDay = now.get(Calendar.YEAR) == msg.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == msg.get(Calendar.DAY_OF_YEAR)

    val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    val dateFormatter = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

    return if (sameDay) {
        "Today at ${timeFormatter.format(Date(epochMs))}"
    } else {
        dateFormatter.format(Date(epochMs))
    }
}
