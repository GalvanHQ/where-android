package com.ovi.where.presentation.permission

import android.Manifest
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PublicOff
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * One row in the first-run permission onboarding flow.
 *
 * Why a sealed model instead of just composables?
 *   The same data drives:
 *     1. The `PermissionOnboardingSheet` rows (icon + title + subline +
 *        granted state),
 *     2. The system permission request when the user taps "Allow",
 *     3. The "skip until next launch" gate — we record which steps the
 *        user dismissed so the sheet doesn't re-pop on every app start.
 *
 *   Keeping all of that in one sealed hierarchy makes adding a new
 *   permission a one-file change instead of three.
 *
 * Each step also carries enough copy to render a *premium* explanation
 * — a one-liner subline plus a short "why we ask" body. Polished apps
 * (Life360, Google Maps, WhatsApp) win user trust by being concrete
 * about what the permission unlocks rather than reciting the system
 * permission name. We do the same here.
 */
sealed interface PermissionStep {

    /** Stable id used by DataStore to remember per-step skip state. */
    val id: String

    /** Glyph shown in the row's leading bubble. */
    val icon: ImageVector

    /** One-line title rendered as `bodyLarge` weight 600. */
    val title: String

    /** One-line subline. Plain English, no jargon. */
    val subline: String

    /** Two-to-three sentence body shown when the row is expanded. */
    val body: String

    /** Whether this step is *required* for core app value. Required steps
     *  block "Get started" until granted; optional ones can be skipped. */
    val required: Boolean

    /**
     * Foreground location — both fine and coarse. Required because
     * everything in the app revolves around the user being on the map.
     */
    data object ForegroundLocation : PermissionStep {
        override val id = "foreground_location"
        override val icon = Icons.Outlined.LocationOn
        override val title = "Location"
        override val subline = "See yourself on the map"
        override val body =
            "We use your location to place you on the map and let you share with friends. " +
                "You can stop sharing any time from the map screen."
        override val required = true

        val permissions: List<String> = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    }

    /**
     * Background location. Critical for a "Where am I"-style app — without
     * it, the OS suspends our location reads after ~30s and friends see a
     * stale dot. Required by our value prop, but still listed second so
     * the system doesn't reject the request before foreground is granted.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    data object BackgroundLocation : PermissionStep {
        override val id = "background_location"
        override val icon = Icons.Outlined.PublicOff
        override val title = "Background location"
        override val subline = "Keep sharing while the app is closed"
        override val body =
            "Where keeps your location fresh for friends even when the app isn't open. " +
                "Without this, your dot freezes the moment you switch apps."
        override val required = true

        val permission: String = Manifest.permission.ACCESS_BACKGROUND_LOCATION
    }

    /**
     * POST_NOTIFICATIONS (Android 13+). Doesn't gate the map experience
     * directly, but if it's denied the user silently misses chats and
     * meetup pings — the most surprised-user pattern in the app.
     */
    data object Notifications : PermissionStep {
        override val id = "notifications"
        override val icon = Icons.Outlined.Notifications
        override val title = "Notifications"
        override val subline = "Get pinged about chats and meetups"
        override val body =
            "We'll only notify you for things that matter: messages, friend " +
                "requests, and meetup updates. You can fine-tune categories in Settings later."
        override val required = false

        val permission: String = Manifest.permission.POST_NOTIFICATIONS
    }

    /**
     * Battery optimization exemption. Not a permission per se but it gates
     * the OS scheduler — without it, the foreground service that powers
     * background location updates gets throttled to a stop on most OEMs.
     */
    data object BatteryOptimization : PermissionStep {
        override val id = "battery_optimization"
        override val icon = Icons.Outlined.BatteryChargingFull
        override val title = "Battery"
        override val subline = "Don't put Where to sleep"
        override val body =
            "Some Android phones aggressively kill background apps. Allowing Where to " +
                "skip battery optimization keeps live sharing reliable."
        override val required = false
    }

    companion object {
        /**
         * The full ordered list of permission steps shown in the onboarding
         * sheet. Order matters: foreground location comes first because
         * background location can't be requested without it, and battery
         * optimization comes last because it's the only one that takes the
         * user out of the app to a Settings page.
         */
        @Suppress("NewApi")
        val ALL: List<PermissionStep> = buildList {
            add(ForegroundLocation)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(BackgroundLocation)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Notifications)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) add(BatteryOptimization)
        }
    }
}
