package com.ledgerengine.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.ledgerengine.database.account.AccountDao
import com.ledgerengine.database.account.AccountEntity
import com.ledgerengine.sync.ApplicationScope
import com.ledgerengine.sync.RemoteEventReconciler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class LedgerApplication : Application(), Configuration.Provider {

    @Inject lateinit var hiltWorkerFactory: HiltWorkerFactory
    @Inject lateinit var accountDao: AccountDao
    @Inject lateinit var remoteEventReconciler: RemoteEventReconciler
    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(hiltWorkerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        seedDemoAccountIfNeeded()
        applicationScope.launch { remoteEventReconciler.run(sinceServerSequence = null) }
    }

    /** Portfolio-project convenience: real onboarding creates the account server-side on signup. */
    private fun seedDemoAccountIfNeeded() {
        applicationScope.launch {
            accountDao.insertIfAbsent(
                AccountEntity(
                    accountId = DEMO_ACCOUNT_ID,
                    displayName = "Everyday",
                    currencyCode = "EUR",
                    balanceMinorUnits = 0,
                    availableBalanceMinorUnits = 0,
                    lastReconciledHlc = null,
                ),
            )
        }
    }

    companion object {
        const val DEMO_ACCOUNT_ID = "acct-demo-1"
    }
}
