package com.ledgerengine.sync

import android.content.Context
import androidx.work.WorkManager
import com.ledgerengine.crypto.TransactionSigner
import com.ledgerengine.crypto.TransactionVerifier
import com.ledgerengine.crypto.VerificationKeyProvider
import com.ledgerengine.domain.clock.HybridLogicalClock
import com.ledgerengine.domain.conflict.ConflictResolutionEngine
import com.ledgerengine.domain.repository.LedgerRepository
import com.ledgerengine.domain.usecase.ObserveAccountBalanceUseCase
import com.ledgerengine.domain.usecase.ObserveTransactionsUseCase
import com.ledgerengine.domain.usecase.ReconcileLedgerUseCase
import com.ledgerengine.domain.usecase.SubmitTransactionUseCase
import com.ledgerengine.network.LedgerApiClient
import com.ledgerengine.network.NetworkConfig
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncBindingsModule {
    @Binds
    @Singleton
    abstract fun bindLedgerRepository(impl: OfflineQueueRepository): LedgerRepository
}

@Module
@InstallIn(SingletonComponent::class)
object SyncModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)

    @Provides
    @Singleton
    fun provideHybridLogicalClock(deviceIdentity: DeviceIdentityProvider): HybridLogicalClock =
        HybridLogicalClock(nodeId = deviceIdentity.deviceId)

    @Provides
    @Singleton
    fun provideConflictResolutionEngine(verifier: TransactionVerifier): ConflictResolutionEngine =
        ConflictResolutionEngine(verifySignature = verifier::verify)

    @Provides
    @Singleton
    fun provideTransactionVerifier(keyProvider: VerificationKeyProvider): TransactionVerifier =
        TransactionVerifier(keyProvider)

    @Provides
    @Singleton
    fun provideNetworkConfig(deviceIdentity: DeviceIdentityProvider): NetworkConfig = NetworkConfig(
        baseUrl = BuildConfigUrls.API_BASE_URL,
        webSocketUrl = BuildConfigUrls.WS_URL,
        deviceId = deviceIdentity.deviceId,
    )

    @Provides
    @Singleton
    fun provideLedgerApiClient(config: NetworkConfig): LedgerApiClient = LedgerApiClient(config)

    @Provides
    fun provideObserveAccountBalanceUseCase(repository: LedgerRepository): ObserveAccountBalanceUseCase =
        ObserveAccountBalanceUseCase(repository)

    @Provides
    fun provideObserveTransactionsUseCase(repository: LedgerRepository): ObserveTransactionsUseCase =
        ObserveTransactionsUseCase(repository)

    @Provides
    fun provideSubmitTransactionUseCase(repository: LedgerRepository): SubmitTransactionUseCase =
        SubmitTransactionUseCase(repository)

    @Provides
    fun provideReconcileLedgerUseCase(repository: LedgerRepository): ReconcileLedgerUseCase =
        ReconcileLedgerUseCase(repository)
}

/**
 * Backend endpoints. In a real deployment these come from the `app` module's
 * per-flavor `BuildConfig` (dev/staging/prod); centralized here as constants
 * to keep this portfolio project self-contained and buildable without secrets.
 */
internal object BuildConfigUrls {
    const val API_BASE_URL = "https://api.ledgerengine.example.com"
    const val WS_URL = "wss://api.ledgerengine.example.com/v1/stream"
}
