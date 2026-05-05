package com.aegispay.android

import android.app.Application
import com.aegispay.android.auth.AuthRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class AegisPayApplication : Application() {

    /** Application-scoped coroutine scope for startup work. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Inject lateinit var authRepository: AuthRepository

    override fun onCreate() {
        super.onCreate()
        // Restore any persisted auth session before the first Activity starts.
        // The splash screen in MainActivity waits on AuthState.Loading, so this
        // must complete before the UI decides where to navigate.
        appScope.launch {
            authRepository.restoreSession()
        }
    }
}
