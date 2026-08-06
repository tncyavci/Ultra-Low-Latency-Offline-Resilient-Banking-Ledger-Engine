package com.ledgerengine.app.crypto

import android.content.Context
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.crypto.tink.signature.SignatureConfig
import com.ledgerengine.crypto.SigningKeyProvider

/**
 * Production [SigningKeyProvider]: the Ed25519 private key is generated once
 * per device inside the Android Keystore (StrongBox-backed on chipsets that
 * support it) via Tink's [AndroidKeysetManager]. The keyset blob persisted to
 * `SharedPreferences` is itself wrapped by a Keystore-resident AES key, so the
 * on-disk bytes are useless without that hardware-backed key — the raw
 * Ed25519 private key material is never directly readable by this process,
 * only usable through the primitives Tink exposes.
 */
class AndroidKeystoreSigningKeyProvider(context: Context) : SigningKeyProvider {

    init {
        SignatureConfig.register()
    }

    private val keysetManager: AndroidKeysetManager by lazy {
        AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET_NAME, PREFS_FILE_NAME)
            .withKeyTemplate(KeyTemplates.get("ED25519"))
            .withMasterKeyUri("$ANDROID_KEYSTORE_URI_PREFIX$MASTER_KEY_ALIAS")
            .build()
    }

    override val keyId: String
        get() = keysetManager.keysetHandle.keysetInfo.getKeyInfo(0).keyId.toString()

    override fun keysetHandle(): KeysetHandle = keysetManager.keysetHandle

    private companion object {
        const val KEYSET_NAME = "ledger_signing_keyset"
        const val PREFS_FILE_NAME = "ledger_signing_keyset_prefs"
        const val MASTER_KEY_ALIAS = "ledger_signing_master_key"
        const val ANDROID_KEYSTORE_URI_PREFIX = "android-keystore://"
    }
}
