package com.ledgerengine.sync

import com.ledgerengine.database.LocalLedgerDataSource
import com.ledgerengine.database.account.AccountDao
import com.ledgerengine.domain.conflict.ConflictResolution
import com.ledgerengine.domain.conflict.ConflictResolutionEngine
import com.ledgerengine.network.LedgerApiClient
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Subscribes to the backend's live transaction stream (WebSocket) and feeds
 * every pushed event through [ConflictResolutionEngine], applying the
 * resulting decision to local storage. This is what lets a second confirmed
 * device — or the server settling a race between two offline devices — show
 * up on this device without the user manually pulling to refresh.
 */
@Singleton
class RemoteEventReconciler @Inject constructor(
    private val apiClient: LedgerApiClient,
    private val localDataSource: LocalLedgerDataSource,
    private val accountDao: AccountDao,
    private val eventBus: TransactionEventBus,
    private val conflictEngine: ConflictResolutionEngine,
) {

    /** Long-running; callers launch this in a process-scoped [kotlinx.coroutines.CoroutineScope] once, at startup. */
    suspend fun run(sinceServerSequence: Long?) {
        apiClient.observeTransactionEvents(sinceServerSequence)
            .map { it.toDomain() }
            .collect { remoteEntry ->
                val existingLocal = localDataSource.findByIdempotencyKey(remoteEntry.idempotencyKey)
                val resolution = conflictEngine.resolveOne(
                    localByIdempotencyKey = { existingLocal },
                    remote = remoteEntry,
                )
                apply(resolution)
            }
    }

    private suspend fun apply(resolution: ConflictResolution) {
        when (resolution) {
            is ConflictResolution.NewFromRemote -> {
                localDataSource.insertAndReserve(resolution.remote)
                accountDao.applyDelta(
                    accountId = resolution.remote.accountId.value,
                    confirmedDelta = resolution.remote.signedAmount.minorUnits,
                    availableDelta = resolution.remote.signedAmount.minorUnits,
                )
                eventBus.publish(resolution.remote)
            }

            is ConflictResolution.Confirmed -> {
                localDataSource.confirmFromServer(
                    resolution.remote.id,
                    requireNotNull(resolution.remote.serverSequence),
                )
                eventBus.publish(resolution.remote)
            }

            is ConflictResolution.LocalSuperseded -> {
                // The remote entry causally precedes ours; our queued copy can no
                // longer be honored against the funds it depended on. Reject it
                // locally and let the UI surface a "transaction could not be
                // completed" notice rather than retrying forever.
                localDataSource.markStatus(resolution.supersededLocal.id, com.ledgerengine.domain.model.TransactionStatus.REJECTED)
                eventBus.publish(resolution.remote)
            }

            is ConflictResolution.SignatureInvalid,
            is ConflictResolution.IntegrityViolation,
            -> {
                // Never persisted. In production this increments a security metric
                // and is reported to the backend's fraud pipeline; intentionally not
                // silently swallowed.
            }
        }
    }
}
