package com.ledgerengine.sync

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns all WorkManager scheduling for the offline queue. Two entry points:
 * [scheduleSyncSoon] is called after every local write (debounced by
 * WorkManager's unique-work de-duplication so ten rapid transactions enqueue
 * one flush, not ten), and [scheduleImmediateSync] is for explicit
 * user-triggered "sync now" / pull-to-refresh.
 */
@Singleton
class SyncScheduler @Inject constructor(
    private val workManager: WorkManager,
) {
    fun scheduleSyncSoon() {
        val request = OneTimeWorkRequestBuilder<TransactionSyncWorker>()
            .setConstraints(networkConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, INITIAL_BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    fun scheduleImmediateSync() {
        val request = OneTimeWorkRequestBuilder<TransactionSyncWorker>()
            .setConstraints(networkConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, INITIAL_BACKOFF_SECONDS, TimeUnit.SECONDS)
            .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        workManager.enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    private companion object {
        const val UNIQUE_WORK_NAME = "ledger_offline_queue_sync"
        const val INITIAL_BACKOFF_SECONDS = 10L

        val networkConstraints: Constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}
