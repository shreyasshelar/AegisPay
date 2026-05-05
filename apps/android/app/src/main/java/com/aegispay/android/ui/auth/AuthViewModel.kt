package com.aegispay.android.ui.auth

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aegispay.android.auth.AuthRepository
import com.aegispay.android.auth.AuthState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.authState

    /** Ask the repository to build the Chrome Custom Tab intent, then invoke [onIntent]. */
    fun buildAuthIntent(onIntent: (Intent) -> Unit) {
        viewModelScope.launch {
            val intent = authRepository.buildAuthIntent()
            onIntent(intent)
        }
    }

    /**
     * Called from [MainActivity] when the Custom Tab redirects back
     * (either via [ActivityResult] or [onNewIntent]).
     */
    fun handleAuthResult(intent: Intent) {
        viewModelScope.launch {
            authRepository.handleAuthResponse(intent)
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
        }
    }
}
