package com.ledgerengine.sync

import com.ledgerengine.crypto.TransactionSigner
import com.ledgerengine.database.LocalLedgerDataSource
import com.ledgerengine.database.account.AccountDao
import com.ledgerengine.domain.clock.HybridLogicalClock
import com.ledgerengine.domain.model.Account
import com.ledgerengine.domain.model.AccountId
import com.ledgerengine.domain.model.CurrencyAmount
import com.ledgerengine.domain.model.IdempotencyKey
import com.ledgerengine.domain.model.LedgerEntry
import com.ledgerengine.domain.model.TransactionDirection
import com.ledgerengine.domain.model.TransactionId
import com.ledgerengine.domain.model.TransactionStatus
import com.ledgerengine.domain.repository.LedgerRepository
import com.ledgerengine.domain.repository.SubmitResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.shareIn
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The concrete [LedgerRepository]: local-first writes, background sync, and
 * reactive fan-out, composed from the four narrowly-scoped modules that own
 * each concern (crypto, storage, network, scheduling) so none of them needs
 * to know about the others.
 */
@Singleton
class OfflineQueueRepository @Inject constructor(
    private val localDataSource: LocalLedgerDataSource,
    private val accountDao: AccountDao,
    private val signer: TransactionSigner,
    private val clock: HybridLogicalClock,
    private val eventBus: TransactionEventBus,
    private val syncScheduler: SyncScheduler,
    @ApplicationScope private val repositoryScope: CoroutineScope,
) : LedgerRepository {

    // One shared, hot flow per account (StateFlow-equivalent via replay=1) so every
    // collector (balance header, spending chart, transaction list) rides the same
    // upstream Room query instead of each opening its own DB cursor.
    private val accountFlows = ConcurrentHashMap<AccountId, Flow<Account>>()

    override fun observeAccount(accountId: AccountId): Flow<Account> =
        accountFlows.getOrPut(accountId) {
            localDataSource.observeAccount(accountId)
                .filterNotNull()
                .shareIn(repositoryScope, SharingStarted.WhileSubscribed(5_000), replay = 1)
        }

    override fun observeTransactions(accountId: AccountId, limit: Int): Flow<List<LedgerEntry>> =
        localDataSource.observeTransactions(accountId, limit)

    override fun observeTransaction(id: TransactionId): Flow<LedgerEntry?> =
        localDataSource.observeTransaction(id)

    override suspend fun submitTransaction(
        accountId: AccountId,
        counterpartyAccountId: AccountId?,
        direction: TransactionDirection,
        amount: CurrencyAmount,
        memo: String?,
    ): SubmitResult {
        val pending = LedgerEntry(
            id = TransactionId(UUID.randomUUID().toString()),
            idempotencyKey = IdempotencyKey(UUID.randomUUID().toString()),
            accountId = accountId,
            counterpartyAccountId = counterpartyAccountId,
            direction = direction,
            amount = amount,
            memo = memo,
            status = TransactionStatus.PENDING_LOCAL,
            hlcTimestamp = clock.tick().toSortableString(),
            createdAtEpochMillis = System.currentTimeMillis(),
            signature = null,
            signingKeyId = null,
            serverSequence = null,
        )

        val signed = signer.sign(pending)

        localDataSource.insertAndReserve(signed)
        applyLocalReservation(signed)
        eventBus.publish(signed)
        syncScheduler.scheduleSyncSoon()

        return SubmitResult.Accepted(signed)
    }

    override suspend fun reconcile(accountId: AccountId) {
        syncScheduler.scheduleImmediateSync()
    }

    private suspend fun applyLocalReservation(entry: LedgerEntry) {
        val delta = entry.signedAmount.minorUnits
        accountDao.applyDelta(entry.accountId.value, confirmedDelta = 0, availableDelta = delta)
    }
}
