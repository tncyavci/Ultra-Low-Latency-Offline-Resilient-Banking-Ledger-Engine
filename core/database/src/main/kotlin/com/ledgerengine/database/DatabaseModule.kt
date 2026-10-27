package com.ledgerengine.database

import android.content.Context
import androidx.room.Room
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.ledgerengine.database.account.AccountDao
import com.ledgerengine.database.account.LedgerRoomDatabase
import com.ledgerengine.database.ledger.LedgerEventDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SupportFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun providePassphraseProvider(@ApplicationContext context: Context): DatabasePassphraseProvider =
        DatabasePassphraseProvider(context)

    @Provides
    @Singleton
    fun provideRoomDatabase(
        @ApplicationContext context: Context,
        passphraseProvider: DatabasePassphraseProvider,
    ): LedgerRoomDatabase =
        Room.databaseBuilder(context, LedgerRoomDatabase::class.java, LedgerRoomDatabase.FILE_NAME)
            .openHelperFactory(SupportFactory(passphraseProvider.passphrase()))
            .build()

    @Provides
    @Singleton
    fun provideAccountDao(database: LedgerRoomDatabase): AccountDao = database.accountDao()

    @Provides
    @Singleton
    fun provideSqlDriver(
        @ApplicationContext context: Context,
        passphraseProvider: DatabasePassphraseProvider,
    ): SqlDriver = AndroidSqliteDriver(
        schema = LedgerEventDatabase.Schema,
        context = context,
        name = "ledger_events.db",
        factory = SupportFactory(passphraseProvider.passphrase()),
    )

    @Provides
    @Singleton
    fun provideLocalLedgerDataSource(
        driver: SqlDriver,
        accountDao: AccountDao,
    ): LocalLedgerDataSource = LocalLedgerDataSource(
        eventDatabase = LedgerEventDatabase(driver),
        accountDao = accountDao,
    )
}
