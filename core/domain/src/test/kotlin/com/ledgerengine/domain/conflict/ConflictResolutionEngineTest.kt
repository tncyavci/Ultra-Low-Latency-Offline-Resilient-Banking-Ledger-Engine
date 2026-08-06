package com.ledgerengine.domain.conflict

import com.ledgerengine.domain.clock.HybridLogicalClock
import com.ledgerengine.domain.model.AccountId
import com.ledgerengine.domain.model.CurrencyAmount
import com.ledgerengine.domain.model.IdempotencyKey
import com.ledgerengine.domain.model.LedgerEntry
import com.ledgerengine.domain.model.Money
import com.ledgerengine.domain.model.TransactionDirection
import com.ledgerengine.domain.model.TransactionId
import com.ledgerengine.domain.model.TransactionStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class ConflictResolutionEngineTest {

    private val clock = HybridLogicalClock(nodeId = "device-a", wallClock = { 10_000L })
    private val account = AccountId("acct-1")

    private fun entry(
        idempotencyKey: String,
        status: TransactionStatus = TransactionStatus.QUEUED,
        amountMinorUnits: Long = 500,
        direction: TransactionDirection = TransactionDirection.DEBIT,
        hlc: String = clock.tick().toSortableString(),
        signature: ByteArray? = "sig".toByteArray(),
    ) = LedgerEntry(
        id = TransactionId("tx-$idempotencyKey"),
        idempotencyKey = IdempotencyKey(idempotencyKey),
        accountId = account,
        counterpartyAccountId = null,
        direction = direction,
        amount = CurrencyAmount(Money.ofMinorUnits(amountMinorUnits), "EUR"),
        memo = null,
        status = status,
        hlcTimestamp = hlc,
        createdAtEpochMillis = 10_000L,
        signature = signature,
        signingKeyId = "key-1",
        serverSequence = null,
    )

    @Test
    fun `unknown remote entry with no local match is treated as new`() = runTest {
        val engine = ConflictResolutionEngine(verifySignature = { true })
        val remote = entry("k1")

        val result = engine.resolveOne(localByIdempotencyKey = { null }, remote = remote)

        assertTrue(result is ConflictResolution.NewFromRemote)
    }

    @Test
    fun `matching idempotency key and identical content confirms the local entry`() = runTest {
        val engine = ConflictResolutionEngine(verifySignature = { true })
        val local = entry("k1", status = TransactionStatus.SYNCING)
        val remote = local.copy(status = TransactionStatus.CONFIRMED, serverSequence = 42)

        val result = engine.resolveOne(localByIdempotencyKey = { local }, remote = remote)

        assertTrue(result is ConflictResolution.Confirmed)
    }

    @Test
    fun `invalid signature is never trusted regardless of local state`() = runTest {
        val engine = ConflictResolutionEngine(verifySignature = { false })
        val remote = entry("k1")

        val result = engine.resolveOne(localByIdempotencyKey = { null }, remote = remote)

        assertTrue(result is ConflictResolution.SignatureInvalid)
    }

    @Test
    fun `earlier remote HLC supersedes a later conflicting local entry`() = runTest {
        val engine = ConflictResolutionEngine(verifySignature = { true })
        val earlierHlc = clock.tick().toSortableString()
        val laterHlc = clock.tick().toSortableString()

        val local = entry("k1", hlc = laterHlc, amountMinorUnits = 500)
        val remote = entry("k1", hlc = earlierHlc, amountMinorUnits = 999)

        val result = engine.resolveOne(localByIdempotencyKey = { local }, remote = remote)

        assertTrue(result is ConflictResolution.LocalSuperseded)
    }

    @Test
    fun `same key with different content and later remote HLC is an integrity violation`() = runTest {
        val engine = ConflictResolutionEngine(verifySignature = { true })
        val earlierHlc = clock.tick().toSortableString()
        val laterHlc = clock.tick().toSortableString()

        val local = entry("k1", hlc = earlierHlc, amountMinorUnits = 500)
        val remote = entry("k1", hlc = laterHlc, amountMinorUnits = 999)

        val result = engine.resolveOne(localByIdempotencyKey = { local }, remote = remote)

        assertTrue(result is ConflictResolution.IntegrityViolation)
    }
}
