package com.ledgerengine.domain.model

@JvmInline
value class AccountId(val value: String)

/**
 * Immutable snapshot of an account's derived state. The authoritative source of
 * truth is always the append-only ledger ([LedgerEntry] stream); this is a
 * materialized projection kept for O(1) balance reads on the UI thread.
 *
 * :core:domain deliberately has zero Android/Compose dependency, so this plain
 * `data class` carries no `@Immutable` annotation. The Compose-facing UI state
 * in :feature:ledger wraps/maps this into a type annotated with the real
 * `androidx.compose.runtime.Immutable`, which is what the Compose compiler
 * actually inspects to skip recomposition.
 */
data class Account(
    val id: AccountId,
    val displayName: String,
    val currencyCode: String,
    val balance: Money,
    /** Balance including transactions that are still PENDING/QUEUED locally but not yet confirmed. */
    val availableBalance: Money,
    val lastReconciledHlc: String?,
)
