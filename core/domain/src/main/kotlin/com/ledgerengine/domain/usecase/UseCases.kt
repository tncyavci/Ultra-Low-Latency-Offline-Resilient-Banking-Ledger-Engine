package com.ledgerengine.domain.usecase

import com.ledgerengine.domain.model.Account
import com.ledgerengine.domain.model.AccountId
import com.ledgerengine.domain.model.CurrencyAmount
import com.ledgerengine.domain.model.LedgerEntry
import com.ledgerengine.domain.model.TransactionDirection
import com.ledgerengine.domain.repository.LedgerRepository
import com.ledgerengine.domain.repository.SubmitResult
import kotlinx.coroutines.flow.Flow

class ObserveAccountBalanceUseCase(private val repository: LedgerRepository) {
    operator fun invoke(accountId: AccountId): Flow<Account> = repository.observeAccount(accountId)
}

class ObserveTransactionsUseCase(private val repository: LedgerRepository) {
    operator fun invoke(accountId: AccountId, limit: Int = 200): Flow<List<LedgerEntry>> =
        repository.observeTransactions(accountId, limit)
}

/**
 * Validates and submits a new transaction. Overdraft/limit checks happen here
 * (against the locally materialized [Account]) so the user gets instant feedback
 * without waiting on a network round-trip — the server performs the same check
 * again authoritatively and the [com.ledgerengine.domain.conflict.ConflictResolutionEngine]
 * reconciles any divergence once connectivity returns.
 */
class SubmitTransactionUseCase(
    private val repository: LedgerRepository,
) {
    suspend operator fun invoke(
        accountId: AccountId,
        counterpartyAccountId: AccountId?,
        direction: TransactionDirection,
        amount: CurrencyAmount,
        memo: String? = null,
        currentAccount: Account,
        allowOverdraft: Boolean = false,
    ): SubmitResult {
        if (amount.money.isZero || amount.money.isNegative) {
            return SubmitResult.Rejected("Amount must be positive")
        }
        if (direction == TransactionDirection.DEBIT && !allowOverdraft) {
            val resultingBalance = currentAccount.availableBalance - amount.money
            if (resultingBalance.isNegative) {
                return SubmitResult.Rejected("Insufficient available balance")
            }
        }
        return repository.submitTransaction(accountId, counterpartyAccountId, direction, amount, memo)
    }
}

class ReconcileLedgerUseCase(private val repository: LedgerRepository) {
    suspend operator fun invoke(accountId: AccountId) = repository.reconcile(accountId)
}
