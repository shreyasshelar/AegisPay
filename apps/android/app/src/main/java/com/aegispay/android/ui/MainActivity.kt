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
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.aegispay.android.auth.AuthState
import com.aegispay.android.auth.BiometricAuthManager
import com.aegispay.android.auth.BiometricAuthResult
import com.aegispay.android.ui.auth.AuthViewModel
import com.aegispay.android.ui.theme.AegisColor
import com.aegispay.android.ui.theme.AegisTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()

    @Inject lateinit var biometricAuthManager: BiometricAuthManager

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

    // Whether the biometric lock overlay is showing
    private val isLockedState   = mutableStateOf(false)
    private val lockMessageState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition {
            authViewModel.authState.value == AuthState.Loading
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            AegisTheme {
                val isLocked    by isLockedState
                val lockMessage by lockMessageState

                Box(modifier = Modifier.fillMaxSize()) {
                    AegisNavHost(
                        authViewModel        = authViewModel,
                        onStartAuthFlow      = { startAuthFlow() },
                        biometricAuthManager = biometricAuthManager,
                    )

                    // Biometric lock overlay — shown when app resumes from background
                    AnimatedVisibility(
                        visible = isLocked,
                        enter   = fadeIn(),
                        exit    = fadeOut(),
                    ) {
                        BiometricLockOverlay(
                            message   = lockMessage,
                            onUnlock  = {
                                lockMessageState.value = null
                                triggerBiometricUnlock()
                            },
                            onSignOut = {
                                isLockedState.value   = false
                                lockMessageState.value = null
                                authViewModel.signOut()
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Show biometric lock when app comes to foreground AND user is authenticated
        val authState = authViewModel.authState.value
        if (biometricAuthManager.isEnabled &&
            biometricAuthManager.isAvailable &&
            authState is AuthState.Authenticated
        ) {
            isLockedState.value = true
            triggerBiometricUnlock()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        authViewModel.handleAuthResult(intent)
    }

    private fun startAuthFlow() {
        authViewModel.buildAuthIntent { intent ->
            authLauncher.launch(intent)
        }
    }

    private fun triggerBiometricUnlock() {
        lifecycleScope.launch {
            when (val result = biometricAuthManager.authenticate(
                activity  = this@MainActivity,
                title     = "AegisPay",
                subtitle  = "Confirm your identity to continue",
            )) {
                is BiometricAuthResult.Success -> {
                    isLockedState.value    = false
                    lockMessageState.value = null
                }
                is BiometricAuthResult.UserCancelled -> {
                    // User intentionally dismissed — keep lock visible, no error shown
                    lockMessageState.value = null
                }
                is BiometricAuthResult.LockedOut -> {
                    lockMessageState.value = "Too many attempts. Use your device passcode to unlock."
                }
                is BiometricAuthResult.NotEnrolled -> {
                    lockMessageState.value = "No biometrics enrolled. Enable them in device Settings."
                }
                is BiometricAuthResult.Failed -> {
                    lockMessageState.value = result.message.ifBlank { "Authentication failed. Try again." }
                }
            }
        }
    }
}

// ── Biometric lock overlay ────────────────────────────────────────────────────

@Composable
private fun BiometricLockOverlay(
    message:   String?,
    onUnlock:  () -> Unit,
    onSignOut: () -> Unit,
) {
    Box(
        modifier          = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .semantics { contentDescription = "App is locked. Authenticate to continue." },
        contentAlignment  = Alignment.Center,
    ) {
        Column(
            modifier              = Modifier.fillMaxWidth(),
            horizontalAlignment   = Alignment.CenterHorizontally,
            verticalArrangement   = Arrangement.spacedBy(24.dp),
        ) {
            // Shield logo
            Box(
                modifier         = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(AegisColor.Primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector        = Icons.Default.Lock,
                    contentDescription = null,
                    tint               = AegisColor.Primary,
                    modifier           = Modifier.size(44.dp),
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text  = "AegisPay",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = AegisColor.Text,
                )
                Text(
                    text  = "Your session is locked",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AegisColor.TextMuted,
                )
            }

            // Error/status message
            if (message != null) {
                Text(
                    text  = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = AegisColor.Danger,
                    modifier = Modifier
                        .fillMaxWidth(0.80f)
                        .semantics { contentDescription = message },
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }

            // Unlock button
            Button(
                onClick  = onUnlock,
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .height(52.dp)
                    .semantics { contentDescription = "Unlock with biometrics" },
                shape  = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AegisColor.Primary),
            ) {
                Text(
                    text       = "Unlock",
                    style      = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color      = Color.White,
                )
            }

            // Sign-out fallback
            TextButton(
                onClick  = onSignOut,
                modifier = Modifier.semantics { contentDescription = "Sign out instead" },
            ) {
                Text(
                    text  = "Sign out instead",
                    color = AegisColor.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
