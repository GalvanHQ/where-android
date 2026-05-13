package com.ovi.where.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.ovi.where.core.common.Resource
import com.ovi.where.core.constants.AppConstants
import com.ovi.where.domain.model.Group
import com.ovi.where.domain.model.GroupMember
import com.ovi.where.domain.model.MemberRole
import com.ovi.where.domain.repository.GroupRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : GroupRepository {

    private val currentUid: String?
        get() = firebaseAuth.currentUser?.uid

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

    override fun observeGroupMembers(groupId: String): Flow<List<GroupMember>> = callbackFlow {
        val listener: ListenerRegistration = firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)
            .document(groupId)
            .collection(AppConstants.FIRESTORE_COLLECTION_MEMBERS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val members = snapshot?.toObjects(GroupMember::class.java) ?: emptyList()
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
}
