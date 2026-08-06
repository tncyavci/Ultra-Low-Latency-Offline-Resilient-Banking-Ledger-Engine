package com.ledgerengine.feature.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerengine.domain.model.Account
import com.ledgerengine.domain.model.AccountId
import com.ledgerengine.domain.model.CurrencyAmount
import com.ledgerengine.domain.model.Money
import com.ledgerengine.domain.repository.SubmitResult
import com.ledgerengine.domain.usecase.ObserveAccountBalanceUseCase
import com.ledgerengine.domain.usecase.ObserveTransactionsUseCase
import com.ledgerengine.domain.usecase.ReconcileLedgerUseCase
import com.ledgerengine.domain.usecase.SubmitTransactionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LedgerViewModel @Inject constructor(
    private val observeAccountBalance: ObserveAccountBalanceUseCase,
    private val observeTransactions: ObserveTransactionsUseCase,
    private val submitTransaction: SubmitTransactionUseCase,
    private val reconcileLedger: ReconcileLedgerUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(LedgerUiState())
    val state: StateFlow<LedgerUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<LedgerEffect>(extraBufferCapacity = 8)
    val effects: SharedFlow<LedgerEffect> = _effects.asSharedFlow()

    private var currentAccountId: AccountId? = null
    private var latestAccount: Account? = null

    fun onIntent(intent: LedgerIntent) {
        when (intent) {
            is LedgerIntent.LoadAccount -> loadAccount(intent.accountId)
            is LedgerIntent.Refresh -> refresh()
            is LedgerIntent.DismissError -> _state.update { it.copy(errorMessage = null) }
            is LedgerIntent.SubmitTransaction -> handleSubmit(intent)
        }
    }

    private fun loadAccount(accountId: AccountId) {
        if (currentAccountId == accountId) return
        currentAccountId = accountId

        observeAccountBalance(accountId)
            .onEach { account ->
                latestAccount = account
                _state.update {
                    it.copy(
                        isLoading = false,
                        accountDisplayName = account.displayName,
                        balanceFormatted = formatMoney(account.balance, account.currencyCode),
                        availableBalanceFormatted = formatMoney(account.availableBalance, account.currencyCode),
                        hasPendingTransactions = account.balance != account.availableBalance,
                    )
                }
            }
            .launchIn(viewModelScope)

        observeTransactions(accountId)
            .onEach { entries ->
                _state.update {
                    it.copy(
                        transactions = entries.toUiModels(),
                        spendingPoints = entries.toSpendingPoints(),
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private fun refresh() {
        val accountId = currentAccountId ?: return
        viewModelScope.launch {
            _state.update { it.copy(isSyncing = true) }
            reconcileLedger(accountId)
            _state.update { it.copy(isSyncing = false) }
        }
    }

    private fun handleSubmit(intent: LedgerIntent.SubmitTransaction) {
        val accountId = currentAccountId ?: return
        val account = latestAccount ?: return

        viewModelScope.launch {
            val result = submitTransaction(
                accountId = accountId,
                counterpartyAccountId = null,
                direction = intent.direction,
                amount = CurrencyAmount(Money.ofMinorUnits(intent.amountMinorUnits), account.currencyCode),
                memo = intent.memo,
                currentAccount = account,
            )
            when (result) {
                is SubmitResult.Accepted -> _effects.emit(LedgerEffect.ShowMessage("Transaction queued for sync"))
                is SubmitResult.Rejected -> _state.update { it.copy(errorMessage = result.reason) }
            }
        }
    }
}
