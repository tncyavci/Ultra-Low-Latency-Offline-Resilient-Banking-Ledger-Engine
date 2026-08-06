package com.ledgerengine.crypto

import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle

/**
 * Cleartext, in-memory Ed25519 keyset provider. Signature-compatible with the
 * production Android Keystore-backed provider, which makes it suitable for
 * JVM unit tests, Compose previews, and local/dev builds — but the private
 * key material lives in the JVM heap, so it must never be used to sign or
 * hold real user transactions.
 */
class InMemorySigningKeyProvider(
    override val keyId: String = "dev-key-1",
) : SigningKeyProvider {
    init {
        TinkInitializer.ensureRegistered()
    }

    private val handle: KeysetHandle by lazy { KeysetHandle.generateNew(KeyTemplates.get("ED25519")) }

    override fun keysetHandle(): KeysetHandle = handle

    /** The corresponding public keyset, wired into a [VerificationKeyProvider] for round-trip tests. */
    fun publicKeysetHandle(): KeysetHandle = handle.publicKeysetHandle
}

class InMemoryVerificationKeyProvider : VerificationKeyProvider {
    private val trustedKeys = mutableMapOf<String, KeysetHandle>()

    fun trust(keyId: String, publicKeysetHandle: KeysetHandle) {
        trustedKeys[keyId] = publicKeysetHandle
    }

    override fun keysetHandleFor(keyId: String): KeysetHandle? = trustedKeys[keyId]
}
