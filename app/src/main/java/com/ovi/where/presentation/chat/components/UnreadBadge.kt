package com.ovi.where.presentation.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A 20dp filled circular badge displaying the unread message count.
 *
 * Uses primary color background with onPrimary (white) text.
 * Displays "99+" when count exceeds 99.
 * Only renders content when count > 0 — caller should handle visibility.
 *
 * Requirement 3.9: Filled circular badge (20dp) with unread count on trailing edge.
 */
@Composable
fun UnreadBadge(
    count: Int,
    modifier: Modifier = Modifier
) {
    if (count <= 0) return

    val displayText = if (count > 99) "99+" else count.toString()

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 4.dp)
    ) {
        Text(
            text = displayText,
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}
