package com.ledgerengine.crypto

import com.google.crypto.tink.PublicKeySign
import com.ledgerengine.domain.model.LedgerEntry
import com.ledgerengine.domain.model.LedgerEntryCanonicalizer
import com.ledgerengine.domain.model.TransactionStatus

/**
 * Produces Ed25519 signatures over the canonical byte encoding of a [LedgerEntry].
 * Every entry must be signed before it is eligible to leave the device
 * ([TransactionStatus.SIGNED]); the backend rejects any submission whose
 * signature does not verify against a known device public key.
 */
class TransactionSigner(
    private val keyProvider: SigningKeyProvider,
) {
    init {
        TinkInitializer.ensureRegistered()
    }

    fun sign(entry: LedgerEntry): LedgerEntry {
        check(entry.signature == null) { "Entry ${entry.id.value} is already signed; entries are immutable once signed" }

        val message = LedgerEntryCanonicalizer.canonicalBytes(entry)
        val signer = keyProvider.keysetHandle().getPrimitive(PublicKeySign::class.java)
        val signature = signer.sign(message)

        return entry.copy(
            signature = signature,
            signingKeyId = keyProvider.keyId,
            status = TransactionStatus.SIGNED,
        )
    }
}
