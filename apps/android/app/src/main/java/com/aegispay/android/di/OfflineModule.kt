package com.aegispay.android.di

import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.aegispay.android.offline.OfflineDatabase
import com.aegispay.android.offline.OfflinePaymentDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object OfflineModule {

    @Provides
    @Singleton
    fun provideOfflineDatabase(@ApplicationContext context: Context): OfflineDatabase =
        OfflineDatabase.getInstance(context)

    @Provides
    @Singleton
    fun provideOfflinePaymentDao(db: OfflineDatabase): OfflinePaymentDao =
        db.offlinePaymentDao()

    /**
     * Provide a WorkManager [Configuration] that uses Hilt's [HiltWorkerFactory]
     * so workers can receive injected dependencies.
     *
     * The [AegisPayApplication] must implement [androidx.work.Configuration.Provider]
     * and return this configuration (see Application class update).
     */
    @Provides
    @Singleton
    fun provideWorkManagerConfiguration(workerFactory: HiltWorkerFactory): Configuration =
        Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
