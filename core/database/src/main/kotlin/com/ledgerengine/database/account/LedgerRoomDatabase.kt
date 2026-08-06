package com.ledgerengine.database.account

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [AccountEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class LedgerRoomDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao

    companion object {
        const val FILE_NAME = "ledger_accounts.db"
    }
}
