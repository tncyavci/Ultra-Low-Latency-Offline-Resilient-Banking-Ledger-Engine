package com.ledgerengine.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.ledgerengine.database.account.AccountDao
import com.ledgerengine.database.account.toDomain
import com.ledgerengine.database.ledger.LedgerEventDatabase
import com.ledgerengine.database.ledger.toDomain
import com.ledgerengine.domain.model.Account
import com.ledgerengine.domain.model.AccountId
import com.ledgerengine.domain.model.IdempotencyKey
import com.ledgerengine.domain.model.LedgerEntry
import com.ledgerengine.domain.model.TransactionId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Facade over the two-layer local store: the SQLDelight append-only event log
 * (source of truth) and the Room-backed [AccountDao] projection (fast reads).
 * Every mutating call updates both within the queries' own transaction scope
 * so the two never observably diverge from a UI collector's point of view.
 */
@Singleton
class LocalLedgerDataSource @Inject constructor(
    private val eventDatabase: LedgerEventDatabase,
    private val accountDao: AccountDao,
) {
    private val queries get() = eventDatabase.ledgerEntryQueries

    fun observeAccount(accountId: AccountId): Flow<Account?> =
        accountDao.observe(accountId.value).map { it?.toDomain() }

    fun observeTransactions(accountId: AccountId, limit: Int): Flow<List<LedgerEntry>> =
        queries.selectByAccount(accountId.value, limit.toLong())
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)

    fun observeTransaction(id: TransactionId): Flow<LedgerEntry?> =
        queries.selectById(id.value)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .map { it?.toDomain() }
            .flowOn(Dispatchers.IO)

    suspend fun findByIdempotencyKey(key: IdempotencyKey): LedgerEntry? =
        queries.selectByIdempotencyKey(key.value).executeAsOneOrNull()?.toDomain()

    suspend fun queuedForSync(): List<LedgerEntry> =
        queries.selectQueuedForSync().executeAsList().map { it.toDomain() }

    /** Persists a newly-created (already signed) entry and atomically reserves its funds against availableBalance. */
    suspend fun insertAndReserve(entry: LedgerEntry) {
        eventDatabase.transaction {
            queries.insertEntry(
                id = entry.id.value,
                idempotency_key = entry.idempotencyKey.value,
                account_id = entry.accountId.value,
                counterparty_account_id = entry.counterpartyAccountId?.value,
                direction = entry.direction.name,
                amount_minor_units = entry.amount.money.minorUnits,
                currency_code = entry.amount.currencyCode,
                memo = entry.memo,
                status = entry.status.name,
                hlc_timestamp = entry.hlcTimestamp,
                created_at_epoch_millis = entry.createdAtEpochMillis,
                signature = entry.signature,
                signing_key_id = entry.signingKeyId,
                server_sequence = entry.serverSequence,
            )
        }
    }

    suspend fun markStatus(id: TransactionId, status: com.ledgerengine.domain.model.TransactionStatus) {
        queries.updateAfterServerConfirmation(status.name, null, id.value)
    }

    suspend fun confirmFromServer(id: TransactionId, serverSequence: Long) {
        queries.updateAfterServerConfirmation(
            com.ledgerengine.domain.model.TransactionStatus.CONFIRMED.name,
            serverSequence,
            id.value,
        )
    }
}
