package com.ledgerengine.crypto

import com.google.crypto.tink.KeysetHandle

/**
 * Supplies the device's current Ed25519 signing keyset. The production
 * implementation (see the `app` module's `AndroidKeystoreSigningKeyProvider`)
 * generates this once per device via Tink's `AndroidKeysetManager`, backed by
 * the platform Keystore (StrongBox-backed where the chipset supports it) so
 * the raw private key material never leaves secure hardware and never touches
 * this process' heap in cleartext.
 *
 * Kept as an interface here so :core:crypto stays a pure JVM module —
 * fully unit-testable without an Android device/emulator — while still
 * defining the exact contract the platform layer must fulfil.
 */
interface SigningKeyProvider {
    /** Stable identifier for the currently active signing key, embedded in every signed entry. */
    val keyId: String

    /** Private keyset handle containing exactly one Ed25519 signing key. */
    fun keysetHandle(): KeysetHandle
}

/**
 * Resolves the public keyset used to verify a signature produced by [keyId].
 * Backed by a local trust store seeded from the backend's published signing
 * keys (and, symmetrically, the backend resolves the device's public key the
 * same way to verify client-signed transactions on submit).
 */
interface VerificationKeyProvider {
    fun keysetHandleFor(keyId: String): KeysetHandle?
}
