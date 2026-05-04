package com.ovi.where.core.constants

object AppConstants {
    const val LOCATION_UPDATE_INTERVAL = 5000L
    const val LOCATION_FASTEST_INTERVAL = 3000L
    const val DEFAULT_ZOOM_LEVEL = 15f

    const val SHARING_DURATION_15_MIN = 15L
    const val SHARING_DURATION_1_HOUR = 60L
    const val SHARING_DURATION_CUSTOM = -1L
    const val SHARING_DURATION_CONTINUOUS = 0L

    const val NOTIFICATION_ID = 1001
    const val NOTIFICATION_CHANNEL_ID = "location_service_channel"
    const val NOTIFICATION_CHANNEL_NAME = "Location Service"

    const val FIRESTORE_COLLECTION_USERS = "users"
    const val FIRESTORE_COLLECTION_GROUPS = "groups"
    const val FIRESTORE_COLLECTION_LOCATIONS = "locations"
    const val FIRESTORE_COLLECTION_MEMBERS = "members"

    const val DATASTORE_USER_PREFERENCES = "user_preferences"
}
