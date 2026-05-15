package com.aegispay.android.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aegispay.android.auth.AuthRepository
import com.aegispay.android.network.AegisApiService
import com.aegispay.android.network.UserRegistrationRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class OnboardingUiState(
    val firstName:    String  = "",
    val lastName:     String  = "",
    val email:        String  = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val done:         Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val api:            AegisApiService,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val idempotencyKey = UUID.randomUUID().toString()

    // ── Field mutations ────────────────────────────────────────────────────────

    fun onFirstNameChange(v: String) = _uiState.update { it.copy(firstName = v, errorMessage = null) }
    fun onLastNameChange(v: String)  = _uiState.update { it.copy(lastName  = v, errorMessage = null) }
    fun onEmailChange(v: String)     = _uiState.update { it.copy(email     = v, errorMessage = null) }

    // ── Validation ─────────────────────────────────────────────────────────────

    val firstNameError: String? get() {
        val v = _uiState.value.firstName
        return if (v.isNotEmpty() && v.trim().length < 2) "At least 2 characters required" else null
    }

    val lastNameError: String? get() {
        val v = _uiState.value.lastName
        return if (v.isNotEmpty() && v.trim().length < 2) "At least 2 characters required" else null
    }

    val emailError: String? get() {
        val v = _uiState.value.email
        if (v.isEmpty()) return null
        val pattern = Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}\$")
        return if (!pattern.matches(v)) "Enter a valid email address" else null
    }

    val isValid: Boolean get() {
        val s = _uiState.value
        return s.firstName.trim().length >= 2 && s.lastName.trim().length >= 2 &&
                s.email.isNotEmpty() && emailError == null &&
                firstNameError == null && lastNameError == null
    }

    // ── Submit ─────────────────────────────────────────────────────────────────

    fun register() {
        if (!isValid) return
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            runCatching {
                api.registerUser(
                    idempotencyKey = idempotencyKey,
                    request = UserRegistrationRequest(
                        firstName = state.firstName.trim(),
                        lastName  = state.lastName.trim(),
                        email     = state.email.trim(),
                    ),
                )
            }
                .onSuccess { profile ->
                    authRepository.completeRegistration(profile.id)
                    _uiState.update { it.copy(isSubmitting = false, done = true) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isSubmitting = false, errorMessage = e.message) }
                }
        }
    }
}
