package com.ovi.where.domain.model

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName

data class User(
    val id: String = "",
    val displayName: String = "",
    val username: String = "",
    val email: String = "",
    val bio: String = "",
    val photoUrl: String? = null,
    @PropertyName("isOnline")
    val isOnline: Boolean = false,
    val lastSeen: Long = 0L,
    val createdAt: Long = 0L,
    val fcmToken: String? = null,
    @PropertyName("isEmailVerified")
    val isEmailVerified: Boolean = false,
    /**
     * Privacy: who can find and view this profile in search.
     *  • "everyone" (default) — anyone can find via search
     *  • "friends"            — only users who are already friends see this profile in search
     *  • "hidden"             — never appears in search; friend-request-by-username still works
     *
     * Stored as a string for forward compatibility (new tiers can be added
     * without migrating documents). The client maps unknown / missing values
     * to "everyone" which keeps the user discoverable by default.
     */
    val profileVisibility: String = "everyone",
    /**
     * Privacy: governs the user's *outgoing* live-location sharing.
     *  • "always" — no client-side restriction; sharing accepted as-is
     *  • "friends" (default) — sharing only allowed with friends; group
     *    targets are accepted only when every member is a friend
     *  • "never"  — hard kill-switch; all start/add sharing calls fail
     *
     * The check happens in [com.ovi.where.data.repository.LocationRepositoryImpl]
     * before any Firestore write so the policy is honored even when the
     * user is offline. We don't need a server-side rule mirror because
     * the foreign side never reads my mode — only my own client does.
     */
    val locationSharingMode: String = "friends",

    // ── Home location ─────────────────────────────────────────────────────────
    /**
     * The user's home coordinates, picked on a map in Edit Profile. A value of
     * `0.0` for both lat/lng means "no home set" (see [hasHome]).
     *
     * Home is used for two things:
     *  • Rendered on the profile (own + other) as a "Home" row.
     *  • Drives the denormalized `isAtHome` flag the location pipeline writes
     *    onto `activeLocations/{uid}` — when the live GPS fix falls inside the
     *    home geofence the user's pin shows an "At Home" badge to viewers.
     */
    val homeLatitude: Double = 0.0,
    val homeLongitude: Double = 0.0,
    /** Optional human-readable label for [homeLatitude]/[homeLongitude] (resolved address). */
    val homeLabel: String = "",

    // ── Social links ───────────────────────────────────────────────────────────
    /**
     * Social handles or full URLs the user chooses to share on their profile.
     * Stored verbatim as the user typed them; the UI normalizes bare handles
     * to full `https://…` links when opening them. Empty string = not set.
     */
    val facebookUrl: String = "",
    val instagramUrl: String = "",
    val linkedinUrl: String = ""
) {
    /** Profile is complete when the user has chosen a username. */
    @get:Exclude
    val isProfileComplete: Boolean
        get() = username.isNotBlank()

    /** True when the user has set a home location (non-zero coordinates). */
    @get:Exclude
    val hasHome: Boolean
        get() = homeLatitude != 0.0 || homeLongitude != 0.0
}
