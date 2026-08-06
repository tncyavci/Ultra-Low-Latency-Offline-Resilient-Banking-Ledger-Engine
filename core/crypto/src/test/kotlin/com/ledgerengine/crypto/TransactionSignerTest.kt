package com.ledgerengine.crypto

import com.ledgerengine.domain.model.AccountId
import com.ledgerengine.domain.model.CurrencyAmount
import com.ledgerengine.domain.model.IdempotencyKey
import com.ledgerengine.domain.model.LedgerEntry
import com.ledgerengine.domain.model.Money
import com.ledgerengine.domain.model.TransactionDirection
import com.ledgerengine.domain.model.TransactionId
import com.ledgerengine.domain.model.TransactionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionSignerTest {

    private fun unsignedEntry(idempotencyKey: String = "idem-1") = LedgerEntry(
        id = TransactionId("tx-1"),
        idempotencyKey = IdempotencyKey(idempotencyKey),
        accountId = AccountId("acct-1"),
        counterpartyAccountId = AccountId("acct-2"),
        direction = TransactionDirection.DEBIT,
        amount = CurrencyAmount(Money.ofMinorUnits(2599), "EUR"),
        memo = "coffee",
        status = TransactionStatus.PENDING_LOCAL,
        hlcTimestamp = "0000000000001700000000.0000000000.device-a",
        createdAtEpochMillis = 1_700_000_000_000,
        signature = null,
        signingKeyId = null,
        serverSequence = null,
    )

    @Test
    fun `signing attaches a signature, key id and transitions status`() {
        val signingProvider = InMemorySigningKeyProvider(keyId = "device-key-1")
        val signer = TransactionSigner(signingProvider)

        val signed = signer.sign(unsignedEntry())

        assertNotNull(signed.signature)
        assertTrue(signed.signature!!.isNotEmpty())
        assertEquals("device-key-1", signed.signingKeyId)
        assertEquals(TransactionStatus.SIGNED, signed.status)
    }

    @Test(expected = IllegalStateException::class)
    fun `signing an already-signed entry throws`() {
        val signer = TransactionSigner(InMemorySigningKeyProvider())
        val signed = signer.sign(unsignedEntry())
        signer.sign(signed)
    }

    @Test
    fun `verifier accepts a signature produced by the matching public key`() {
        val signingProvider = InMemorySigningKeyProvider(keyId = "device-key-1")
        val signer = TransactionSigner(signingProvider)
        val verificationProvider = InMemoryVerificationKeyProvider().apply {
            trust("device-key-1", signingProvider.publicKeysetHandle())
        }
        val verifier = TransactionVerifier(verificationProvider)

        val signed = signer.sign(unsignedEntry())

        assertTrue(verifier.verify(signed))
    }

    @Test
    fun `verifier rejects a tampered payload`() {
        val signingProvider = InMemorySigningKeyProvider(keyId = "device-key-1")
        val signer = TransactionSigner(signingProvider)
        val verificationProvider = InMemoryVerificationKeyProvider().apply {
            trust("device-key-1", signingProvider.publicKeysetHandle())
        }
        val verifier = TransactionVerifier(verificationProvider)

        val signed = signer.sign(unsignedEntry())
        val tampered = signed.copy(amount = CurrencyAmount(Money.ofMinorUnits(999_999), "EUR"))

        assertFalse(verifier.verify(tampered))
    }

    @Test
    fun `verifier rejects a signature from an untrusted key id`() {
        val signingProvider = InMemorySigningKeyProvider(keyId = "device-key-1")
        val signer = TransactionSigner(signingProvider)
        val verificationProvider = InMemoryVerificationKeyProvider() // never trusted
        val verifier = TransactionVerifier(verificationProvider)

        val signed = signer.sign(unsignedEntry())

        assertFalse(verifier.verify(signed))
    }

    @Test
    fun `verifier rejects an unsigned entry`() {
        val verifier = TransactionVerifier(InMemoryVerificationKeyProvider())
        assertFalse(verifier.verify(unsignedEntry()))
    }

    @Test
    fun `two different in-memory keys never cross-verify`() {
        val providerA = InMemorySigningKeyProvider(keyId = "device-a")
        val providerB = InMemorySigningKeyProvider(keyId = "device-b")
        val signerA = TransactionSigner(providerA)
        val trustOnlyB = InMemoryVerificationKeyProvider().apply {
            trust("device-b", providerB.publicKeysetHandle())
        }
        val verifier = TransactionVerifier(trustOnlyB)

        val signedByA = signerA.sign(unsignedEntry())

        assertFalse(verifier.verify(signedByA))
    }
}
