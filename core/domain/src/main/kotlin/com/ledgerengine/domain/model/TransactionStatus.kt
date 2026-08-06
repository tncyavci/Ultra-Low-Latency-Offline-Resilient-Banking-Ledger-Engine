package com.ledgerengine.domain.model

/**
 * Lifecycle of a transaction from local creation to server confirmation.
 *
 * ```
 * PENDING_LOCAL -> SIGNED -> QUEUED -> SYNCING -> CONFIRMED
 *                                          |
 *                                          +-> REJECTED
 *                                          +-> CONFLICTED -> (resolved) -> CONFIRMED | REJECTED
 * ```
 */
enum class TransactionStatus {
    /** Written to the local append-only ledger, not yet cryptographically signed. */
    PENDING_LOCAL,

    /** Ed25519-signed with the device keyset; immutable and safe to enqueue for sync. */
    SIGNED,

    /** Sitting in the WorkManager offline queue awaiting network connectivity. */
    QUEUED,

    /** In-flight network request to the ledger backend. */
    SYNCING,

    /** Server has accepted and durably persisted the transaction. */
    CONFIRMED,

    /** Server rejected the transaction (e.g. insufficient funds, invalid signature). */
    REJECTED,

    /** Server holds a competing entry for the same idempotency key; awaiting [com.ledgerengine.domain.conflict.ConflictResolutionEngine]. */
    CONFLICTED,
}
