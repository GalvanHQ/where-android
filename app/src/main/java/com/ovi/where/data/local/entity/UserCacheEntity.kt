package com.ovi.where.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ovi.where.domain.model.User

/**
 * Persistent cache of resolved user profiles.
 *
 * Why this exists: previously every ViewModel kept its own
 * `mutableMapOf<String, User>()` to avoid re-fetching profiles for the
 * same uid — but those maps lived only as long as the VM. After process
 * death, low-memory eviction, or even a configuration change in some
 * scenarios, the map was empty and the UI fell back to "Member" /
 * placeholder names + null photos until Firestore re-fetched. That's
 * the visible "vanishing" of meetup names, chat top-bar avatars,
 * conversation row names, etc.
 *
 * Now we cache to Room so the warm-path read is synchronous + survives
 * VM recreation. Population still happens on demand from Firestore (via
 * [com.ovi.where.data.cache.UserCache]); this table is just the local
 * mirror.
 *
 * No TTL: profile data is refreshed whenever the same user's `users/{uid}`
 * doc is read again (the existing observer in `UserRepositoryImpl.observeUser`
 * keeps the row warm), so a stale-then-replaced flow is the worst case.
 *
 * Indexed by `id` (uid). The unique row count is bounded by the number of
 * distinct people the user has ever seen — friends, group members,
 * location sharers, message senders. Realistic ceiling is hundreds, not
 * thousands, so we don't need a TTL or LRU eviction.
 */
@Entity(tableName = "user_cache")
data class UserCacheEntity(
    @PrimaryKey
    val id: String,
    val displayName: String,
    val username: String,
    val email: String,
    val bio: String,
    val photoUrl: String?,
    val isOnline: Boolean,
    val lastSeen: Long,
    /** When this row was last refreshed locally — drives staleness checks. */
    val cachedAt: Long
)

/** Map a domain [User] to its Room cache entity. */
fun User.toCacheEntity(now: Long = System.currentTimeMillis()): UserCacheEntity =
    UserCacheEntity(
        id = id,
        displayName = displayName,
        username = username,
        email = email,
        bio = bio,
        photoUrl = photoUrl,
        isOnline = isOnline,
        lastSeen = lastSeen,
        cachedAt = now,
    )

/** Map a Room cache entity back into a domain [User].
 *  Fields not stored in the cache (createdAt, fcmToken, privacy flags) are
 *  defaulted because consumers of the cache (avatar lookups, name
 *  resolution) only ever read display fields. */
fun UserCacheEntity.toDomain(): User =
    User(
        id = id,
        displayName = displayName,
        username = username,
        email = email,
        bio = bio,
        photoUrl = photoUrl,
        isOnline = isOnline,
        lastSeen = lastSeen,
    )
