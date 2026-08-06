package com.ledgerengine.database

import android.content.Context
import android.util.Base64
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import java.security.SecureRandom

/**
 * Supplies the symmetric passphrase used to open the SQLCipher-encrypted
 * databases. The passphrase itself is a random 256-bit key generated once on
 * first launch and stored, AES-256-GCM-encrypted via Tink, in plain
 * `SharedPreferences`; the AEAD key protecting it is held in the Android
 * Keystore (StrongBox-backed where available) via Tink's
 * `AndroidKeysetManager` — so the passphrase at rest is only ever readable
 * after the OS has unlocked the Keystore-protected key for this app.
 *
 * (`androidx.security:security-crypto`/`EncryptedSharedPreferences` was
 * deprecated without a stable release; this uses the same Tink primitives
 * already relied on for transaction signing instead.)
 */
class DatabasePassphraseProvider(private val context: Context) {

    init {
        AeadConfig.register()
    }

    private val keysetManager: AndroidKeysetManager by lazy {
        AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET_NAME, KEYSET_PREFS_FILE_NAME)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri("$ANDROID_KEYSTORE_URI_PREFIX$MASTER_KEY_ALIAS")
            .build()
    }

    private val aead: Aead by lazy { keysetManager.keysetHandle.getPrimitive(Aead::class.java) }

    fun passphrase(): ByteArray {
        val prefs = context.getSharedPreferences(STORAGE_PREFS_FILE_NAME, Context.MODE_PRIVATE)
        val encoded = prefs.getString(PASSPHRASE_KEY, null)
        if (encoded != null) {
            val ciphertext = Base64.decode(encoded, Base64.NO_WRAP)
            return aead.decrypt(ciphertext, ASSOCIATED_DATA)
        }
        val generated = ByteArray(PASSPHRASE_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val ciphertext = aead.encrypt(generated, ASSOCIATED_DATA)
        prefs.edit()
            .putString(PASSPHRASE_KEY, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .apply()
        return generated
    }

    private companion object {
        const val KEYSET_NAME = "ledger_db_passphrase_keyset"
        const val KEYSET_PREFS_FILE_NAME = "ledger_db_passphrase_keyset_prefs"
        const val MASTER_KEY_ALIAS = "ledger_db_passphrase_master_key"
        const val ANDROID_KEYSTORE_URI_PREFIX = "android-keystore://"
        const val STORAGE_PREFS_FILE_NAME = "ledger_db_secrets"
        const val PASSPHRASE_KEY = "sqlcipher_passphrase_v1"
        const val PASSPHRASE_LENGTH_BYTES = 32
        val ASSOCIATED_DATA = ByteArray(0)
    }
}
