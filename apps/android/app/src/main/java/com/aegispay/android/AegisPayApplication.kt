package com.aegispay.android

import android.app.Application
import androidx.work.Configuration
import com.aegispay.android.BuildConfig
import com.aegispay.android.auth.AuthRepository
import com.aegispay.android.offline.OfflinePaymentQueue
import com.stripe.android.PaymentConfiguration
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class AegisPayApplication : Application(), Configuration.Provider {

    /** Application-scoped coroutine scope for startup work. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var offlinePaymentQueue: OfflinePaymentQueue

    // Injected via OfflineModule; returned as Configuration.Provider impl
    @Inject lateinit var wmConfig: Configuration

    override val workManagerConfiguration: Configuration
        get() = wmConfig

    override fun onCreate() {
        super.onCreate()

        // Initialize Stripe — must be called before any PaymentSheet usage.
        // In production the publishable key is injected by CI via STRIPE_PUBLISHABLE_KEY.
        PaymentConfiguration.init(
            context           = applicationContext,
            publishableKey    = BuildConfig.STRIPE_PUBLISHABLE_KEY,
        )

        // Restore any persisted auth session before the first Activity starts.
        appScope.launch {
            authRepository.restoreSession()
        }
        // Re-schedule offline sync on app start in case there are pending payments
        // that were queued while the device was offline.
        appScope.launch {
            offlinePaymentQueue.scheduleSync()
        }
    }
}
