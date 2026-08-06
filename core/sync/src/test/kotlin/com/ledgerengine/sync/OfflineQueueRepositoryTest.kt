package com.ledgerengine.sync

import com.ledgerengine.crypto.InMemorySigningKeyProvider
import com.ledgerengine.crypto.TransactionSigner
import com.ledgerengine.database.LocalLedgerDataSource
import com.ledgerengine.database.account.AccountDao
import com.ledgerengine.domain.clock.HybridLogicalClock
import com.ledgerengine.domain.model.AccountId
import com.ledgerengine.domain.model.CurrencyAmount
import com.ledgerengine.domain.model.LedgerEntry
import com.ledgerengine.domain.model.Money
import com.ledgerengine.domain.model.TransactionDirection
import com.ledgerengine.domain.repository.SubmitResult
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineQueueRepositoryTest {

    private val localDataSource: LocalLedgerDataSource = mockk(relaxed = true)
    private val accountDao: AccountDao = mockk(relaxed = true)
    private val syncScheduler: SyncScheduler = mockk(relaxed = true)
    private val eventBus = TransactionEventBus()

    private fun repository(testScope: TestScope) = OfflineQueueRepository(
        localDataSource = localDataSource,
        accountDao = accountDao,
        signer = TransactionSigner(InMemorySigningKeyProvider(keyId = "device-key-1")),
        clock = HybridLogicalClock(nodeId = "device-a"),
        eventBus = eventBus,
        syncScheduler = syncScheduler,
        repositoryScope = testScope.backgroundScope,
    )

    @Test
    fun `submitting a valid transaction signs it, reserves funds locally, and schedules sync`() = runTest {
        val repository = repository(this)
        val entrySlot: CapturingSlot<LedgerEntry> = slot()
        coEvery { localDataSource.insertAndReserve(capture(entrySlot)) } returns Unit

        val result = repository.submitTransaction(
            accountId = AccountId("acct-1"),
            counterpartyAccountId = AccountId("acct-2"),
            direction = TransactionDirection.DEBIT,
            amount = CurrencyAmount(Money.ofMinorUnits(1500), "EUR"),
            memo = "lunch",
        )

        assertTrue(result is SubmitResult.Accepted)
        val signedEntry = (result as SubmitResult.Accepted).entry
        assertTrue(signedEntry.isSigned)
        assertEquals(entrySlot.captured.id, signedEntry.id)

        coVerify(exactly = 1) {
            accountDao.applyDelta(accountId = "acct-1", confirmedDelta = 0, availableDelta = -1500)
        }
        coVerify(exactly = 1) { syncScheduler.scheduleSyncSoon() }
    }

    @Test
    fun `reconcile triggers an immediate sync`() = runTest {
        val repository = repository(this)

        repository.reconcile(AccountId("acct-1"))

        coVerify(exactly = 1) { syncScheduler.scheduleImmediateSync() }
    }
}
