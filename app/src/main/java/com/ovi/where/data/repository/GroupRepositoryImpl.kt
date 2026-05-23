package com.ovi.where.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.ovi.where.core.common.Resource
import com.ovi.where.core.constants.AppConstants
import com.ovi.where.data.local.CacheStalenessChecker
import com.ovi.where.domain.model.Group
import com.ovi.where.domain.model.GroupMember
import com.ovi.where.domain.model.MemberRole
import com.ovi.where.domain.repository.GroupRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import com.ovi.where.data.util.Resource as DataResource

@Singleton
class GroupRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val cacheStalenessChecker: CacheStalenessChecker
) : GroupRepository {

    private val currentUid: String?
        get() = firebaseAuth.currentUser?.uid

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** In-memory cache for group members (since no Room entity exists for group members) */
    private val groupMembersCache = ConcurrentHashMap<String, MutableStateFlow<List<GroupMember>>>()

    override suspend fun createGroup(name: String, description: String, avatarUrl: String?): Resource<Group> {
        return try {
            val uid = currentUid ?: return Resource.Error("Not authenticated")
            val groupId = UUID.randomUUID().toString()
            val inviteCode = UUID.randomUUID().toString().take(8).uppercase()
            val now = System.currentTimeMillis()

            val group = Group(
                id = groupId,
                name = name,
                description = description,
                createdBy = uid,
                createdAt = now,
                memberCount = 1,
                inviteCode = inviteCode,
                avatarUrl = avatarUrl
            )

            val batch = firestore.batch()

            val groupRef = firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)
                .document(groupId)
            batch.set(groupRef, group)

            val member = GroupMember(
                id = uid,
                userId = uid,
                groupId = groupId,
                role = MemberRole.ADMIN,
                joinedAt = now
            )
            val memberRef = groupRef.collection(AppConstants.FIRESTORE_COLLECTION_MEMBERS)
                .document(uid)
            batch.set(memberRef, member)

            batch.update(groupRef, "memberIds", FieldValue.arrayUnion(uid))

            batch.commit().await()
            Resource.Success(group)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to create group")
        }
    }

    override suspend fun getGroup(groupId: String): Resource<Group> {
        return try {
            val doc = firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)
                .document(groupId)
                .get()
                .await()
            val group = doc.toObject(Group::class.java)
            if (group != null) Resource.Success(group)
            else Resource.Error("Group not found")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to fetch group")
        }
    }

    override suspend fun getUserGroups(): Resource<List<Group>> {
        return try {
            val uid = currentUid ?: return Resource.Error("Not authenticated")
            
            val groups = firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)
                .whereArrayContains("memberIds", uid)
                .get()
                .await()
                .toObjects(Group::class.java)
            Resource.Success(groups)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to fetch groups")
        }
    }

    override suspend fun joinGroupWithCode(inviteCode: String): Resource<Group> {
        return try {
            val uid = currentUid ?: return Resource.Error("Not authenticated")
            
            val query = firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)
                .whereEqualTo("inviteCode", inviteCode)
                .get()
                .await()
            
            if (query.isEmpty) {
                return Resource.Error("Invalid invite code")
            }
            
            val groupDoc = query.documents.first()
            val group = groupDoc.toObject(Group::class.java)
                ?: return Resource.Error("Group not found")
            
            if (group.memberIds.contains(uid)) {
                return Resource.Success(group)
            }
            
            val batch = firestore.batch()
            
            val member = GroupMember(
                id = uid,
                userId = uid,
                groupId = group.id,
                role = MemberRole.MEMBER,
                joinedAt = System.currentTimeMillis()
            )
            
            val memberRef = firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)
                .document(group.id)
                .collection(AppConstants.FIRESTORE_COLLECTION_MEMBERS)
                .document(uid)
            batch.set(memberRef, member)
            
            val groupRef = firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)
                .document(group.id)
            batch.update(groupRef, "memberIds", FieldValue.arrayUnion(uid))
            batch.update(groupRef, "memberCount", FieldValue.increment(1))
            
            batch.commit().await()
            
            Resource.Success(group)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to join group")
        }
    }

    override suspend fun leaveGroup(groupId: String): Resource<Unit> {
        return try {
            val uid = currentUid ?: return Resource.Error("Not authenticated")
            
            val batch = firestore.batch()
            
            val memberRef = firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)
                .document(groupId)
                .collection(AppConstants.FIRESTORE_COLLECTION_MEMBERS)
                .document(uid)
            batch.delete(memberRef)
            
            val groupRef = firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)
                .document(groupId)
            batch.update(groupRef, "memberIds", FieldValue.arrayRemove(uid))
            batch.update(groupRef, "memberCount", FieldValue.increment(-1))
            
            batch.commit().await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to leave group")
        }
    }

    override suspend fun deleteGroup(groupId: String): Resource<Unit> {
        return try {
            val batch = firestore.batch()
            
            val membersSnapshot = firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)
                .document(groupId)
                .collection(AppConstants.FIRESTORE_COLLECTION_MEMBERS)
                .get()
                .await()
            for (doc in membersSnapshot.documents) {
                batch.delete(doc.reference)
            }
            
            val locationsSnapshot = firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)
                .document(groupId)
                .collection(AppConstants.FIRESTORE_COLLECTION_LOCATIONS)
                .get()
                .await()
            for (doc in locationsSnapshot.documents) {
                batch.delete(doc.reference)
            }
            
            val groupRef = firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)
                .document(groupId)
            batch.delete(groupRef)
            
            batch.commit().await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to delete group")
        }
    }

    override suspend fun updateGroup(groupId: String, name: String, description: String): Resource<Group> {
        return try {
            val updates = hashMapOf<String, Any>(
                "name" to name,
                "description" to description
            )
            firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)
                .document(groupId)
                .update(updates)
                .await()
            val doc = firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)
                .document(groupId)
                .get()
                .await()
            val group = doc.toObject(Group::class.java)
            if (group != null) Resource.Success(group)
            else Resource.Error("Group not found")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to update group")
        }
    }

    override suspend fun setGroupConversationId(
        groupId: String,
        conversationId: String
    ): Resource<Unit> = try {
        firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)
            .document(groupId)
            .update("conversationId", conversationId)
            .await()
        Resource.Success(Unit)
    } catch (e: Exception) {
        Resource.Error(e.message ?: "Failed to set conversation id")
    }

    override suspend fun setGroupAvatar(
        groupId: String,
        avatarUrl: String
    ): Resource<Unit> = try {
        firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)
            .document(groupId)
            .update("avatarUrl", avatarUrl)
            .await()
        Resource.Success(Unit)
    } catch (e: Exception) {
        Resource.Error(e.message ?: "Failed to set group avatar")
    }

    override fun observeGroupMembers(groupId: String): Flow<List<GroupMember>> = callbackFlow {
        val listener: ListenerRegistration = firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)
            .document(groupId)
            .collection(AppConstants.FIRESTORE_COLLECTION_MEMBERS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                // Cache-snapshot guard: an empty cache snapshot doesn't
                // prove the group is empty (a real group always has at
                // least one member). Suppress so the meetup sheet's member
                // avatars don't briefly disappear on cold start.
                val isFromCache = snapshot?.metadata?.isFromCache == true
                val members = snapshot?.toObjects(GroupMember::class.java) ?: emptyList()
                if (isFromCache && members.isEmpty()) {
                    Timber.d("observeGroupMembers: ignoring empty cache snapshot for %s", groupId)
                    return@addSnapshotListener
                }

                trySend(members).isSuccess
            }
        awaitClose { listener.remove() }
    }

    override suspend fun addMember(groupId: String, userId: String): Resource<Unit> {
        return try {
            val member = GroupMember(
                id = userId,
                userId = userId,
                groupId = groupId,
                role = MemberRole.MEMBER,
                joinedAt = System.currentTimeMillis()
            )
            
            val batch = firestore.batch()
            
            val memberRef = firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)
                .document(groupId)
                .collection(AppConstants.FIRESTORE_COLLECTION_MEMBERS)
                .document(userId)
            batch.set(memberRef, member)
            
            val groupRef = firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)
                .document(groupId)
            batch.update(groupRef, "memberIds", FieldValue.arrayUnion(userId))
            batch.update(groupRef, "memberCount", FieldValue.increment(1))
            
            batch.commit().await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to add member")
        }
    }

    override suspend fun removeMember(groupId: String, userId: String): Resource<Unit> {
        return try {
            val batch = firestore.batch()
            
            val memberRef = firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)
                .document(groupId)
                .collection(AppConstants.FIRESTORE_COLLECTION_MEMBERS)
                .document(userId)
            batch.delete(memberRef)
            
            val groupRef = firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)
                .document(groupId)
            batch.update(groupRef, "memberIds", FieldValue.arrayRemove(userId))
            batch.update(groupRef, "memberCount", FieldValue.increment(-1))
            
            batch.commit().await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to remove member")
        }
    }

    override suspend fun updateMemberRole(groupId: String, userId: String, role: MemberRole): Resource<Unit> {
        return try {
            firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)
                .document(groupId)
                .collection(AppConstants.FIRESTORE_COLLECTION_MEMBERS)
                .document(userId)
                .update("role", role.name)
                .await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to update member role")
        }
    }

    // ─── NetworkBoundResource Pattern (Requirements 11.1, 11.2, 11.3, 11.6) ───

    /**
     * Returns group members using the NetworkBoundResource pattern:
     * 1. Serves in-memory cached members immediately (Loading state)
     * 2. Checks staleness via CacheStalenessChecker (5-minute threshold)
     * 3. Fetches from Firestore if stale
     * 4. Updates in-memory cache on success (Success state)
     * 5. Serves stale cache on failure (Error state with cached data)
     *
     * Since there's no Room entity for group members, we use an in-memory
     * MutableStateFlow as the cache layer, backed by CacheStalenessChecker
     * for staleness tracking.
     *
     * This method should only be called from ViewModel init blocks or explicit user actions,
     * NOT from Compose recomposition (Requirement 11.1).
     */
    override fun getGroupMembersResource(groupId: String): Flow<DataResource<List<GroupMember>>> {
        val cacheKey = "group_members_$groupId"
        val cachedFlow = groupMembersCache.getOrPut(groupId) {
            MutableStateFlow(emptyList())
        }

        return flow {
            // Step 1: Emit Loading with current cached data
            val cachedData = cachedFlow.value
            emit(DataResource.Loading(cachedData))

            // Step 2: Check if we should fetch
            val shouldFetch = cachedData.isEmpty() || cacheStalenessChecker.shouldFetch(cacheKey)

            if (shouldFetch) {
                try {
                    // Step 3: Fetch from Firestore
                    val members = firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)
                        .document(groupId)
                        .collection(AppConstants.FIRESTORE_COLLECTION_MEMBERS)
                        .get()
                        .await()
                        .toObjects(GroupMember::class.java)

                    // Step 4: Update cache on success
                    cachedFlow.value = members
                    cacheStalenessChecker.updateMetadata(cacheKey)
                    emit(DataResource.Success(members))
                } catch (throwable: Throwable) {
                    // Step 5: Serve stale cache on failure
                    Timber.w(throwable, "Failed to fetch group members for $groupId, serving stale cache")
                    emit(DataResource.Error(throwable, cachedData))
                    // Schedule retry after ≤60 seconds (Requirement 11.6)
                    repositoryScope.launch {
                        delay(RETRY_DELAY_MS)
                        cacheStalenessChecker.updateMetadata(cacheKey, currentTimeMs = 0L)
                    }
                }
            } else {
                // Cache is fresh, serve it directly
                emit(DataResource.Success(cachedData))
            }
        }
    }

    companion object {
        /** Maximum delay before retrying a failed fetch of stale data (Requirement 11.6) */
        private const val RETRY_DELAY_MS = 60_000L
    }

    // ─── Mute Member (Requirement 15.2, 15.3) ────────────────────────────────

    override suspend fun muteMember(groupId: String, userId: String): Resource<Unit> {
        return try {
            firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)
                .document(groupId)
                .collection(AppConstants.FIRESTORE_COLLECTION_MEMBERS)
                .document(userId)
                .update("isMuted", true)
                .await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to mute member")
        }
    }

    // ─── Invite Link (Requirement 15.5, 15.6) ────────────────────────────────

    override suspend fun getInviteLink(groupId: String): Resource<String> {
        return try {
            val doc = firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)
                .document(groupId)
                .get()
                .await()
            val group = doc.toObject(Group::class.java)
            if (group != null && group.inviteCode.isNotEmpty()) {
                Resource.Success("https://where.app/join/${group.inviteCode}")
            } else {
                Resource.Error("Invite link not available")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to retrieve invite link")
        }
    }
}
