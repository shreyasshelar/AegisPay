package com.aegispay.android.ui.auth

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aegispay.android.auth.AuthRepository
import com.aegispay.android.auth.AuthState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.authState

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    /** Ask the repository to build the Chrome Custom Tab intent, then invoke [onIntent]. */
    fun buildAuthIntent(onIntent: (Intent) -> Unit) {
        _loginError.value = null   // clear any previous error on a new attempt
        viewModelScope.launch {
            val intent = authRepository.buildAuthIntent()
            onIntent(intent)
        }
    }

    /**
     * Called from [MainActivity] when the Custom Tab redirects back.
     * Sets [loginError] if the auth response results in an Unauthenticated state
     * (user cancelled, network failure, or invalid Keycloak response).
     */
    fun handleAuthResult(intent: Intent) {
        viewModelScope.launch {
            try {
                authRepository.handleAuthResponse(intent)
            } catch (e: Exception) {
                _loginError.value = e.message ?: "Authentication failed. Please try again."
                return@launch
            }
            // handleAuthResponse swallows exceptions internally; detect failure by state
            if (authRepository.authState.value is AuthState.Unauthenticated) {
                _loginError.value = "Sign in failed. Please try again."
            }
        }
    }

    fun clearLoginError() {
        _loginError.value = null
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
        }
    }
}
