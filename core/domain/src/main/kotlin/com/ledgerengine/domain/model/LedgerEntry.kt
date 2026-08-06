package com.ledgerengine.domain.model

@JvmInline
value class TransactionId(val value: String)

/**
 * Idempotency key deduplicating client-submitted transactions across retries.
 * Generated once at creation time on-device and never regenerated, so replaying
 * a queued sync request after a crash or a network flap can never double-spend.
 */
@JvmInline
value class IdempotencyKey(val value: String)

enum class TransactionDirection { DEBIT, CREDIT }

/**
 * A single, append-only entry in the offline ledger. Once [signature] is set the
 * entry is immutable — any correction is modeled as a new, opposing entry
 * (standard double-entry bookkeeping), never a mutation of history.
 */
data class LedgerEntry(
    val id: TransactionId,
    val idempotencyKey: IdempotencyKey,
    val accountId: AccountId,
    val counterpartyAccountId: AccountId?,
    val direction: TransactionDirection,
    val amount: CurrencyAmount,
    val memo: String?,
    val status: TransactionStatus,
    /** HLC timestamp (see [com.ledgerengine.domain.clock.HlcTimestamp]), serialized for storage. */
    val hlcTimestamp: String,
    val createdAtEpochMillis: Long,
    /** Ed25519 signature over the canonical byte encoding of this entry; null until signed. */
    val signature: ByteArray?,
    /** Public key id used to produce [signature], for verification and key-rotation lookup. */
    val signingKeyId: String?,
    val serverSequence: Long?,
) {
    val isSigned: Boolean get() = signature != null

    val signedAmount: Money
        get() = when (direction) {
            TransactionDirection.CREDIT -> amount.money
            TransactionDirection.DEBIT -> -amount.money
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LedgerEntry) return false
        return id == other.id &&
            idempotencyKey == other.idempotencyKey &&
            accountId == other.accountId &&
            counterpartyAccountId == other.counterpartyAccountId &&
            direction == other.direction &&
            amount == other.amount &&
            memo == other.memo &&
            status == other.status &&
            hlcTimestamp == other.hlcTimestamp &&
            createdAtEpochMillis == other.createdAtEpochMillis &&
            (signature?.contentEquals(other.signature) ?: (other.signature == null)) &&
            signingKeyId == other.signingKeyId &&
            serverSequence == other.serverSequence
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + idempotencyKey.hashCode()
        result = 31 * result + accountId.hashCode()
        result = 31 * result + (signature?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * Canonical, deterministic byte representation of a [LedgerEntry] used as the
 * message input for Ed25519 signing/verification. Field order and encoding are
 * fixed for the lifetime of the schema — changing this invalidates every
 * previously-issued signature and must be shipped as a new signature version.
 */
object LedgerEntryCanonicalizer {
    private const val FIELD_SEPARATOR = '\u001F' // ASCII unit separator

    fun canonicalBytes(entry: LedgerEntry): ByteArray = buildString {
        append(entry.id.value); append(FIELD_SEPARATOR)
        append(entry.idempotencyKey.value); append(FIELD_SEPARATOR)
        append(entry.accountId.value); append(FIELD_SEPARATOR)
        append(entry.counterpartyAccountId?.value.orEmpty()); append(FIELD_SEPARATOR)
        append(entry.direction.name); append(FIELD_SEPARATOR)
        append(entry.amount.money.minorUnits); append(FIELD_SEPARATOR)
        append(entry.amount.currencyCode); append(FIELD_SEPARATOR)
        append(entry.hlcTimestamp); append(FIELD_SEPARATOR)
        append(entry.createdAtEpochMillis)
    }.toByteArray(Charsets.UTF_8)
}
