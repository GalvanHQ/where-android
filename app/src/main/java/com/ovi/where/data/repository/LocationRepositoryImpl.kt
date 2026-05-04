package com.ovi.where.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.ovi.where.core.common.Resource
import com.ovi.where.core.constants.AppConstants
import com.ovi.where.domain.model.SharedLocation
import com.ovi.where.domain.repository.LocationRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : LocationRepository {

    private val currentUid: String?
        get() = firebaseAuth.currentUser?.uid

    private val activeSharingSessions = mutableMapOf<String, Long>()

    override suspend fun startLocationSharing(groupId: String, durationMinutes: Long): Resource<Unit> {
        return try {
            val uid = currentUid ?: return Resource.Error("Not authenticated")
            val expiryTime = if (durationMinutes > 0) {
                System.currentTimeMillis() + (durationMinutes * 60 * 1000)
            } else {
                Long.MAX_VALUE
            }
            
            activeSharingSessions[groupId] = expiryTime
            
            val locationData = hashMapOf(
                "userId" to uid,
                "groupId" to groupId,
                "isSharingActive" to true,
                "sharingExpiresAt" to expiryTime,
                "timestamp" to System.currentTimeMillis()
            )
            
            firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)
                .document(groupId)
                .collection(AppConstants.FIRESTORE_COLLECTION_LOCATIONS)
                .document(uid)
                .set(locationData, SetOptions.merge())
                .await()
            
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to start location sharing")
        }
    }

    override suspend fun stopLocationSharing(groupId: String): Resource<Unit> {
        return try {
            val uid = currentUid ?: return Resource.Error("Not authenticated")
            
            activeSharingSessions.remove(groupId)
            
            firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)
                .document(groupId)
                .collection(AppConstants.FIRESTORE_COLLECTION_LOCATIONS)
                .document(uid)
                .update(
                    "isSharingActive", false,
                    "timestamp", System.currentTimeMillis()
                )
                .await()
            
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to stop location sharing")
        }
    }

    override suspend fun updateLocation(
        groupId: String,
        latitude: Double,
        longitude: Double,
        accuracy: Float,
        speed: Float,
        bearing: Float
    ): Resource<Unit> {
        return try {
            val uid = currentUid ?: return Resource.Error("Not authenticated")
            
            val locationData = hashMapOf(
                "id" to uid,
                "userId" to uid,
                "groupId" to groupId,
                "latitude" to latitude,
                "longitude" to longitude,
                "accuracy" to accuracy,
                "speed" to speed,
                "bearing" to bearing,
                "timestamp" to System.currentTimeMillis(),
                "isSharingActive" to true
            )
            
            firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)
                .document(groupId)
                .collection(AppConstants.FIRESTORE_COLLECTION_LOCATIONS)
                .document(uid)
                .set(locationData, SetOptions.merge())
                .await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to update location")
        }
    }

    override fun observeGroupLocations(groupId: String): Flow<List<SharedLocation>> = callbackFlow {
        val listener: ListenerRegistration = firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)
            .document(groupId)
            .collection(AppConstants.FIRESTORE_COLLECTION_LOCATIONS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val locations = snapshot?.documents?.mapNotNull { doc ->
                    val location = doc.toObject(SharedLocation::class.java)
                    location?.copy(id = doc.id)
                } ?: emptyList()
                trySend(locations).isSuccess
            }
        awaitClose { listener.remove() }
    }

    override fun observeUserLocation(userId: String, groupId: String): Flow<SharedLocation?> = callbackFlow {
        val listener: ListenerRegistration = firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)
            .document(groupId)
            .collection(AppConstants.FIRESTORE_COLLECTION_LOCATIONS)
            .document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val location = snapshot?.toObject(SharedLocation::class.java)
                trySend(location?.copy(id = snapshot.id)).isSuccess
            }
        awaitClose { listener.remove() }
    }

    override fun isSharingLocation(groupId: String): Boolean {
        val expiryTime = activeSharingSessions[groupId] ?: return false
        return System.currentTimeMillis() < expiryTime
    }

    override fun getSharingExpiryTime(groupId: String): Long? {
        return activeSharingSessions[groupId]
    }
}
