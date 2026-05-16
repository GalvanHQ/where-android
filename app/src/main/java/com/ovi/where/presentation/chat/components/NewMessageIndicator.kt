package com.ovi.where.presentation.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ovi.where.core.theme.Dimens
import com.ovi.where.core.utils.LocalReducedMotion

/**
 * Floating "new message" indicator shown when the user is scrolled more than 150dp
 * above the last visible message and a new message arrives.
 *
 * Tapping scrolls to the latest message with a 300ms decelerate animation.
 *
 * Requirement 23.4: If > 150dp above, show "new message" indicator; tap scrolls to latest
 * with 300ms animation.
 * Requirement 23.7: Reduced motion — skip slide animation, apply instantly.
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
                    initialOffsetY = { it }
                )
    }

    val exitTransition = if (reducedMotion) {
        fadeOut(animationSpec = tween(0))
    } else {
        fadeOut(animationSpec = tween(150)) +
                slideOutVertically(
                    animationSpec = tween(150),
                    targetOffsetY = { it }
                )
    }

    AnimatedVisibility(
        visible = visible,
        enter = enterTransition,
        exit = exitTransition,
        modifier = modifier
    ) {
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = RoundedCornerShape(Dimens.cornerLarge),
            modifier = Modifier
                .padding(Dimens.spaceMedium)
                .semantics {
                    contentDescription = if (unreadCount > 1) {
                        "$unreadCount new messages. Tap to scroll to latest."
                    } else {
                        "New message. Tap to scroll to latest."
                    }
                }
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(Dimens.iconSizeMedium)
            )
        }
    }
}
