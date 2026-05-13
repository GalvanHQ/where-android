package com.ovi.where.presentation.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import com.ovi.where.core.utils.LocalReducedMotion
import com.ovi.where.core.utils.effectiveAnimationDuration

/**
 * A reusable Modifier extension that applies a press animation effect:
 * - Scale-down to 0.95 of original size during press
 * - Fade to 0.7 opacity during press
 * - Returns to 1.0 scale and 1.0 opacity within 150ms of release
 *
 * When the button is not being pressed, no opacity or scale effects are applied.
 * Respects the "Remove animations" / "Reduce motion" accessibility setting —
 * when enabled, animation duration is reduced to ≤50ms.
 */
fun Modifier.pressAnimation(): Modifier = composed {
    val reducedMotion = LocalReducedMotion.current
    val duration = effectiveAnimationDuration(PRESS_ANIMATION_DURATION_MS, reducedMotion)

    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(durationMillis = duration),
        label = "press_scale"
    )

    val alpha by animateFloatAsState(
        targetValue = if (isPressed) 0.7f else 1f,
        animationSpec = tween(durationMillis = duration),
        label = "press_alpha"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    isPressed = true
                    tryAwaitRelease()
                    isPressed = false
                }
            )
        }
}

/** Standard press animation duration (ms). */
private const val PRESS_ANIMATION_DURATION_MS = 150

/**
 * Animation duration for list item appearance/disappearance animations.
 * Set to 250ms (within the 200-300ms requirement range).
 */
const val LIST_ITEM_ANIMATION_DURATION_MS = 250
