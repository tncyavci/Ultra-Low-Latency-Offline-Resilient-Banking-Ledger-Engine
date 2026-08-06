package com.ledgerengine.domain.conflict

import com.ledgerengine.domain.clock.HlcTimestamp
import com.ledgerengine.domain.model.LedgerEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Outcome of reconciling one remote (server-authoritative) ledger entry against
 * the current local, possibly-offline ledger state.
 */
sealed interface ConflictResolution {
    val remote: LedgerEntry

    /** Remote confirms an entry we already know about (matching idempotency key); replace local copy. */
    data class Confirmed(override val remote: LedgerEntry, val previousLocal: LedgerEntry?) : ConflictResolution

    /** Genuinely new remote entry (e.g. incoming transfer from another account); insert as-is. */
    data class NewFromRemote(override val remote: LedgerEntry) : ConflictResolution

    /**
     * Both local and remote produced an entry with the same idempotency key but
     * different content — only possible under key reuse/bug, treated as a hard
     * integrity failure and surfaced to the caller rather than silently merged.
     */
    data class IntegrityViolation(override val remote: LedgerEntry, val local: LedgerEntry) : ConflictResolution

    /**
     * A locally-queued, not-yet-synced transaction is causally superseded by a
     * remote transaction that already consumed the funds it depended on (per HLC
     * order). The local entry must be rejected and surfaced to the user rather
     * than silently retried.
     */
    data class LocalSuperseded(override val remote: LedgerEntry, val supersededLocal: LedgerEntry) : ConflictResolution

    /** Signature failed verification — never trust or persist the remote entry. */
    data class SignatureInvalid(override val remote: LedgerEntry) : ConflictResolution
}

/**
 * Reactive, side-effect-free reconciliation engine. It is intentionally decoupled
 * from storage, networking, and crypto primitives — those live in :core:database,
 * :core:network, and :core:crypto respectively — so this class can be exhaustively
 * unit tested with plain in-memory lists.
 *
 * The offline queue producer and the sync engine each publish onto SharedFlows
 * owned by the :core:sync repository implementation; this engine is the `map`/`transform`
 * stage sitting in between, turning "remote entry arrived" events into
 * actionable [ConflictResolution]s.
 */
class ConflictResolutionEngine(
    private val verifySignature: (LedgerEntry) -> Boolean,
) {

    /**
     * @param localByIdempotencyKey lookup for the local ledger, keyed by idempotency key.
     * @param remoteEvents stream of confirmed/pushed entries from the server (e.g. a Ktor
     *   WebSocket subscription or the response of a batched sync call).
     */
    fun reconcile(
        localByIdempotencyKey: suspend (com.ledgerengine.domain.model.IdempotencyKey) -> LedgerEntry?,
        remoteEvents: Flow<LedgerEntry>,
    ): Flow<ConflictResolution> = remoteEvents.map { remote -> resolveOne(localByIdempotencyKey, remote) }

    suspend fun resolveOne(
        localByIdempotencyKey: suspend (com.ledgerengine.domain.model.IdempotencyKey) -> LedgerEntry?,
        remote: LedgerEntry,
    ): ConflictResolution {
        if (!verifySignature(remote)) {
            return ConflictResolution.SignatureInvalid(remote)
        }

        val local = localByIdempotencyKey(remote.idempotencyKey)
            ?: return ConflictResolution.NewFromRemote(remote)

        if (isSameLogicalTransaction(local, remote)) {
            return ConflictResolution.Confirmed(remote, previousLocal = local)
        }

        val localHlc = HlcTimestamp.parse(local.hlcTimestamp)
        val remoteHlc = HlcTimestamp.parse(remote.hlcTimestamp)

        return if (remoteHlc < localHlc) {
            ConflictResolution.LocalSuperseded(remote, supersededLocal = local)
        } else {
            ConflictResolution.IntegrityViolation(remote, local)
        }
    }

    private fun isSameLogicalTransaction(a: LedgerEntry, b: LedgerEntry): Boolean =
        a.accountId == b.accountId &&
            a.direction == b.direction &&
            a.amount == b.amount &&
            a.counterpartyAccountId == b.counterpartyAccountId
}
