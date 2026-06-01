package com.ovi.where.data.cache

import com.ovi.where.core.common.Resource
import com.ovi.where.data.local.dao.UserCacheDao
import com.ovi.where.data.local.entity.toCacheEntity
import com.ovi.where.data.local.entity.toDomain
import com.ovi.where.domain.model.User
import com.ovi.where.domain.repository.UserRepository
import dagger.Lazy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Application-wide cache of resolved user profiles.
 *
 * Replaces the per-ViewModel `mutableMapOf<String, User>()` pattern that
 * was rampant throughout the codebase (`ChatViewModel`, `ChatsViewModel`,
 * `GlobalMapViewModel`, etc.). That pattern died with each VM, so process
 * death / low-memory eviction wiped names + photos and the UI fell back
 * to "Unknown User" / "Member" placeholders until Firestore re-fetched —
 * the visible "vanishing" of meetup participants, chat avatars, etc.
 *
 * Architecture:
 *   • **Storage**: Room (`user_cache` table). Survives process death.
 *   • **Reads**: per-uid Flow backed by Room. Any composable observing
 *     [observeUser] or [observeUsers] gets an immediate emission from
 *     cache plus live updates as Firestore catches up.
 *   • **Writes**: [warmUp] / [warmUpMany] one-shot fetch from Firestore
 *     when a uid hasn't been seen yet. Fire-and-forget — callers don't
 *     await the result; the Flow surfaces it when ready.
 *
 * Coalescing: the warm-up path uses [UserCacheDao.cachedIds] to skip
 * uids we already have. Heavy-traffic screens like the global map can
 * call [warmUpMany] every layout pass without flooding Firestore.
 *
 * Why a singleton, not just a UserDao?
 *   The cache also contains the *fetch* side. UI layers should never
 *   touch [UserRepository.getUsers] directly any more — they should ask
 *   the cache, which decides between Room and Firestore.
 *
 * Lazy [UserRepository] dependency: the repository chain has a cycle
 * (UserRepositoryImpl -> FriendshipRepository -> ... -> UserRepository),
 * which Hilt resolves with `dagger.Lazy`.
 */
@Singleton
class UserCache @Inject constructor(
    private val dao: UserCacheDao,
    private val lazyUserRepository: Lazy<UserRepository>,
) {

    private val userRepository: UserRepository
        get() = lazyUserRepository.get()

    // ── Reads ──────────────────────────────────────────────────────────────

    /**
     * Reactive stream of every cached user. Used by ViewModels that want
     * to hydrate an in-memory map at process start.
     *
     * Emits whenever any row in the cache changes, so consumers get live
     * updates as the rest of the app reads new profiles.
     */
    fun observeAllCached(): Flow<List<User>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }
            .distinctUntilChanged()

    /** Suspend read; returns `null` when not cached. */
    suspend fun getCached(uid: String): User? = dao.get(uid)?.toDomain()

    /** Suspend bulk read; only returns cached entries. */
    suspend fun getCachedMany(uids: List<String>): Map<String, User> {
        if (uids.isEmpty()) return emptyMap()
        return dao.getMany(uids).associate { it.id to it.toDomain() }
    }

    // ── Writes / population ────────────────────────────────────────────────

    /**
     * Persists the given [user] into the cache. Called by repositories
     * whenever they read a user from Firestore so the cache stays warm
     * with anything the rest of the app sees.
     */
    suspend fun put(user: User) {
        if (user.id.isBlank()) return
        dao.upsert(user.toCacheEntity())
    }

    /** Bulk version of [put] — single transaction, single REPLACE. */
    suspend fun putAll(users: List<User>) {
        val now = System.currentTimeMillis()
        dao.upsertAll(users.filter { it.id.isNotBlank() }.map { it.toCacheEntity(now) })
    }

    /**
     * Ensures [uid] is cached. If already present (any age), no-op. If
     * absent, fetches from Firestore and stores. Errors are swallowed —
     * the absence will resolve next time the user doc is read elsewhere.
     */
    suspend fun warmUp(uid: String) {
        if (uid.isBlank()) return
        if (dao.get(uid) != null) return
        runCatching {
            when (val res = userRepository.getUser(uid)) {
                is Resource.Success -> res.data?.let { put(it) }
                else -> Unit
            }
        }
    }

    /**
     * Bulk warm-up. Skips uids already in the cache, then fetches the rest
     * via the repository's chunked `getUsers` call.
     */
    suspend fun warmUpMany(uids: List<String>) {
        val unique = uids.filter { it.isNotBlank() }.distinct()
        if (unique.isEmpty()) return
        val cached = dao.cachedIds(unique).toSet()
        val missing = unique.filter { it !in cached }
        if (missing.isEmpty()) return
        runCatching {
            when (val res = userRepository.getUsers(missing)) {
                is Resource.Success -> res.data?.let { putAll(it) }
                else -> Unit
            }
        }
    }

    /** Clears all cached profiles. Called on sign-out. */
    suspend fun clear() = dao.clearAll()
}
