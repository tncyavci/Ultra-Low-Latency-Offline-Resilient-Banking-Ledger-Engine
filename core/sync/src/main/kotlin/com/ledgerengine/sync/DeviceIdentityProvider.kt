package com.ledgerengine.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stable per-install device identifier. Used both as the [com.ledgerengine.domain.clock.HlcTimestamp]
 * node id (so two devices offline at the same physical millisecond still
 * produce a strict total order) and as the `X-Device-Id` header the backend
 * uses to attribute and rate-limit sync traffic.
 */
@Singleton
class DeviceIdentityProvider @Inject constructor(
    @ApplicationContext context: Context,
) {

    val deviceId: String by lazy {
        val prefs = context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
        prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also { generated ->
            prefs.edit().putString(KEY_DEVICE_ID, generated).apply()
        }
    }

    private companion object {
        const val PREFS_FILE_NAME = "ledger_device_identity"
        const val KEY_DEVICE_ID = "device_id"
    }
}
