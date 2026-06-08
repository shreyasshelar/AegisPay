package com.aegispay.android.ui.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aegispay.android.network.AegisApiService
import com.aegispay.android.network.Account
import com.aegispay.android.network.FxRateRepository
import com.aegispay.android.network.FxRates
import com.aegispay.android.network.TopUpConfirmRequest
import com.aegispay.android.network.TopUpIntentRequest
import java.math.BigDecimal
import java.math.RoundingMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── Constants ─────────────────────────────────────────────────────────────────

/** Hard INR balance ceiling — Stripe KYC threshold. */
val BALANCE_LIMIT_INR: BigDecimal = BigDecimal("100000")

// ── UI State ──────────────────────────────────────────────────────────────────

sealed interface WalletUiState {
    data object Loading : WalletUiState
    data class  Success(
        val accounts:    List<Account>,
        val topUpResult: TopUpResult? = null,
    ) : WalletUiState
    data class  Error(val message: String) : WalletUiState
}

enum class TopUpResult { SUCCESS, FAILED }

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class WalletViewModel @Inject constructor(
    private val api:           AegisApiService,
    private val fxRateRepo:    FxRateRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<WalletUiState>(WalletUiState.Loading)
    val uiState: StateFlow<WalletUiState> = _uiState.asStateFlow()

    /**
     * The Stripe [clientSecret] to present via [PaymentSheet].
     * Set when the backend creates a PaymentIntent; cleared after the sheet is shown.
     */
    private val _clientSecret = MutableStateFlow<String?>(null)
    val clientSecret: StateFlow<String?> = _clientSecret.asStateFlow()

    /**
     * The PaymentIntent ID extracted from the clientSecret (e.g. "pi_xxx" from "pi_xxx_secret_yyy").
     * Passed to [confirmTopUp] after the Stripe SDK reports [PaymentSheetResult.Completed].
     */
    var pendingPaymentIntentId: String? = null
        private set

    private val _isTopUpLoading = MutableStateFlow(false)
    val isTopUpLoading: StateFlow<Boolean> = _isTopUpLoading.asStateFlow()

    // ── FX rates ──────────────────────────────────────────────────────────────

    private val _fxRates   = MutableStateFlow(FxRates())
    val fxRates: StateFlow<FxRates> = _fxRates.asStateFlow()

    private val _fxLoading = MutableStateFlow(false)
    val fxLoading: StateFlow<Boolean> = _fxLoading.asStateFlow()

    init {
        loadAccounts()
        refreshFxRates()
    }

    // ── Load accounts + FX rates ──────────────────────────────────────────────

    fun loadAccounts() {
        viewModelScope.launch {
            _uiState.value = WalletUiState.Loading
            val accountsDeferred = async { runCatching { api.getMyAccount() } }
            val ratesDeferred    = async { fxRateRepo.rates() }

            _fxRates.value = ratesDeferred.await()
            _uiState.value = accountsDeferred.await().fold(
                onSuccess = { WalletUiState.Success(it) },
                onFailure = { WalletUiState.Error(it.message ?: "Failed to load wallet") },
            )
        }
    }

    fun refreshFxRates() {
        viewModelScope.launch {
            _fxLoading.value = true
            _fxRates.value   = fxRateRepo.rates()
            _fxLoading.value = false
        }
    }

    // ── Balance limit helpers ─────────────────────────────────────────────────

    /** INR available balance, or null when no INR account. */
    val inrAvailable: BigDecimal?
        get() = (uiState.value as? WalletUiState.Success)
            ?.accounts?.firstOrNull { it.currency == "INR" }
            ?.availableBalance

    /** Headroom until the INR wallet cap. */
    val inrRoom: BigDecimal
        get() = (BALANCE_LIMIT_INR - (inrAvailable ?: BigDecimal.ZERO))
            .coerceAtLeast(BigDecimal.ZERO)

    /** Returns true when adding [amount] INR would breach the limit. */
    fun wouldExceedLimit(amount: BigDecimal): Boolean =
        (inrAvailable ?: BigDecimal.ZERO) + amount > BALANCE_LIMIT_INR

    // ── Step 1: create PaymentIntent ──────────────────────────────────────────

    /**
     * Calls the backend to create a Stripe PaymentIntent.
     * On success sets [clientSecret] which triggers [WalletScreen] to call
     * `paymentSheet.presentWithPaymentIntent(clientSecret, config)`.
     *
     * Applies a hard block for INR top-ups that would exceed [BALANCE_LIMIT_INR].
     */
    fun createTopUpIntent(amount: BigDecimal, currency: String = "INR") {
        // Hard block — INR limit
        if (currency == "INR" && wouldExceedLimit(amount)) {
            val current = _uiState.value
            if (current is WalletUiState.Success) {
                _uiState.value = current.copy(topUpResult = TopUpResult.FAILED)
            }
            return
        }

        viewModelScope.launch {
            _isTopUpLoading.value = true
            try {
                val response = api.createTopUpIntent(TopUpIntentRequest(amount, currency))
                pendingPaymentIntentId = response.paymentIntentId
                _clientSecret.value   = response.clientSecret
            } catch (e: Exception) {
                val prev = _uiState.value
                _uiState.value = WalletUiState.Error("Could not initiate top-up: ${e.message}")
                delay(2_500)
                _uiState.value = if (prev is WalletUiState.Success) prev
                                 else WalletUiState.Success(emptyList())
            } finally {
                _isTopUpLoading.value = false
            }
        }
    }

    // ── Step 2: confirm after Stripe SDK succeeds ─────────────────────────────

    fun confirmTopUp(paymentIntentId: String) {
        viewModelScope.launch {
            _isTopUpLoading.value = true
            _clientSecret.value   = null
            try {
                api.confirmTopUp(TopUpConfirmRequest(paymentIntentId))
                pendingPaymentIntentId = null
                loadAccounts()  // refresh balance display
                val current = _uiState.value
                if (current is WalletUiState.Success) {
                    _uiState.value = current.copy(topUpResult = TopUpResult.SUCCESS)
                }
            } catch (e: Exception) {
                val current = _uiState.value
                if (current is WalletUiState.Success) {
                    _uiState.value = current.copy(topUpResult = TopUpResult.FAILED)
                }
            } finally {
                _isTopUpLoading.value = false
            }
        }
    }

    // ── Called by WalletScreen when PaymentSheet reports failure ──────────────

    fun onPaymentSheetFailed(errorMessage: String?) {
        _clientSecret.value    = null
        pendingPaymentIntentId = null
        val current = _uiState.value
        if (current is WalletUiState.Success) {
            _uiState.value = current.copy(topUpResult = TopUpResult.FAILED)
        } else {
            _uiState.value = WalletUiState.Error(errorMessage ?: "Payment failed")
        }
    }

    fun clearTopUpResult() {
        _clientSecret.value = null
        val current = _uiState.value
        if (current is WalletUiState.Success) {
            _uiState.value = current.copy(topUpResult = null)
        }
    }
}

// ── Extensions ────────────────────────────────────────────────────────────────

private fun BigDecimal.coerceAtLeast(min: BigDecimal): BigDecimal =
    if (this < min) min else this
