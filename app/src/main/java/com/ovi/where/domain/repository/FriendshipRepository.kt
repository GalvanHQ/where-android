package com.ovi.where.domain.repository

import com.ovi.where.core.common.Resource
import com.ovi.where.domain.model.BlockEntry
import com.ovi.where.domain.model.FriendEntry
import com.ovi.where.domain.model.Friendship
import com.ovi.where.domain.model.FriendshipStatus
import com.ovi.where.domain.model.RequestEntry
import com.ovi.where.domain.model.SharedLocation
import com.ovi.where.domain.model.SocialSummary
import kotlinx.coroutines.flow.Flow

/**
 * Repository for the friendship / social graph subsystem.
 *
 * Design contract (see `people-ux-and-friendship-data-redesign/design.md` §5.1):
 *
 * - **All mutations go through Cloud Functions callables.** The suspend write methods below
 *   (`sendFriendRequest`, `cancelFriendRequest`, `acceptFriendRequest`,
 *   `declineFriendRequest`, `removeFriend`, `blockUser`, `unblockUser`) never issue
 *   client-side Firestore writes — they invoke their matching HTTPS callable on the
 *   backend, which owns the transactional state machine and the denormalized fan-out.
 *   This keeps security rules strict and guarantees consistency across the five
 *   derived documents (pair doc, both friend entries, both inbox/outbox mirrors,
 *   both social summaries).
 *
 * - **Read methods use a single snapshot listener per `observe*` call.** Each
 *   returned `Flow` is `callbackFlow`-backed by exactly one Firestore listener
 *   (sub-collection or single-doc), with no aggregate queries and no per-item
 *   fan-out. When the user is unauthenticated the flow emits an empty value and
 *   closes without opening a listener. Collectors are expected to use
 *   `combine(...)` to join multiple observables rather than re-subscribing.
 *
 * - **`getFriendshipStatus` returns `null` when no document exists.** Absence of
 *   the `friendships/{pairId}` doc is semantically equivalent to
 *   [FriendshipStatus.NONE]; callers should treat `null` as "no relationship"
 *   rather than as an error. The method performs a single deterministic `get`
 *   (zero listener, zero retries) and is safe to call from cold ViewModel init.
 */
interface FriendshipRepository {
    // ─── Writes — routed via Cloud Functions ─────────────────────────
    suspend fun sendFriendRequest(receiverId: String): Resource<Unit>
    suspend fun cancelFriendRequest(receiverId: String): Resource<Unit>
    suspend fun acceptFriendRequest(requesterId: String): Resource<Unit>
    suspend fun declineFriendRequest(requesterId: String): Resource<Unit>
    suspend fun removeFriend(friendId: String): Resource<Unit>
    suspend fun blockUser(userId: String): Resource<Unit>
    suspend fun unblockUser(userId: String): Resource<Unit>

    // ─── Reads — listeners ───────────────────────────────────────────
    fun observeFriends(): Flow<List<FriendEntry>>
    fun observeIncomingRequests(): Flow<List<RequestEntry>>
    fun observeOutgoingRequests(): Flow<List<RequestEntry>>
    fun observeSocialSummary(): Flow<SocialSummary>
    fun observeBlockedUsers(): Flow<List<BlockEntry>>

    // ─── One-shot ────────────────────────────────────────────────────
    suspend fun getFriendshipStatus(otherUserId: String): FriendshipStatus?
    suspend fun getFriendship(otherUserId: String): Friendship?
    fun observeAllFriendLocations(): Flow<List<SharedLocation>>
}
