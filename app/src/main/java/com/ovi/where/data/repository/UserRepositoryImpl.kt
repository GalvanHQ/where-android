package com.ovi.where.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.ovi.where.core.common.Resource
import com.ovi.where.core.constants.AppConstants
import com.ovi.where.core.firestore.observeDoc
import com.ovi.where.data.local.dao.UserCacheDao
import com.ovi.where.data.local.entity.toCacheEntity
import com.ovi.where.domain.model.User
import com.ovi.where.domain.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val userCacheDao: UserCacheDao,
    private val lazyFriendshipRepository: dagger.Lazy<com.ovi.where.domain.repository.FriendshipRepository>
) : UserRepository {

    /**
     * Singleton scope for write-through cache populates. We don't await the
     * Room write because the caller already has the User in hand and the
     * UI bound to the cache flow will pick the row up reactively.
     */
    private val cacheScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val currentUid: String?
        get() = firebaseAuth.currentUser?.uid

    /** Upserts every successful Firestore read into the persistent cache so
     *  it survives ViewModel + process recreations. The cache is what the
     *  rest of the app reads from. */
    private fun warmCache(users: List<User>) {
        if (users.isEmpty()) return
        val now = System.currentTimeMillis()
        cacheScope.launch {
            runCatching {
                userCacheDao.upsertAll(
                    users.filter { it.id.isNotBlank() }
                        .map { it.toCacheEntity(now) }
                )
            }
        }
    }

    override suspend fun getUser(userId: String): Resource<User> {
        return try {
            val doc = firestore.collection(AppConstants.FIRESTORE_COLLECTION_USERS)
                .document(userId).get().await()
            val user = doc.toObject(User::class.java)
            if (user != null) {
                warmCache(listOf(user))
                Resource.Success(user)
            } else Resource.Error("User not found")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to fetch user")
        }
    }

    /**
     * Firestore `whereIn` supports a maximum of 10 values.
     * We chunk the list and merge the results.
     */
    override suspend fun getUsers(userIds: List<String>): Resource<List<User>> {
        return try {
            if (userIds.isEmpty()) return Resource.Success(emptyList())
            val users = mutableListOf<User>()
            // Chunk into batches of 10 (Firestore whereIn limit)
            for (chunk in userIds.distinct().chunked(10)) {
                val snapshot = firestore.collection(AppConstants.FIRESTORE_COLLECTION_USERS)
                    .whereIn("id", chunk)
                    .get().await()
                users.addAll(snapshot.toObjects(User::class.java))
            }
            warmCache(users)
            Resource.Success(users)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to fetch users")
        }
    }

    override fun observeUser(userId: String): Flow<User?> {
        return firestore.collection(AppConstants.FIRESTORE_COLLECTION_USERS)
            .document(userId)
            .observeDoc(
                skipPolicy = com.ovi.where.core.firestore.SnapshotSkipPolicy.MISSING_DOC,
            ) { snap ->
                val user = snap.toObject(User::class.java)
                // Warm the persistent cache on every fresh read so VMs
                // and the inbox + meetup sheet have name/photo data
                // immediately available after process death.
                if (user != null) warmCache(listOf(user))
                user
            }
    }

    override suspend fun updateUserStatus(isOnline: Boolean): Resource<Unit> {
        return try {
            val uid = currentUid ?: return Resource.Error("Not authenticated")
            firestore.collection(AppConstants.FIRESTORE_COLLECTION_USERS)
                .document(uid)
                .update(
                    "isOnline", isOnline,
                    "lastSeen", System.currentTimeMillis()
                ).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to update user status")
        }
    }

    /**
     * Searches by both displayName prefix and username prefix, merges and deduplicates results.
     *
     * Profile-visibility filter:
     *  • "hidden" → never appears in results
     *  • "friends" → only appears if the searcher is already a friend
     *  • "everyone" (default) → always visible
     *
     * The check happens client-side because Firestore can't filter result
     * sets by a target field condition without a compound index per query
     * shape, and the existing prefix-search already pulls a small page so
     * the cost of post-filtering is negligible. A determined attacker
     * could still call Firestore directly to bypass this — the visibility
     * promise here is best-effort UX, not a hard security boundary. For a
     * stronger guarantee we'd need a separate `searchableUsers` collection
     * maintained by a Cloud Function trigger.
     */
    override suspend fun searchUsers(query: String): Resource<List<User>> {
        return try {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return Resource.Success(emptyList())

            // Firestore prefix queries are case-sensitive. We search displayName
            // with both the original casing and a capitalized variant to catch
            // "john" matching "John Smith" and "john doe" matching "John Doe".
            val capitalizedQuery = trimmed.replaceFirstChar { it.uppercase() }

            val byNameOriginal = firestore.collection(AppConstants.FIRESTORE_COLLECTION_USERS)
                .whereGreaterThanOrEqualTo("displayName", trimmed)
                .whereLessThanOrEqualTo("displayName", trimmed + "\uf8ff")
                .limit(20).get().await().toObjects(User::class.java)

            val byNameCapitalized = if (capitalizedQuery != trimmed) {
                firestore.collection(AppConstants.FIRESTORE_COLLECTION_USERS)
                    .whereGreaterThanOrEqualTo("displayName", capitalizedQuery)
                    .whereLessThanOrEqualTo("displayName", capitalizedQuery + "\uf8ff")
                    .limit(20).get().await().toObjects(User::class.java)
            } else emptyList()

            val byUsername = firestore.collection(AppConstants.FIRESTORE_COLLECTION_USERS)
                .whereGreaterThanOrEqualTo("username", trimmed.lowercase())
                .whereLessThanOrEqualTo("username", trimmed.lowercase() + "\uf8ff")
                .limit(20).get().await().toObjects(User::class.java)

            // Merge + deduplicate, exclude self
            val uid = currentUid
            val merged = (byNameOriginal + byNameCapitalized + byUsername)
                .distinctBy { it.id }
                .filter { it.id != uid }

            // Resolve the searcher's friend list once so we can apply the
            // friends-only filter below without N round trips.
            val friendIds: Set<String> = runCatching {
                lazyFriendshipRepository.get()
                    .observeFriends()
                    .first()
                    .map { it.friendUid }
                    .toSet()
            }.getOrDefault(emptySet())

            val visible = merged.filter { user ->
                when (user.profileVisibility) {
                    "hidden" -> false
                    "friends" -> user.id in friendIds
                    else -> true // "everyone" or any forward-compat value
                }
            }
            Resource.Success(visible)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to search users")
        }
    }
}
