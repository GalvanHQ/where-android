package com.ovi.where.data.remote

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks all active Firestore snapshot listeners and enforces a maximum of 10
 * concurrent listeners. When the limit is reached, related document listeners
 * from the same collection are combined into a single collection-level query.
 *
 * Requirement 11.7: THE App SHALL limit the number of concurrently active
 * Firestore snapshot listeners to no more than 10 by combining related document
 * listeners into collection-level queries where multiple documents from the same
 * collection are observed simultaneously.
 */
@Singleton
class FirestoreListenerRegistry @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    companion object {
        const val MAX_CONCURRENT_LISTENERS = 10
    }

    /**
     * Represents a registered listener entry.
     */
    data class ListenerEntry(
        val id: String,
        val collectionPath: String,
        val documentId: String?,
        val registration: ListenerRegistration,
        val isCombined: Boolean = false
    )

    private val activeListeners = ConcurrentHashMap<String, ListenerEntry>()
    private val listenerCounter = AtomicInteger(0)

    /**
     * Returns the current number of active listeners.
     */
    val activeCount: Int
        get() = activeListeners.size

    /**
     * Registers a document-level snapshot listener. If the maximum concurrent
     * listener limit is reached, attempts to combine document listeners from
     * the same collection into a single collection-level query.
     *
     * @param collectionPath The Firestore collection path (e.g., "users", "groups/abc/members")
     * @param documentId The document ID to listen to
     * @param listener The event listener callback for document snapshots
     * @return A [ManagedListenerRegistration] that can be used to unregister the listener,
     *         or null if the listener could not be registered
     */
    fun registerDocumentListener(
        collectionPath: String,
        documentId: String,
        listener: EventListener<DocumentSnapshot>
    ): ManagedListenerRegistration? {
        val listenerId = generateListenerId()

        if (activeListeners.size < MAX_CONCURRENT_LISTENERS) {
            // Under the limit — register directly
            val registration = firestore.collection(collectionPath)
                .document(documentId)
                .addSnapshotListener(listener)

            val entry = ListenerEntry(
                id = listenerId,
                collectionPath = collectionPath,
                documentId = documentId,
                registration = registration
            )
            activeListeners[listenerId] = entry
            Timber.i("Registered document listener: $collectionPath/$documentId (active: ${activeListeners.size})")
            return ManagedListenerRegistration(listenerId, this)
        }

        // Limit reached — try to combine with existing listeners from the same collection
        val combined = tryCombineDocumentListeners(collectionPath, documentId, listener)
        if (combined != null) {
            return combined
        }

        // Cannot combine — queue the request by waiting for a slot
        Timber.w("Listener limit reached ($MAX_CONCURRENT_LISTENERS). Cannot register listener for $collectionPath/$documentId")
        return null
    }

    /**
     * Registers a query-level snapshot listener (e.g., collection queries with filters).
     *
     * @param collectionPath The Firestore collection path
     * @param query The Firestore query to listen to
     * @param listener The event listener callback for query snapshots
     * @return A [ManagedListenerRegistration] that can be used to unregister the listener,
     *         or null if the limit is reached and combination is not possible
     */
    fun registerQueryListener(
        collectionPath: String,
        query: Query,
        listener: EventListener<QuerySnapshot>
    ): ManagedListenerRegistration? {
        val listenerId = generateListenerId()

        if (activeListeners.size < MAX_CONCURRENT_LISTENERS) {
            val registration = query.addSnapshotListener(listener)

            val entry = ListenerEntry(
                id = listenerId,
                collectionPath = collectionPath,
                documentId = null,
                registration = registration
            )
            activeListeners[listenerId] = entry
            Timber.i("Registered query listener: $collectionPath (active: ${activeListeners.size})")
            return ManagedListenerRegistration(listenerId, this)
        }

        Timber.w("Listener limit reached ($MAX_CONCURRENT_LISTENERS). Cannot register query listener for $collectionPath")
        return null
    }

    /**
     * Unregisters a listener by its managed ID.
     *
     * @param listenerId The ID of the listener to unregister
     */
    fun unregister(listenerId: String) {
        val entry = activeListeners.remove(listenerId)
        if (entry != null) {
            entry.registration.remove()
            Timber.i("Unregistered listener: ${entry.collectionPath}/${entry.documentId ?: "query"} (active: ${activeListeners.size})")
        }
    }

    /**
     * Unregisters all active listeners. Useful for cleanup on logout or app termination.
     */
    fun unregisterAll() {
        activeListeners.values.forEach { entry ->
            entry.registration.remove()
        }
        activeListeners.clear()
        Timber.i("Unregistered all listeners")
    }

    /**
     * Returns a snapshot of all currently active listener entries.
     */
    fun getActiveListeners(): List<ListenerEntry> = activeListeners.values.toList()

    /**
     * Checks if a listener with the given ID is currently active.
     */
    fun isActive(listenerId: String): Boolean = activeListeners.containsKey(listenerId)

    /**
     * Attempts to combine document listeners from the same collection into a
     * single collection-level query when the listener limit is reached.
     *
     * This finds existing document listeners for the same collection, removes them,
     * and creates a single collection-level query that covers all documents.
     */
    private fun tryCombineDocumentListeners(
        collectionPath: String,
        newDocumentId: String,
        newListener: EventListener<DocumentSnapshot>
    ): ManagedListenerRegistration? {
        // Find existing document listeners for the same collection
        val sameCollectionEntries = activeListeners.values.filter { entry ->
            entry.collectionPath == collectionPath && entry.documentId != null && !entry.isCombined
        }

        if (sameCollectionEntries.isEmpty()) {
            return null
        }

        // Remove existing individual document listeners for this collection
        sameCollectionEntries.forEach { entry ->
            entry.registration.remove()
            activeListeners.remove(entry.id)
        }

        // Collect all document IDs (existing + new)
        val allDocumentIds = sameCollectionEntries.mapNotNull { it.documentId } + newDocumentId

        // Create a combined collection-level query using whereIn for the document IDs
        // Firestore whereIn supports up to 30 values
        val combinedListenerId = generateListenerId()
        val collectionRef = firestore.collection(collectionPath)

        val combinedRegistration: ListenerRegistration = if (allDocumentIds.size <= 30) {
            collectionRef
                .whereIn(com.google.firebase.firestore.FieldPath.documentId(), allDocumentIds)
                .addSnapshotListener { querySnapshot, error ->
                    if (error != null) {
                        Timber.w(error, "Combined listener error for $collectionPath")
                        return@addSnapshotListener
                    }
                    // Dispatch individual document snapshots to the new listener
                    querySnapshot?.documents?.forEach { docSnapshot ->
                        if (docSnapshot.id == newDocumentId) {
                            newListener.onEvent(docSnapshot, null)
                        }
                    }
                }
        } else {
            // If more than 30 documents, just listen to the entire collection
            collectionRef.addSnapshotListener { querySnapshot, error ->
                if (error != null) {
                    Timber.w(error, "Combined listener error for $collectionPath")
                    return@addSnapshotListener
                }
                querySnapshot?.documents?.forEach { docSnapshot ->
                    if (docSnapshot.id == newDocumentId) {
                        newListener.onEvent(docSnapshot, null)
                    }
                }
            }
        }

        val combinedEntry = ListenerEntry(
            id = combinedListenerId,
            collectionPath = collectionPath,
            documentId = null,
            registration = combinedRegistration,
            isCombined = true
        )
        activeListeners[combinedListenerId] = combinedEntry

        Timber.i(
            "Combined ${allDocumentIds.size} document listeners into collection query for $collectionPath (active: ${activeListeners.size})"
        )
        return ManagedListenerRegistration(combinedListenerId, this)
    }

    private fun generateListenerId(): String {
        return "listener_${listenerCounter.incrementAndGet()}_${System.nanoTime()}"
    }
}

/**
 * A handle to a managed listener registration that allows unregistering
 * through the [FirestoreListenerRegistry].
 */
class ManagedListenerRegistration(
    val id: String,
    private val registry: FirestoreListenerRegistry
) {
    /**
     * Removes this listener from the registry and stops receiving updates.
     */
    fun remove() {
        registry.unregister(id)
    }
}
