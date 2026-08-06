package com.ledgerengine.database.account

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Materialized projection of an account's balance, kept up to date inside the
 * same database transaction as every [com.ledgerengine.database.ledger] insert
 * so UI reads never have to aggregate-scan the append-only event log. Room
 * (rather than SQLDelight) hosts this table because its Flow-returning `@Dao`
 * queries plug directly into the Compose/ViewModel layer with the least
 * ceremony for this simple, single-table read path.
 */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val accountId: String,
    val displayName: String,
    val currencyCode: String,
    val balanceMinorUnits: Long,
    val availableBalanceMinorUnits: Long,
    val lastReconciledHlc: String?,
)
