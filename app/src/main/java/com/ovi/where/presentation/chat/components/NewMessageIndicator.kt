package com.ovi.where.presentation.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ovi.where.core.utils.LocalReducedMotion

/**
 * Floating "scroll to bottom" indicator with an unread count badge.
 *
 * Visible whenever a new message arrives while the user is scrolled away from the bottom.
 * Tapping scrolls to the latest message with a 300ms decelerate animation.
 *
 * Layout: small circular FAB with a chevron-down icon, plus a tiny primary-colored badge
 * pinned to the top-end corner showing the unread count (capped at "99+").
 */
@Composable
fun NewMessageIndicator(
    visible: Boolean,
    unreadCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reducedMotion = LocalReducedMotion.current

    val enterTransition = if (reducedMotion) {
        fadeIn(animationSpec = tween(0))
    } else {
        fadeIn(animationSpec = tween(200, easing = FastOutSlowInEasing)) +
                slideInVertically(
                    animationSpec = tween(200, easing = FastOutSlowInEasing),
                    initialOffsetY = { it / 2 }
                ) +
                scaleIn(
                    animationSpec = tween(200, easing = FastOutSlowInEasing),
                    initialScale = 0.85f
                )
    }

    val exitTransition = if (reducedMotion) {
        fadeOut(animationSpec = tween(0))
    } else {
        fadeOut(animationSpec = tween(150)) +
                slideOutVertically(
                    animationSpec = tween(150),
                    targetOffsetY = { it / 2 }
                ) +
                scaleOut(animationSpec = tween(150), targetScale = 0.85f)
    }

    AnimatedVisibility(
        visible = visible,
        enter = enterTransition,
        exit = exitTransition,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .padding(end = 12.dp, bottom = 12.dp)
                .semantics {
                    contentDescription = if (unreadCount > 1) {
                        "$unreadCount new messages. Tap to scroll to latest."
                    } else {
                        "New message. Tap to scroll to latest."
                    }
                }
        ) {
            SmallFloatingActionButton(
                onClick = onClick,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = CircleShape,
                elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(
                    defaultElevation = 4.dp,
                    pressedElevation = 6.dp
                ),
                modifier = Modifier
                    .size(40.dp)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Unread badge pinned to top-end corner
            if (unreadCount > 0) {
                val badgeText = if (unreadCount > 99) "99+" else unreadCount.toString()
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = badgeText,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
