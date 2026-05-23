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
    val locationSharingMode: String = "friends"
) {
    /** Profile is complete when the user has chosen a username. */
    @get:Exclude
    val isProfileComplete: Boolean
        get() = username.isNotBlank()
}
