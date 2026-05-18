package com.ovi.where.presentation.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ovi.where.core.theme.Dimens
import com.ovi.where.presentation.model.MessageUiModel

/**
 * Reply preview bar displayed above the input field when the user is replying to a message.
 *
 * Shows:
 * - Sender name of the message being replied to
 * - Up to 100 characters of the message text
 * - Close button to dismiss the reply without affecting input text
 *
 * Requirements: 4.1, 4.2, 4.3
 */
@Composable
fun ReplyPreviewBar(
    replyingToMessage: MessageUiModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Accent vertical bar indicator
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )

            Spacer(Modifier.width(10.dp))

            // Reply content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Replying to ${replyingToMessage.senderName}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = when {
                        replyingToMessage.isImage -> "📷 Photo"
                        replyingToMessage.isVoice -> "🎤 Voice message"
                        replyingToMessage.isLocation -> "📍 Location"
                        else -> replyingToMessage.text.take(100)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Image thumbnail for image replies
            if (replyingToMessage.isImage && !replyingToMessage.imageUrl.isNullOrBlank()) {
                Spacer(Modifier.width(10.dp))
                coil.compose.AsyncImage(
                    model = replyingToMessage.imageUrl,
                    contentDescription = "Reply image",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
            }

            // Close button — dismisses reply without affecting input text (Requirement 4.3)
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(28.dp)
                    .semantics { contentDescription = "Dismiss reply" }
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Quoted message preview displayed above the reply text within a message bubble.
 *
 * Shows:
 * - replyToSenderName in bold
 * - Up to 100 characters of replyToText
 * - Tappable: scrolls to original message (no-op if not loaded)
 *
 * Requirements: 4.5, 4.6, 4.7
 */
@Composable
fun QuotedMessagePreview(
    replyToSenderName: String,
    replyToText: String,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .semantics { contentDescription = "Quoted message from $replyToSenderName" },
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
        shape = RoundedCornerShape(Dimens.cornerExtraSmall)
    ) {
        Row(
            modifier = Modifier.padding(Dimens.spaceMedium),
            verticalAlignment = Alignment.Top
        ) {
            // Accent vertical bar
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
            )

            Spacer(Modifier.width(Dimens.spaceMedium))

            Column {
                Text(
                    text = replyToSenderName,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = replyToText.take(100),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
