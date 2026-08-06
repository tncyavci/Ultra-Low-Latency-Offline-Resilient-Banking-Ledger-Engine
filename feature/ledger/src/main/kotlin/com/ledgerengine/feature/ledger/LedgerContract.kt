package com.ledgerengine.feature.ledger

import androidx.compose.runtime.Immutable
import com.ledgerengine.domain.model.AccountId
import com.ledgerengine.domain.model.TransactionDirection
import com.ledgerengine.ui.chart.SpendingPoint
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class TransactionUiModel(
    val id: String,
    val title: String,
    val subtitle: String,
    val amountFormatted: String,
    val isCredit: Boolean,
    val statusLabel: String,
)

/**
 * Single, `@Immutable`-annotated screen state. Compose skips recomposing any
 * subtree reading only an unchanged slice of this class — combined with
 * `ImmutableList` for the collections, the transaction list and the spending
 * chart only redraw the rows/points that actually changed between emissions.
 */
@Immutable
data class LedgerUiState(
    val isLoading: Boolean = true,
    val accountDisplayName: String = "",
    val balanceFormatted: String = "",
    val availableBalanceFormatted: String = "",
    val hasPendingTransactions: Boolean = false,
    val isSyncing: Boolean = false,
    val spendingPoints: ImmutableList<SpendingPoint> = persistentListOf(),
    val transactions: ImmutableList<TransactionUiModel> = persistentListOf(),
    val errorMessage: String? = null,
)

sealed interface LedgerIntent {
    data class LoadAccount(val accountId: AccountId) : LedgerIntent
    data object Refresh : LedgerIntent
    data object DismissError : LedgerIntent
    data class SubmitTransaction(
        val direction: TransactionDirection,
        val amountMinorUnits: Long,
        val memo: String?,
    ) : LedgerIntent
}

sealed interface LedgerEffect {
    data class ShowMessage(val message: String) : LedgerEffect
}
