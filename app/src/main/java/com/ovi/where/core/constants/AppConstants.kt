package com.ovi.where.core.constants

object AppConstants {
    // Location
    const val LOCATION_UPDATE_INTERVAL = 5000L
    const val LOCATION_FASTEST_INTERVAL = 3000L
    const val DEFAULT_ZOOM_LEVEL = 15f

    // Sharing durations
    const val SHARING_DURATION_15_MIN = 15L
    const val SHARING_DURATION_1_HOUR = 60L
    const val SHARING_DURATION_CUSTOM = -1L
    const val SHARING_DURATION_CONTINUOUS = 0L

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
    const val FIRESTORE_COLLECTION_MESSAGES = "messages"
    const val FIRESTORE_COLLECTION_FRIENDSHIPS = "friendships"
    const val FIRESTORE_COLLECTION_DIRECT_LOCATION_SHARES = "directLocationShares"

    // DataStore
    const val DATASTORE_USER_PREFERENCES = "user_preferences"

    // Validation
    const val USERNAME_MIN_LENGTH = 3
    const val USERNAME_MAX_LENGTH = 20
    const val BIO_MAX_LENGTH = 150
}
