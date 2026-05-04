package com.aegispay.android.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.aegispay.android.auth.AuthState
import com.aegispay.android.ui.auth.AuthViewModel
import com.aegispay.android.ui.theme.AegisTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()

    /** Launcher for AppAuth Chrome Custom Tab → returns auth code intent */
    private val authLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.let { authViewModel.handleAuthResult(it) }
    }

    /** Request POST_NOTIFICATIONS permission on Android 13+ */
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or denied — FCM works regardless, just no notifications if denied */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Show system splash screen until auth state resolves
        installSplashScreen().setKeepOnScreenCondition {
            authViewModel.authState.value == AuthState.Loading
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request push notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            AegisTheme {
                AegisNavHost(
                    authViewModel = authViewModel,
                    onStartAuthFlow = { startAuthFlow() },
                )
            }
        }
    }

    // Handle the redirect URI coming back from the Custom Tab
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        authViewModel.handleAuthResult(intent)
    }

    private fun startAuthFlow() {
        authViewModel.buildAuthIntent { intent ->
            authLauncher.launch(intent)
        }
    }
}
