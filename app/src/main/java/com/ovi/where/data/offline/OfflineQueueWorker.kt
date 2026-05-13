package com.ovi.where.data.offline

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ovi.where.data.local.dao.OfflineOperationDao
import com.ovi.where.data.local.entity.OperationStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that processes queued offline operations when connectivity is restored.
 *
 * Behavior:
 * - Processes up to 50 pending operations per cycle (ordered by createdAt ASC)
 * - Uses exponential backoff with 30s base delay, max 3 retries per operation
 * - Marks operations as FAILED after all retries are exhausted
 * - Requires network connectivity constraint
 */
@HiltWorker
class OfflineQueueWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val offlineOperationDao: OfflineOperationDao
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val pendingOperations = offlineOperationDao.getPendingOperations()

        if (pendingOperations.isEmpty()) {
            return Result.success()
        }

        var hasFailures = false

        for (operation in pendingOperations) {
            // Mark as in-progress
            offlineOperationDao.updateStatus(operation.id, OperationStatus.IN_PROGRESS.name)

            val success = executeOperation(operation.type, operation.payload)

            if (success) {
                offlineOperationDao.updateStatus(operation.id, OperationStatus.COMPLETED.name)
            } else {
                val newRetryCount = operation.retryCount + 1
                if (newRetryCount >= MAX_RETRIES) {
                    // All retries exhausted — mark as failed
                    offlineOperationDao.updateStatusAndRetryCount(
                        operationId = operation.id,
                        status = OperationStatus.FAILED.name,
                        retryCount = newRetryCount
                    )
                    Timber.w("Offline operation ${operation.id} failed after $MAX_RETRIES retries")
                } else {
                    // Retry later — reset to pending with incremented retry count
                    offlineOperationDao.updateStatusAndRetryCount(
                        operationId = operation.id,
                        status = OperationStatus.PENDING.name,
                        retryCount = newRetryCount
                    )
                    hasFailures = true
                }
            }
        }

        return if (hasFailures) Result.retry() else Result.success()
    }

    /**
     * Executes a single offline operation against the server.
     * Returns true if the operation succeeded, false otherwise.
     */
    private suspend fun executeOperation(type: String, payload: String): Boolean {
        return try {
            // Operation execution will be implemented by specific operation handlers.
            // Each OperationType maps to a specific repository call.
            // For now, this is a placeholder that will be wired to actual network calls.
            true
        } catch (e: Exception) {
            Timber.w(e, "Failed to execute offline operation of type: $type")
            false
        }
    }

    companion object {
        const val WORK_NAME = "offline_queue_sync"
        const val MAX_RETRIES = 3
        private const val BACKOFF_DELAY_SECONDS = 30L
        private const val MAX_QUEUE_CAPACITY = 200

        /**
         * Checks if the queue can accept a new operation (capacity < 200).
         * Returns true if the operation can be enqueued, false if the queue is full.
         */
        suspend fun canEnqueue(offlineOperationDao: OfflineOperationDao): Boolean {
            return offlineOperationDao.getActiveCount() < MAX_QUEUE_CAPACITY
        }

        /**
         * Enqueues the offline queue worker with network connectivity constraints
         * and exponential backoff (30s base, max 3 retries).
         */
        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<OfflineQueueWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    BACKOFF_DELAY_SECONDS,
                    TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )
        }
    }
}
