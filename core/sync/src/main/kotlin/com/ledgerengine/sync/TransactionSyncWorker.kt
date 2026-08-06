package com.ledgerengine.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ledgerengine.database.LocalLedgerDataSource
import com.ledgerengine.database.account.AccountDao
import com.ledgerengine.domain.model.LedgerEntry
import com.ledgerengine.domain.model.TransactionStatus
import com.ledgerengine.network.LedgerApiClient
import com.ledgerengine.network.NetworkResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Drains the local offline queue against the backend. Two retry layers work
 * together here: [LedgerApiClient] already retries transient per-request
 * failures with exponential backoff internally, while this worker's own
 * [androidx.work.BackoffPolicy] (configured in [SyncScheduler]) governs
 * retrying the *entire batch* when there is no connectivity at all — so a
 * dead network doesn't spin the CPU or drain the battery polling.
 */
@HiltWorker
class TransactionSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val localDataSource: LocalLedgerDataSource,
    private val accountDao: AccountDao,
    private val apiClient: LedgerApiClient,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val queued = localDataSource.queuedForSync()
        if (queued.isEmpty()) return Result.success()

        var sawConnectivityFailure = false

        for (entry in queued) {
            when (val outcome = syncOne(entry)) {
                SyncOutcome.Confirmed, SyncOutcome.Rejected -> Unit
                SyncOutcome.ConnectivityFailure -> sawConnectivityFailure = true
            }
        }

        return if (sawConnectivityFailure) Result.retry() else Result.success()
    }

    private suspend fun syncOne(entry: LedgerEntry): SyncOutcome {
        return when (val response = apiClient.submitTransaction(entry.toSyncRequest())) {
            is NetworkResult.Success -> {
                val body = response.value
                if (body.status == "CONFIRMED") {
                    localDataSource.confirmFromServer(entry.id, body.serverSequence)
                    reconcileAccountBalance(entry)
                    SyncOutcome.Confirmed
                } else {
                    localDataSource.markStatus(entry.id, TransactionStatus.REJECTED)
                    SyncOutcome.Rejected
                }
            }
            is NetworkResult.ApiError -> {
                localDataSource.markStatus(entry.id, TransactionStatus.REJECTED)
                SyncOutcome.Rejected
            }
            is NetworkResult.ConnectivityError -> SyncOutcome.ConnectivityFailure
        }
    }

    /**
     * The optimistic local reservation (see `OfflineQueueRepository.applyLocalReservation`)
     * already moved `availableBalanceMinorUnits`; confirmation now moves the
     * confirmed `balanceMinorUnits` by the same amount so the two converge.
     */
    private suspend fun reconcileAccountBalance(entry: LedgerEntry) {
        accountDao.applyDelta(
            accountId = entry.accountId.value,
            confirmedDelta = entry.signedAmount.minorUnits,
            availableDelta = 0,
        )
    }

    private enum class SyncOutcome { Confirmed, Rejected, ConnectivityFailure }
}
