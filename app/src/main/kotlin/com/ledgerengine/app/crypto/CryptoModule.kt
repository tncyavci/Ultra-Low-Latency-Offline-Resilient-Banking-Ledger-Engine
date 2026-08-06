package com.ledgerengine.app.crypto

import android.content.Context
import com.ledgerengine.crypto.SigningKeyProvider
import com.ledgerengine.crypto.TransactionSigner
import com.ledgerengine.crypto.VerificationKeyProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CryptoModule {

    @Provides
    @Singleton
    fun provideSigningKeyProvider(@ApplicationContext context: Context): SigningKeyProvider =
        AndroidKeystoreSigningKeyProvider(context)

    @Provides
    @Singleton
    fun provideTrustedKeyStore(): TrustedKeyStore = TrustedKeyStore()

    @Provides
    @Singleton
    fun provideVerificationKeyProvider(trustedKeyStore: TrustedKeyStore): VerificationKeyProvider = trustedKeyStore

    @Provides
    @Singleton
    fun provideTransactionSigner(keyProvider: SigningKeyProvider): TransactionSigner = TransactionSigner(keyProvider)
}
