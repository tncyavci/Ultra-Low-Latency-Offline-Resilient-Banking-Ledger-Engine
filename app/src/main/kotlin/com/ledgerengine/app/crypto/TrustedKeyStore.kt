package com.ledgerengine.app.crypto

import com.google.crypto.tink.CleartextKeysetHandle
import com.google.crypto.tink.JsonKeysetReader
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.signature.SignatureConfig
import com.ledgerengine.crypto.VerificationKeyProvider
import java.util.concurrent.ConcurrentHashMap

/**
 * Local cache of public signing keys the device trusts for verifying incoming
 * transactions: the backend's own signing key(s), and — for peer-to-peer
 * confirmation of another device's offline transaction — any counterparty
 * public keys the backend has vouched for. Ed25519 public keys are not
 * secret, so [CleartextKeysetHandle] (rather than an encrypted/Keystore-backed
 * handle) is the correct, Tink-documented way to hold them.
 *
 * Population is out of scope for this sample (in production: a `/v1/keys`
 * sync call seeds this on startup and on `keyId` cache misses); [trust] is
 * exposed directly so it can be wired to that call site.
 */
class TrustedKeyStore : VerificationKeyProvider {

    init {
        SignatureConfig.register()
    }

    private val trustedKeys = ConcurrentHashMap<String, KeysetHandle>()

    fun trust(keyId: String, publicKeysetJson: String) {
        trustedKeys[keyId] = CleartextKeysetHandle.read(JsonKeysetReader.withString(publicKeysetJson))
    }

    override fun keysetHandleFor(keyId: String): KeysetHandle? = trustedKeys[keyId]
}
