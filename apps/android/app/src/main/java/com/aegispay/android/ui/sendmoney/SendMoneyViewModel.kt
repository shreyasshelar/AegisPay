package com.aegispay.android.ui.sendmoney

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aegispay.android.auth.AuthRepository
import com.aegispay.android.network.AegisApiService
import com.aegispay.android.network.CreateTransactionRequest
import com.aegispay.android.network.ErrorResolutionRequest
import com.aegispay.android.network.ErrorResolutionResponse
import com.aegispay.android.network.StompWebSocketClient
import com.aegispay.android.network.Transaction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import java.util.UUID
import javax.inject.Inject

// ── Step enum ─────────────────────────────────────────────────────────────────

enum class SendStep { PAYEE, AMOUNT, REVIEW, STATUS }

// ── UI state ──────────────────────────────────────────────────────────────────

data class SendMoneyUiState(
    val step:               SendStep              = SendStep.PAYEE,

    // Form fields
    val payeeId:            String                = "",
    val amountText:         String                = "",
    val currency:           String                = "INR",
    val note:               String                = "",

    // Submit
    val isSubmitting:       Boolean               = false,
    val submissionError:    String?               = null,

    // Status
    val createdTransaction: Transaction?          = null,
    val isLoadingStatus:    Boolean               = false,
    val statusError:        String?               = null,

    // AI error resolution
    val errorResolution:    ErrorResolutionResponse? = null,
    val isResolvingError:   Boolean               = false,
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class SendMoneyViewModel @Inject constructor(
    private val api:            AegisApiService,
    private val authRepository: AuthRepository,
    private val okHttpClient:   OkHttpClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SendMoneyUiState())
    val uiState: StateFlow<SendMoneyUiState> = _uiState.asStateFlow()

    // Idempotency key — generated once per session, reused on retry
    private var idempotencyKey: String = UUID.randomUUID().toString()

    private var pollingJob:  Job?                  = null
    private var stompClient: StompWebSocketClient? = null

    // ── Validation ────────────────────────────────────────────────────────────

    val payeeIdError: String?
        get() {
            val id = _uiState.value.payeeId
            if (id.isBlank()) return "Payee ID is required"
            val uuidRegex = Regex(
                "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
            )
            return if (!uuidRegex.matches(id)) "Must be a valid UUID" else null
        }

    val amountError: String?
        get() {
            val text = _uiState.value.amountText
            if (text.isBlank()) return "Amount is required"
            val v = text.toDoubleOrNull() ?: return "Enter a valid amount"
            if (v <= 0) return "Amount must be greater than zero"
            if (v > 1_000_000) return "Maximum 10,00,000 per transfer"
            return null
        }

    val isPayeeValid  get() = _uiState.value.payeeId.isNotBlank() && payeeIdError == null
    val isAmountValid get() = amountError == null && _uiState.value.amountText.isNotBlank()

    // ── Field mutations ───────────────────────────────────────────────────────

    fun onPayeeIdChange(v: String)  = _uiState.update { it.copy(payeeId = v) }
    fun onAmountChange(v: String)   = _uiState.update { it.copy(amountText = v) }
    fun onCurrencyChange(v: String) = _uiState.update { it.copy(currency = v) }
    fun onNoteChange(v: String)     = _uiState.update { it.copy(note = v) }

    // ── Step navigation ───────────────────────────────────────────────────────

    fun goTo(step: SendStep)    = _uiState.update { it.copy(step = step) }
    fun back() = when (_uiState.value.step) {
        SendStep.PAYEE  -> Unit
        SendStep.AMOUNT -> goTo(SendStep.PAYEE)
        SendStep.REVIEW -> goTo(SendStep.AMOUNT)
        SendStep.STATUS -> Unit
    }

    // ── Submit ────────────────────────────────────────────────────────────────

    fun submit() {
        if (!isPayeeValid || !isAmountValid) return
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, submissionError = null) }
            runCatching {
                api.createTransaction(
                    idempotencyKey = idempotencyKey,
                    request = CreateTransactionRequest(
                        payeeId  = state.payeeId.trim(),
                        amount   = state.amountText.toDouble(),
                        currency = state.currency,
                        note     = state.note.ifBlank { null },
                    ),
                )
            }
                .onSuccess { tx ->
                    _uiState.update {
                        it.copy(
                            isSubmitting       = false,
                            createdTransaction = tx,
                            step               = SendStep.STATUS,
                        )
                    }
                    startLiveUpdates(tx.transactionId)
                }
                .onFailure { e ->
                    // idempotencyKey preserved so the user can retry safely
                    _uiState.update { it.copy(isSubmitting = false, submissionError = e.message) }
                }
        }
    }

    // ── Live updates ──────────────────────────────────────────────────────────

    private fun startLiveUpdates(transactionId: String) {
        val userId = authRepository.currentUserId ?: return

        viewModelScope.launch(Dispatchers.IO) {
            val token = runCatching { authRepository.validAccessToken() }.getOrNull()
                ?: return@launch

            stompClient = StompWebSocketClient(
                userId       = userId,
                accessToken  = token,
                okHttpClient = okHttpClient,
                onMessage    = { _ -> refreshStatus(transactionId) },
            ).also { it.connect() }
        }

        // Polling fallback every 4 s
        pollingJob = viewModelScope.launch {
            while (true) {
                delay(4_000)
                val tx = runCatching { api.getTransaction(transactionId) }.getOrNull() ?: break
                handleStatusUpdate(tx)
                if (tx.status.isTerminal) break
            }
        }
    }

    private fun refreshStatus(transactionId: String) {
        viewModelScope.launch {
            val tx = runCatching { api.getTransaction(transactionId) }.getOrNull() ?: return@launch
            handleStatusUpdate(tx)
        }
    }

    private fun handleStatusUpdate(tx: Transaction) {
        val prev = _uiState.value.createdTransaction
        _uiState.update { it.copy(createdTransaction = tx) }

        if (tx.status.isTerminal) {
            stopLiveUpdates()
        }

        // Auto-resolve error on first failure
        if ((tx.status.name == "FAILED" || tx.status.name == "ROLLED_BACK") &&
            tx.failureReason != null &&
            _uiState.value.errorResolution == null &&
            !_uiState.value.isResolvingError
        ) {
            resolveError(tx.failureReason)
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

    private fun stopLiveUpdates() {
        pollingJob?.cancel()
        pollingJob = null
        stompClient?.disconnect()
        stompClient = null
    }

    // ── Reset ─────────────────────────────────────────────────────────────────

    fun reset() {
        stopLiveUpdates()
        idempotencyKey = UUID.randomUUID().toString()
        _uiState.value = SendMoneyUiState()
    }

    override fun onCleared() {
        super.onCleared()
        stopLiveUpdates()
    }
}
