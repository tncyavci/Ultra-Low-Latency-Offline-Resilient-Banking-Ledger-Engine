package com.ledgerengine.feature.ledger

import com.ledgerengine.domain.model.Account
import com.ledgerengine.domain.model.AccountId
import com.ledgerengine.domain.model.LedgerEntry
import com.ledgerengine.domain.model.Money
import com.ledgerengine.domain.model.TransactionDirection
import com.ledgerengine.domain.repository.SubmitResult
import com.ledgerengine.domain.usecase.ObserveAccountBalanceUseCase
import com.ledgerengine.domain.usecase.ObserveTransactionsUseCase
import com.ledgerengine.domain.usecase.ReconcileLedgerUseCase
import com.ledgerengine.domain.usecase.SubmitTransactionUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LedgerViewModelTest {

    private val observeAccountBalance: ObserveAccountBalanceUseCase = mockk()
    private val observeTransactions: ObserveTransactionsUseCase = mockk()
    private val submitTransaction: SubmitTransactionUseCase = mockk()
    private val reconcileLedger: ReconcileLedgerUseCase = mockk()

    private val account = Account(
        id = AccountId("acct-1"),
        displayName = "Everyday",
        currencyCode = "EUR",
        balance = Money.ofMinorUnits(10_000),
        availableBalance = Money.ofMinorUnits(9_000),
        lastReconciledHlc = null,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(): LedgerViewModel {
        every { observeAccountBalance(account.id) } returns flowOf(account)
        every { observeTransactions(account.id) } returns flowOf(emptyList<LedgerEntry>())
        return LedgerViewModel(observeAccountBalance, observeTransactions, submitTransaction, reconcileLedger)
    }

    @Test
    fun `initial state is loading before any account is requested`() = runTest {
        val viewModel = viewModel()
        assertTrue(viewModel.state.value.isLoading)
    }

    @Test
    fun `loading an account publishes formatted balance and clears loading state`() = runTest {
        val viewModel = viewModel()

        viewModel.onIntent(LedgerIntent.LoadAccount(account.id))
        advanceUntilIdle()

        val loaded = viewModel.state.value
        assertFalse(loaded.isLoading)
        assertEquals("Everyday", loaded.accountDisplayName)
        assertTrue(loaded.hasPendingTransactions)
        assertEquals(formatMoney(account.balance, account.currencyCode), loaded.balanceFormatted)
    }

    @Test
    fun `rejected submission surfaces the rejection reason as an error`() = runTest {
        val viewModel = viewModel()
        viewModel.onIntent(LedgerIntent.LoadAccount(account.id))
        advanceUntilIdle()

        coEvery {
            submitTransaction(
                accountId = account.id,
                counterpartyAccountId = null,
                direction = any(),
                amount = any(),
                memo = any(),
                currentAccount = account,
            )
        } returns SubmitResult.Rejected("Insufficient available balance")

        viewModel.onIntent(
            LedgerIntent.SubmitTransaction(
                direction = TransactionDirection.DEBIT,
                amountMinorUnits = 50_000,
                memo = "too much",
            ),
        )
        advanceUntilIdle()

        assertEquals("Insufficient available balance", viewModel.state.value.errorMessage)
    }

    @Test
    fun `dismissing an error clears it without touching other state`() = runTest {
        val viewModel = viewModel()
        viewModel.onIntent(LedgerIntent.LoadAccount(account.id))
        advanceUntilIdle()
        coEvery {
            submitTransaction(any(), any(), any(), any(), any(), any(), any())
        } returns SubmitResult.Rejected("nope")
        viewModel.onIntent(LedgerIntent.SubmitTransaction(TransactionDirection.DEBIT, 1, null))
        advanceUntilIdle()

        viewModel.onIntent(LedgerIntent.DismissError)

        assertEquals(null, viewModel.state.value.errorMessage)
        assertEquals("Everyday", viewModel.state.value.accountDisplayName)
    }

    @Test
    fun `refresh delegates to the reconcile use case`() = runTest {
        val viewModel = viewModel()
        viewModel.onIntent(LedgerIntent.LoadAccount(account.id))
        advanceUntilIdle()
        coEvery { reconcileLedger(account.id) } returns Unit

        viewModel.onIntent(LedgerIntent.Refresh)
        advanceUntilIdle()

        coVerify(exactly = 1) { reconcileLedger(account.id) }
    }
}
