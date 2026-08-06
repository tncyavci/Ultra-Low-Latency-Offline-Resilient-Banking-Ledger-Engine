package com.ledgerengine.database

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

/**
 * Supplies the symmetric passphrase used to open the SQLCipher-encrypted
 * databases. The passphrase itself is a random 256-bit key generated once on
 * first launch and stored in [EncryptedSharedPreferences], which wraps it
 * with a key held in the Android Keystore (StrongBox-backed where available)
 * — so the passphrase at rest is only ever readable after the OS has
 * unlocked the Keystore-protected master key for this app.
 */
class DatabasePassphraseProvider(private val context: Context) {

    private val encryptedPrefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun passphrase(): ByteArray {
        val encoded = encryptedPrefs.getString(PASSPHRASE_KEY, null)
        if (encoded != null) {
            return android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP)
        }
        val generated = ByteArray(PASSPHRASE_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        encryptedPrefs.edit()
            .putString(PASSPHRASE_KEY, android.util.Base64.encodeToString(generated, android.util.Base64.NO_WRAP))
            .apply()
        return generated
    }

    private companion object {
        const val PREFS_FILE_NAME = "ledger_db_secrets"
        const val PASSPHRASE_KEY = "sqlcipher_passphrase_v1"
        const val PASSPHRASE_LENGTH_BYTES = 32
    }
}
