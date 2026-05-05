package com.aegispay.android.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aegispay.android.auth.AuthRepository
import com.aegispay.android.network.AegisApiService
import com.aegispay.android.network.ErrorResolutionRequest
import com.aegispay.android.network.ErrorResolutionResponse
import com.aegispay.android.network.StompWebSocketClient
import com.aegispay.android.network.Transaction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import javax.inject.Inject

data class TransactionDetailUiState(
    val isLoading:        Boolean                 = true,
    val transaction:      Transaction?            = null,
    val error:            String?                 = null,
    val errorResolution:  ErrorResolutionResponse? = null,
    val isResolvingError: Boolean                 = false,
)

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    private val api:            AegisApiService,
    private val authRepository: AuthRepository,
    private val okHttpClient:   OkHttpClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionDetailUiState())
    val uiState: StateFlow<TransactionDetailUiState> = _uiState.asStateFlow()

    private var pollingJob:  Job?                  = null
    private var stompClient: StompWebSocketClient? = null

    fun load(transactionId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { api.getTransaction(transactionId) }
                .onSuccess { tx ->
                    _uiState.update { it.copy(isLoading = false, transaction = tx) }
                    if (!tx.status.isTerminal) startLiveUpdates(transactionId)
                    // Auto-resolve on failure
                    if ((tx.status.name == "FAILED" || tx.status.name == "ROLLED_BACK") &&
                        tx.failureReason != null &&
                        _uiState.value.errorResolution == null
                    ) {
                        resolveError(tx.failureReason)
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    private fun startLiveUpdates(transactionId: String) {
        val userId = authRepository.currentUserId ?: return

        // Resolve access token on a background thread (blocking is OK here because
        // we're launching from a coroutine; runBlocking is used inside the STOMP
        // construction only when we need a sync value for the client).
        viewModelScope.launch(Dispatchers.IO) {
            val token = runCatching { authRepository.validAccessToken() }.getOrNull()
                ?: return@launch

            stompClient = StompWebSocketClient(
                userId       = userId,
                accessToken  = token,
                okHttpClient = okHttpClient,
                onMessage    = { _ ->
                    // Any notification on the user queue triggers a refresh
                    refreshTransaction(transactionId)
                },
            ).also { it.connect() }
        }

        // Polling fallback every 3 s
        pollingJob = viewModelScope.launch {
            while (true) {
                delay(3_000)
                val tx = runCatching { api.getTransaction(transactionId) }.getOrNull() ?: break
                _uiState.update { it.copy(transaction = tx) }
                if (tx.status.isTerminal) {
                    stopLiveUpdates()
                    break
                }
            }
        }
    }

    fun resolveError(failureReason: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isResolvingError = true) }
            runCatching {
                api.resolveError(
                    ErrorResolutionRequest(
                        errorCode    = failureReason.split(":").first().trim(),
                        errorMessage = failureReason,
                    )
                )
            }
                .onSuccess { res -> _uiState.update { it.copy(isResolvingError = false, errorResolution = res) } }
                .onFailure  {      _uiState.update { it.copy(isResolvingError = false) } }
        }
    }

    private fun refreshTransaction(transactionId: String) {
        viewModelScope.launch {
            runCatching { api.getTransaction(transactionId) }
                .onSuccess { tx ->
                    _uiState.update { it.copy(transaction = tx) }
                    if (tx.status.isTerminal) stopLiveUpdates()
                }
        }
    }

    private fun stopLiveUpdates() {
        pollingJob?.cancel()
        pollingJob = null
        stompClient?.disconnect()
        stompClient = null
    }

    override fun onCleared() {
        super.onCleared()
        stopLiveUpdates()
    }
}
