package com.ovi.where.core.constants

object AppConstants {
    // Location — Adaptive intervals based on movement state
    const val LOCATION_UPDATE_INTERVAL = 5000L
    const val LOCATION_FASTEST_INTERVAL = 3000L

    /** Interval when user is moving (walking/driving). High accuracy, frequent updates. */
    const val LOCATION_INTERVAL_MOVING_MS = 5_000L

    /** Interval when user is stationary (speed < threshold). Saves battery. */
    const val LOCATION_INTERVAL_IDLE_MS = 30_000L

    /** Interval when user is in background but sharing is active. Balanced. */
    const val LOCATION_INTERVAL_BACKGROUND_MS = 15_000L

    /** Speed threshold (m/s) below which user is considered stationary (~1 km/h). */
    const val LOCATION_IDLE_SPEED_THRESHOLD = 0.3f

    /** Minimum displacement (meters) to trigger an update when idle. */
    const val LOCATION_MIN_DISPLACEMENT_METERS = 10f

    /** Radius (meters) for arrival detection geofence. */
    const val ARRIVAL_RADIUS_METERS = 100.0

    /**
     * Radius (meters) around a user's saved home within which they're
     * considered "At Home". Slightly larger than the arrival geofence to
     * tolerate GPS drift indoors where the fix is weaker.
     */
    const val HOME_RADIUS_METERS = 150.0

    // Notifications
    const val NOTIFICATION_ID = 1001
    const val NOTIFICATION_CHANNEL_ID = "location_service_channel"
    const val NOTIFICATION_CHANNEL_NAME = "Location Service"

    // Firestore collections
    const val FIRESTORE_COLLECTION_USERS = "users"
    const val FIRESTORE_COLLECTION_GROUPS = "groups"
    const val FIRESTORE_COLLECTION_LOCATIONS = "locations"
    const val FIRESTORE_COLLECTION_MEMBERS = "members"
    const val FIRESTORE_COLLECTION_CONVERSATIONS = "conversations"
    const val FIRESTORE_COLLECTION_DIRECT_LOCATION_SHARES = "directLocationShares"
    const val FIRESTORE_COLLECTION_ACTIVE_LOCATIONS = "activeLocations"

    // Write throttle — speed-dependent (see LocationRepositoryImpl companion object)
    // Moving (speed > 1 m/s): 10s interval
    // Stationary (speed ≤ 1 m/s): 30s interval
    @Deprecated("Use speed-dependent throttle in LocationRepositoryImpl instead")
    const val LOCATION_WRITE_THROTTLE_MS = 15_000L

    // ── Time Constants ────────────────────────────────────────────────────────

    /** Milliseconds per minute. */
    const val MILLIS_PER_MINUTE = 60_000L

    /** Milliseconds per hour. */
    const val MILLIS_PER_HOUR = 3_600_000L

    /** Milliseconds per day. */
    const val MILLIS_PER_DAY = 86_400_000L

    /** Milliseconds per week. */
    const val MILLIS_PER_WEEK = 604_800_000L

    /** Seconds per hour. */
    const val SECONDS_PER_HOUR = 3600L

    /** Seconds for two hours. */
    const val SECONDS_PER_TWO_HOURS = 7200L

    /** Minutes per day (used for duration formatting). */
    const val MINUTES_PER_DAY = 1440L

    // ── UI Timing Constants ───────────────────────────────────────────────────

    /** Debounce delay for search input in milliseconds. */
    const val SEARCH_DEBOUNCE_MS = 300L

    /** Minimum query length before triggering a search. */
    const val MIN_SEARCH_QUERY_LENGTH = 2

    /** Pull-to-refresh timeout in milliseconds. */
    const val PULL_TO_REFRESH_TIMEOUT_MS = 10_000L

    /** Time-ago refresh ticker interval in milliseconds. */
    const val TIME_AGO_REFRESH_INTERVAL_MS = 30_000L

    /** Debounce delay for batched user fetches in milliseconds. */
    const val USER_FETCH_DEBOUNCE_MS = 200L

    /** Standard ViewModel StateFlow subscription timeout in milliseconds. */
    const val STATE_FLOW_SUBSCRIBE_TIMEOUT_MS = 5_000L

    // ── Distance Constants ────────────────────────────────────────────────────

    /** Threshold in meters for switching from meters to kilometers display. */
    const val DISTANCE_KM_THRESHOLD_METERS = 1000.0

    /** Threshold in meters for switching from decimal km to rounded km display. */
    const val DISTANCE_ROUNDED_KM_THRESHOLD_METERS = 10_000.0
}
