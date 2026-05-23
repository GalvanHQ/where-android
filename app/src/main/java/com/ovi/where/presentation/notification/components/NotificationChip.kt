package com.ovi.where.presentation.notification.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Top-of-map icon-only chip that opens the in-app Notifications inbox.
 *
 * Visual language matches the meetup + group filter chips: 40dp circular
 * chip, surface background, outlineVariant border, 2dp elevation. The bell
 * glyph sits inside a primary-tinted bubble so the chip carries the same
 * weight as its labelled neighbours.
 *
 * Layout:
 * - Outer [Box] is 46dp to make room for the unread badge to peek above
 *   the chip's clip without getting cropped. The chip itself stays 40dp
 *   and is bottom-start anchored inside the outer box, so the badge
 *   floats free in the top-right corner.
 * - The clickable region is on the chip Surface only — tapping the empty
 *   space around the badge does nothing.
 *
 * When [unreadCount] is positive a small primary badge is overlaid on the
 * chip's top-right corner, capped at "9+" so it never grows. White
 * onPrimary text mirrors the bottom-tab unread bubble.
 */
@Composable
fun NotificationChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    unreadCount: Int = 0
) {
    // Outer wrapper is a few dp larger than the chip so the badge can
    // render outside the chip's circular clip. Without this the badge gets
    // cropped against the corner of the rounded surface.
    Box(
        modifier = modifier.size(46.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .size(40.dp)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onClick)
                .semantics {
                    contentDescription = if (unreadCount > 0) {
                        "Notifications, $unreadCount unread"
                    } else {
                        "Notifications"
                    }
                },
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 2.dp,
            shadowElevation = 2.dp,
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Inner badge — primary-tinted bubble around the bell glyph.
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        // Unread badge — sibling of the Surface, not inside it, so the
        // chip's rounded clip can't crop it. Aligned to the top-end of
        // the outer box (which is slightly larger than the chip).
        if (unreadCount > 0) {
            UnreadBadge(
                count = unreadCount,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
    }
}

@Composable
private fun UnreadBadge(
    count: Int,
    modifier: Modifier = Modifier
) {
    val label = if (count > 9) "9+" else count.toString()
    Box(
        modifier = modifier
            // Subtle white halo around the badge so the boundary against
            // the chip border stays crisp regardless of background.
            .shadow(elevation = 1.dp, shape = CircleShape)
            .size(16.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .background(Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = 10.sp,
            textAlign = TextAlign.Center
        )
    }
}
