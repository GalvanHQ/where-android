package com.ovi.where.presentation.chat.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ovi.where.R
import com.ovi.where.core.theme.Dimens

/**
 * Empty state displayed in ChatScreen when a conversation has no messages.
 *
 * Shows a centered illustration with a "Say hi!" prompt to encourage the user
 * to send the first message.
 *
 * Requirement 10.5: When a conversation has no messages, display a centered empty state
 * with an illustration and "Say hi!" prompt text.
 */
@Composable
fun ChatEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Dimens.spaceXLarge),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.dialogue),
            contentDescription = "No messages yet",
            modifier = Modifier
                .size(140.dp)
                .alpha(0.9f),
            contentScale = ContentScale.Fit
        )
        Spacer(Modifier.height(Dimens.spaceLarge))
        Text(
            text = "Say hi! 👋",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Dimens.spaceMedium))
        Text(
            text = "Send a message to start the conversation",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}
