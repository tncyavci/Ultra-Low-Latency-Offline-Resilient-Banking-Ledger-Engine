package com.ledgerengine.domain.repository

import com.ledgerengine.domain.model.Account
import com.ledgerengine.domain.model.AccountId
import com.ledgerengine.domain.model.LedgerEntry
import com.ledgerengine.domain.model.TransactionId
import kotlinx.coroutines.flow.Flow

sealed interface SubmitResult {
    data class Accepted(val entry: LedgerEntry) : SubmitResult
    data class Rejected(val reason: String) : SubmitResult
}

/**
 * Single source of truth for account balances and the transaction ledger.
 * Implementations (see :core:sync) compose the local encrypted store,
 * the crypto signer, and the network sync engine behind this one interface
 * so the domain/UI layers never touch storage or transport directly.
 */
interface LedgerRepository {

    /** Hot stream of an account's materialized balance; emits on every ledger mutation. */
    fun observeAccount(accountId: AccountId): Flow<Account>

    /** Hot stream of transactions for an account, newest first. */
    fun observeTransactions(accountId: AccountId, limit: Int = 200): Flow<List<LedgerEntry>>

    fun observeTransaction(id: TransactionId): Flow<LedgerEntry?>

    /**
     * Writes a new entry to the local append-only ledger, signs it, and enqueues it
     * for background sync. Returns as soon as the entry is durably persisted locally —
     * callers do not block on network round-trips, which is what makes this safe to
     * call while fully offline.
     */
    suspend fun submitTransaction(
        accountId: AccountId,
        counterpartyAccountId: AccountId?,
        direction: com.ledgerengine.domain.model.TransactionDirection,
        amount: com.ledgerengine.domain.model.CurrencyAmount,
        memo: String?,
    ): SubmitResult

    /** Forces an immediate reconciliation pass against the server, if connected. */
    suspend fun reconcile(accountId: AccountId)
}
