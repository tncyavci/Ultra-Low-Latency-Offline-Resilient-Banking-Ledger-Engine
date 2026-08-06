package com.ledgerengine.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TransactionSyncRequestDto(
    val id: String,
    @SerialName("idempotency_key") val idempotencyKey: String,
    @SerialName("account_id") val accountId: String,
    @SerialName("counterparty_account_id") val counterpartyAccountId: String? = null,
    val direction: String,
    @SerialName("amount_minor_units") val amountMinorUnits: Long,
    @SerialName("currency_code") val currencyCode: String,
    val memo: String? = null,
    @SerialName("hlc_timestamp") val hlcTimestamp: String,
    @SerialName("created_at_epoch_millis") val createdAtEpochMillis: Long,
    /** Base64-encoded Ed25519 signature, see :core:crypto TransactionSigner. */
    val signature: String,
    @SerialName("signing_key_id") val signingKeyId: String,
)

@Serializable
data class TransactionSyncResponseDto(
    val id: String,
    val status: String,
    @SerialName("server_sequence") val serverSequence: Long,
    @SerialName("server_hlc_timestamp") val serverHlcTimestamp: String? = null,
    @SerialName("rejection_reason") val rejectionReason: String? = null,
)

/** Server-pushed event over the WebSocket stream: another device's confirmed transaction, or a reconciliation result. */
@Serializable
data class TransactionEventDto(
    val id: String,
    @SerialName("idempotency_key") val idempotencyKey: String,
    @SerialName("account_id") val accountId: String,
    @SerialName("counterparty_account_id") val counterpartyAccountId: String? = null,
    val direction: String,
    @SerialName("amount_minor_units") val amountMinorUnits: Long,
    @SerialName("currency_code") val currencyCode: String,
    val memo: String? = null,
    @SerialName("hlc_timestamp") val hlcTimestamp: String,
    @SerialName("created_at_epoch_millis") val createdAtEpochMillis: Long,
    val signature: String,
    @SerialName("signing_key_id") val signingKeyId: String,
    @SerialName("server_sequence") val serverSequence: Long,
)
