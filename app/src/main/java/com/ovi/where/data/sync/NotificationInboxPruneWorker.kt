package com.ovi.where.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ovi.where.data.repository.NotificationRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Periodic worker that prunes notification inbox rows older than the
 * retention window (30 days, see [NotificationRepository.DEFAULT_RETENTION_MS]).
 *
 * Why a worker instead of an inline call from [WhereApplication]?
 * The cold-start prune runs once per process launch, which is fine for
 * normal usage but not for installs that are kept resident for weeks
 * (tablets in dock, devices with aggressive doze exemptions). A daily
 * worker guarantees the inbox doesn't grow unbounded regardless of the
 * cold-start cadence.
 *
 * Constraints: none — the work is local-only and cheap. We don't gate
 * on network or charging state because we want the prune to run even
 * when the device is offline.
 */
@HiltWorker
class NotificationInboxPruneWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val notificationRepository: NotificationRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            notificationRepository.prune()
            Result.success()
        } catch (e: Exception) {
            Timber.w(e, "NotificationInboxPruneWorker failed")
            // Retry once — pruning is idempotent so retries are safe.
            if (runAttemptCount < MAX_RETRY_COUNT) Result.retry() else Result.success()
        }
    }

    companion object {
        const val WORK_NAME = "notification_inbox_prune"

        /** 24h cadence — one prune per day is plenty given the 30-day window. */
        private const val INTERVAL_HOURS = 24L
        private const val MAX_RETRY_COUNT = 2

        /**
         * Schedules the periodic prune work. Idempotent — uses
         * [ExistingPeriodicWorkPolicy.KEEP] so re-scheduling on each app
         * start doesn't reset the timer.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<NotificationInboxPruneWorker>(
                INTERVAL_HOURS, TimeUnit.HOURS
            ).build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
        }
    }
}
