package com.ledgerengine.crypto

import com.google.crypto.tink.signature.SignatureConfig

/**
 * Registers Tink's signature primitives (Ed25519 sign/verify key managers) exactly
 * once per process. Must run before any [java.security.KeyPairGenerator]-style
 * KeysetHandle creation or primitive lookup.
 */
internal object TinkInitializer {
    private val registered = java.util.concurrent.atomic.AtomicBoolean(false)

    fun ensureRegistered() {
        if (registered.compareAndSet(false, true)) {
            SignatureConfig.register()
        }
    }
}
