package com.ledgerengine.database.account

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts WHERE accountId = :accountId")
    fun observe(accountId: String): Flow<AccountEntity?>

    @Query("SELECT * FROM accounts WHERE accountId = :accountId")
    suspend fun get(accountId: String): AccountEntity?

    @Upsert
    suspend fun upsert(entity: AccountEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: AccountEntity)

    /**
     * Applies a signed delta to both the confirmed and available balances in one
     * atomic statement, avoiding a read-modify-write race between concurrent
     * ledger writers (the offline queue producer and the sync worker).
     */
    @Query(
        """
        UPDATE accounts
        SET balanceMinorUnits = balanceMinorUnits + :confirmedDelta,
            availableBalanceMinorUnits = availableBalanceMinorUnits + :availableDelta
        WHERE accountId = :accountId
        """,
    )
    suspend fun applyDelta(accountId: String, confirmedDelta: Long, availableDelta: Long)

    @Query("UPDATE accounts SET lastReconciledHlc = :hlc WHERE accountId = :accountId")
    suspend fun updateReconciledHlc(accountId: String, hlc: String)

    @Transaction
    suspend fun applyPendingDebit(accountId: String, amountMinorUnits: Long) {
        applyDelta(accountId, confirmedDelta = 0, availableDelta = -amountMinorUnits)
    }

    @Transaction
    suspend fun applyConfirmedCredit(accountId: String, amountMinorUnits: Long) {
        applyDelta(accountId, confirmedDelta = amountMinorUnits, availableDelta = amountMinorUnits)
    }
}
