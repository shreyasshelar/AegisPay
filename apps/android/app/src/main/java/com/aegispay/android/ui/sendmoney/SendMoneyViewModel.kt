package com.aegispay.android.ui.sendmoney

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aegispay.android.auth.AuthRepository
import com.aegispay.android.network.AegisApiService
import com.aegispay.android.network.CreateTransactionRequest
import com.aegispay.android.network.ErrorResolutionRequest
import com.aegispay.android.network.ErrorResolutionResponse
import com.aegispay.android.network.FxRateRepository
import com.aegispay.android.network.FxRates
import com.aegispay.android.network.KycStatus
import com.aegispay.android.network.StompWebSocketClient
import com.aegispay.android.network.Transaction
import com.aegispay.android.offline.OfflinePaymentQueue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import javax.inject.Inject

// ── Step enum ─────────────────────────────────────────────────────────────────

enum class SendStep { PAYEE, AMOUNT, REVIEW, STATUS }

// ── UI state ──────────────────────────────────────────────────────────────────

data class SendMoneyUiState(
    val step:               SendStep              = SendStep.PAYEE,

    // KYC gate
    val kycStatus:          KycStatus?            = null,
    val kycLoading:         Boolean               = true,

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

    // Offline queue
    val isQueuedOffline:    Boolean               = false,

    // Risk threshold warning (amber) — shown when INR equivalent ≥ ₹10,000
    val riskWarning:        String?               = null,
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

/** INR threshold above which enhanced risk review is triggered. */
private val RISK_THRESHOLD_INR = BigDecimal("10000")

@HiltViewModel
class SendMoneyViewModel @Inject constructor(
    private val api:                 AegisApiService,
    private val authRepository:      AuthRepository,
    private val okHttpClient:        OkHttpClient,
    private val offlinePaymentQueue: OfflinePaymentQueue,
    private val fxRateRepository:    FxRateRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SendMoneyUiState())
    val uiState: StateFlow<SendMoneyUiState> = _uiState.asStateFlow()

    private val _fxRates = MutableStateFlow(FxRates())
    val fxRates: StateFlow<FxRates> = _fxRates.asStateFlow()

    // Idempotency key — generated once per session, reused on retry
    private var idempotencyKey: String = UUID.randomUUID().toString()

    init { loadKycStatus() }

    private fun loadKycStatus() {
        val userId = authRepository.currentUserId ?: run {
            _uiState.update { it.copy(kycLoading = false) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(kycLoading = true) }
            val statusDeferred = async { runCatching { api.getUser(userId).kycStatus }.getOrNull() }
            val ratesDeferred  = async { fxRateRepository.rates() }
            val status = statusDeferred.await()
            _fxRates.value = ratesDeferred.await()
            _uiState.update { it.copy(kycStatus = status, kycLoading = false) }
        }
    }

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
            val v = text.toBigDecimalOrNull() ?: return "Enter a valid amount"
            if (v <= BigDecimal.ZERO) return "Amount must be greater than zero"
            if (v > BigDecimal("1000000")) return "Maximum 10,00,000 per transfer"
            return null
        }

    val isPayeeValid  get() = _uiState.value.payeeId.isNotBlank() && payeeIdError == null
    val isAmountValid get() = amountError == null && _uiState.value.amountText.isNotBlank()

    // ── Field mutations ───────────────────────────────────────────────────────

    fun onPayeeIdChange(v: String)  = _uiState.update { it.copy(payeeId = v) }
    fun onNoteChange(v: String)     = _uiState.update { it.copy(note = v) }

    fun onAmountChange(v: String) {
        _uiState.update { it.copy(amountText = v) }
        recalculateRiskWarning()
    }

    fun onCurrencyChange(v: String) {
        _uiState.update { it.copy(currency = v) }
        recalculateRiskWarning()
    }

    // ── Risk threshold warning ────────────────────────────────────────────────

    /**
     * Recomputes the amber risk warning every time amount or currency changes.
     * Frankfurter rates: 1 INR = rate[currency] units.
     * To convert [amount] foreign currency to INR: inr = amount / rate[currency].
     */
    fun recalculateRiskWarning() {
        val state  = _uiState.value
        val amount = state.amountText.toBigDecimalOrNull()
        if (amount == null || amount <= BigDecimal.ZERO) {
            _uiState.update { it.copy(riskWarning = null) }
            return
        }

        val rates = _fxRates.value
        val inrEquivalent: BigDecimal = if (state.currency == "INR") {
            amount
        } else {
            val rate = rates.forCurrency(state.currency)
            if (rate != null && rate > 0) {
                amount.divide(BigDecimal(rate), 4, RoundingMode.HALF_UP)
            } else amount
        }

        if (inrEquivalent >= RISK_THRESHOLD_INR) {
            val fmt = com.aegispay.android.ui.wallet.formatCurrency(
                inrEquivalent.setScale(0, RoundingMode.HALF_UP), "INR"
            )
            _uiState.update {
                it.copy(riskWarning = "Transfers ≥ ₹10,000 (~$fmt) trigger enhanced risk review — approval may take up to 60 s.")
            }
        } else {
            _uiState.update { it.copy(riskWarning = null) }
        }
    }

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
                        amount   = state.amountText.toBigDecimal(),
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
                    if (e is IOException || e.cause is IOException) {
                        runCatching {
                            offlinePaymentQueue.enqueue(
                                payeeId  = state.payeeId.trim(),
                                amount   = state.amountText.toBigDecimal(),
                                currency = state.currency,
                                note     = state.note.ifBlank { null },
                            )
                        }
                        _uiState.update {
                            it.copy(
                                isSubmitting    = false,
                                isQueuedOffline = true,
                                step            = SendStep.STATUS,
                            )
                        }
                    } else {
                        _uiState.update { it.copy(isSubmitting = false, submissionError = e.message) }
                    }
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
        _uiState.update { it.copy(createdTransaction = tx) }

        if (tx.status.isTerminal) {
            stopLiveUpdates()
        }

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
                        errorCode    = failureReason.split(":").last().trim(),
                        errorMessage = failureReason,
                    )
                )
            }
                .onSuccess { res -> _uiState.update { it.copy(isResolvingError = false, errorResolution = res) } }
                .onFailure {       _uiState.update { it.copy(isResolvingError = false) } }
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
