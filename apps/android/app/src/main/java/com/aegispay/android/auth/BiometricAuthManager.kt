package com.aegispay.android.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

// ── Biometric availability ────────────────────────────────────────────────────

enum class BiometricAvailability {
    AVAILABLE,
    NO_HARDWARE,
    HARDWARE_UNAVAILABLE,
    NONE_ENROLLED,
    UNSUPPORTED,
}

// ── BiometricAuthManager ──────────────────────────────────────────────────────

/**
 * Wraps [BiometricPrompt] with a suspend-based API and manages the user's
 * opt-in preference in [SharedPreferences].
 *
 * Usage:
 *   val result = biometricAuthManager.authenticate(activity, "Unlock AegisPay")
 *   if (result) { /* authenticated */ }
 */
@Singleton
class BiometricAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private companion object {
        const val PREF_FILE = "aegispay_biometric_prefs"
        const val KEY_ENABLED = "biometric_enabled"
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
    }

    // ── Preference ─────────────────────────────────────────────────────────────

    var isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    // ── Availability ───────────────────────────────────────────────────────────

    fun checkAvailability(): BiometricAvailability {
        val manager = BiometricManager.from(context)
        return when (manager.canAuthenticate(BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS          -> BiometricAvailability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricAvailability.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricAvailability.HARDWARE_UNAVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED  -> BiometricAvailability.NONE_ENROLLED
            else -> BiometricAvailability.UNSUPPORTED
        }
    }

    val isAvailable: Boolean
        get() = checkAvailability() == BiometricAvailability.AVAILABLE

    // ── Authentication ─────────────────────────────────────────────────────────

    /**
     * Shows the system biometric prompt attached to [activity].
     * Returns `true` on success, `false` on any failure (cancelled, lockout, error).
     *
     * Must be called from a coroutine on the main thread.
     */
    suspend fun authenticate(
        activity:    FragmentActivity,
        title:       String = "AegisPay",
        subtitle:    String = "Confirm your identity to continue",
        negativeText: String = "Cancel",
    ): Boolean = suspendCancellableCoroutine { cont ->
        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (cont.isActive) cont.resume(true)
            }
            override fun onAuthenticationFailed() {
                // A single biometric attempt failed — prompt stays open, don't resolve yet.
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // Terminal error (cancelled, lockout, etc.)
                if (cont.isActive) cont.resume(false)
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeText)
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .build()

        cont.invokeOnCancellation { /* prompt cannot be programmatically cancelled */ }

        prompt.authenticate(info)
    }
}
