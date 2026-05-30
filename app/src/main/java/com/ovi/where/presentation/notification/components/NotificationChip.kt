package com.ovi.where.presentation.notification.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Top-of-map circular chip that opens the in-app Notifications inbox.
 *
 * Visual language matches the meetup + group-filter chips: a 52dp circle,
 * white `surface`, soft shadow, NO border, and a BARE bell glyph (no
 * tinted bubble behind it).
 *
 * When [unreadCount] is positive, a small error-red DOT sits on the bell's
 * top-right — a clean "you have something new" signal, no number. The dot
 * gets a thin surface ring so it reads cleanly against the bell and the map.
 */
@Composable
fun NotificationChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    unreadCount: Int = 0
) {
    Surface(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = if (unreadCount > 0) {
                    "Notifications, $unreadCount unread"
                } else {
                    "Notifications"
                }
            },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 3.dp,
        tonalElevation = 1.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Rounded.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )

            if (unreadCount > 0) {
                // Unread dot — offset onto the bell's top-right. A surface
                // ring lifts the red off the glyph so it stays crisp.
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(20.dp),
                    contentAlignment = Alignment.TopEnd
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(1.5.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error)
                    )
                }
            }
        }
    }
}
