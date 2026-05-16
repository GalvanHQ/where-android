package com.ovi.where.data.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Batches rapid Firestore snapshot emissions into a single Room database write
 * per 500ms fixed window to reduce write amplification and UI churn.
 *
 * Requirement 5.6: WHEN the Firestore snapshot listener emits multiple updates within
 * a 500ms fixed window (measured from the first update in the batch), THE SnapshotDebouncer
 * SHALL coalesce them into a single Room database write operation containing the latest
 * state of each affected document.
 *
 * Thread-safety is achieved via ConcurrentHashMap for the pending writes buffer
 * and a coroutine scope for the timer execution.
 */
@Singleton
class SnapshotDebouncer @Inject constructor() {

    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Constructor for testing that accepts a custom CoroutineScope.
     * This allows tests to use TestScope for virtual time control.
     */
    constructor(testScope: CoroutineScope) : this() {
        scope = testScope
    }

    private val pendingWrites = ConcurrentHashMap<String, Any>()

    @Volatile
    private var debounceJob: Job? = null

    /**
     * Submits a document update to be batched.
     *
     * On the first update in a window, a 500ms timer starts. All subsequent updates
     * within that window are coalesced by documentId (latest state wins). When the
     * timer fires, the [writer] callback is invoked with the full coalesced batch.
     *
     * @param documentId The unique identifier of the document being updated.
     * @param data The latest state of the document.
     * @param writer A suspend function that performs the actual Room write with the
     *              coalesced batch of document states.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> submit(documentId: String, data: T, writer: suspend (Map<String, T>) -> Unit) {
        pendingWrites[documentId] = data as Any

        if (debounceJob == null || debounceJob?.isActive != true) {
            debounceJob = scope.launch {
                delay(DEBOUNCE_WINDOW_MS)
                val batch = HashMap<String, Any>()
                // Drain all pending writes atomically
                val keys = pendingWrites.keys().toList()
                for (key in keys) {
                    pendingWrites.remove(key)?.let { value ->
                        batch[key] = value
                    }
                }
                if (batch.isNotEmpty()) {
                    writer(batch as Map<String, T>)
                }
            }
        }
    }

    /**
     * Returns the number of pending (not yet flushed) document updates.
     * Useful for testing and monitoring.
     */
    val pendingCount: Int
        get() = pendingWrites.size

    /**
     * Returns whether a debounce timer is currently active.
     */
    val isTimerActive: Boolean
        get() = debounceJob?.isActive == true

    companion object {
        /** Fixed debounce window duration in milliseconds. */
        const val DEBOUNCE_WINDOW_MS = 500L
    }
}
