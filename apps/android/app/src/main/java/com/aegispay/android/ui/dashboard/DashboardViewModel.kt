package com.aegispay.android.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aegispay.android.auth.AuthRepository
import com.aegispay.android.network.Account
import com.aegispay.android.network.AegisApiService
import com.aegispay.android.network.KycStatus
import com.aegispay.android.network.StompWebSocketClient
import com.aegispay.android.network.Transaction
import com.aegispay.android.push.NotificationBadgeState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject

data class DashboardUiState(
    val isLoadingAccount:      Boolean           = true,
    val isLoadingTransactions: Boolean           = true,
    val account:               Account?          = null,
    val recentTransactions:    List<Transaction> = emptyList(),
    val kycStatus:             KycStatus?        = null,
    val error:                 String?           = null,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val api:                    AegisApiService,
    private val authRepository:         AuthRepository,
    private val okHttpClient:           OkHttpClient,
    private val notificationBadgeState: NotificationBadgeState,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    /** Forwards the shared badge count so DashboardScreen can observe it. */
    val badgeCount: StateFlow<Int> = notificationBadgeState.count

    val currentUserId: String?
        get() = authRepository.currentUserId

    val isBackOfficeUser: Boolean
        get() = authRepository.currentUserRole in setOf("BACK_OFFICE", "ADMIN", "MERCHANT_OPS")

    private var notificationSocket: StompWebSocketClient? = null

    init {
        loadDashboard()
        startNotificationSocket()
    }

    fun loadDashboard() {
        val userId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingAccount = true, isLoadingTransactions = true, error = null) }

            // Account balance
            runCatching { api.getAccount(userId) }
                .onSuccess { account ->
                    _uiState.update { it.copy(isLoadingAccount = false, account = account) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoadingAccount = false, error = e.message) }
                }

            // Recent transactions
            runCatching { api.listTransactions(page = 0, size = 10) }
                .onSuccess { page ->
                    _uiState.update { it.copy(isLoadingTransactions = false, recentTransactions = page.content) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoadingTransactions = false, error = e.message) }
                }

            // KYC status — best-effort; failure does not block the dashboard
            runCatching { api.getUser(userId).kycStatus }
                .onSuccess { kyc -> _uiState.update { it.copy(kycStatus = kyc) } }
        }
    }

    // ── Notification STOMP socket ─────────────────────────────────────────────

    /**
     * Subscribes to the server's per-user notification queue so the in-app
     * badge increments even without an FCM push (foreground delivery).
     * Topic: /user/{userId}/queue/notifications
     */
    private fun startNotificationSocket() {
        val userId = authRepository.currentUserId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val token = runCatching { authRepository.validAccessToken() }.getOrNull()
                ?: return@launch
            notificationSocket = StompWebSocketClient(
                userId       = userId,
                accessToken  = token,
                okHttpClient = okHttpClient,
                onMessage    = { _ -> notificationBadgeState.increment() },
            ).also { it.connect() }
        }
    }

    override fun onCleared() {
        super.onCleared()
        notificationSocket?.disconnect()
        notificationSocket = null
    }
}
