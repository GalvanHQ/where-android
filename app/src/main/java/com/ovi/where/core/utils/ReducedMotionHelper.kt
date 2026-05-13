package com.ovi.where.core.utils

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Maximum animation duration (ms) when reduced motion is enabled.
 * Per requirement 6.6, all animations must be ≤50ms when the accessibility setting is active.
 */
const val REDUCED_MOTION_MAX_DURATION_MS = 50

/**
 * CompositionLocal that indicates whether the device has reduced motion / remove animations
 * accessibility setting enabled.
 *
 * When `true`, all transition and animation durations should be reduced to ≤50ms.
 *
 * Usage:
 * ```
 * val reducedMotion = LocalReducedMotion.current
 * val duration = if (reducedMotion) REDUCED_MOTION_MAX_DURATION_MS else normalDuration
 * ```
 */
val LocalReducedMotion = compositionLocalOf { false }

/**
 * Detects whether the device has the "Remove animations" or "Reduce motion" accessibility
 * setting enabled.
 *
 * This checks [Settings.Global.ANIMATOR_DURATION_SCALE]. A value of 0f means animations
 * are disabled (the user has turned off animations in Developer Options or via the
 * accessibility "Remove animations" toggle).
 *
 * @return `true` if animations should be reduced/skipped, `false` otherwise.
 */
fun isReducedMotionEnabled(context: Context): Boolean {
    val animatorDurationScale = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    )
    return animatorDurationScale == 0f
}

/**
 * Composable helper that remembers the reduced motion state.
 * Use this to provide the value to [LocalReducedMotion] at the theme level.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember { isReducedMotionEnabled(context) }
}

/**
 * Returns the effective animation duration in milliseconds, respecting the reduced motion setting.
 *
 * @param normalDurationMs The standard animation duration when reduced motion is not active.
 * @param reducedMotion Whether reduced motion is currently enabled.
 * @return [REDUCED_MOTION_MAX_DURATION_MS] if reduced motion is enabled, otherwise [normalDurationMs].
 */
fun effectiveAnimationDuration(normalDurationMs: Int, reducedMotion: Boolean): Int {
    return if (reducedMotion) REDUCED_MOTION_MAX_DURATION_MS else normalDurationMs
}
