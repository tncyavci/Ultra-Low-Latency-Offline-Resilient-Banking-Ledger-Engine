package com.ledgerengine.crypto

import com.google.crypto.tink.PublicKeyVerify
import com.ledgerengine.domain.model.LedgerEntry
import com.ledgerengine.domain.model.LedgerEntryCanonicalizer
import java.security.GeneralSecurityException

/**
 * Verifies that a [LedgerEntry]'s Ed25519 signature is authentic before it is
 * ever trusted, persisted, or merged by [com.ledgerengine.domain.conflict.ConflictResolutionEngine].
 * Verification failure is never an exception the caller has to remember to
 * catch — it collapses to a plain `false` so it composes directly as the
 * engine's `verifySignature` predicate.
 */
class TransactionVerifier(
    private val keyProvider: VerificationKeyProvider,
) {
    init {
        TinkInitializer.ensureRegistered()
    }

    fun verify(entry: LedgerEntry): Boolean {
        val signature = entry.signature ?: return false
        val keyId = entry.signingKeyId ?: return false
        val handle = keyProvider.keysetHandleFor(keyId) ?: return false

        return try {
            val verifier = handle.getPrimitive(PublicKeyVerify::class.java)
            verifier.verify(signature, LedgerEntryCanonicalizer.canonicalBytes(entry))
            true
        } catch (_: GeneralSecurityException) {
            false
        }
    }

    /** Convenience for wiring directly into the engine: `ConflictResolutionEngine(verifier::verify)`. */
    fun asPredicate(): (LedgerEntry) -> Boolean = ::verify
}
