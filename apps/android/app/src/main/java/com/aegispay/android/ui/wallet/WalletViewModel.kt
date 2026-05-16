package com.aegispay.android.ui.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aegispay.android.network.AegisApiService
import com.aegispay.android.network.Account
import com.aegispay.android.network.TopUpConfirmRequest
import com.aegispay.android.network.TopUpIntentRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    private val api: AegisApiService,
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

    init { loadAccounts() }

    // ── Load accounts ─────────────────────────────────────────────────────────

    fun loadAccounts() {
        viewModelScope.launch {
            _uiState.value = WalletUiState.Loading
            _uiState.value = try {
                WalletUiState.Success(api.getMyAccount())
            } catch (e: Exception) {
                WalletUiState.Error(e.message ?: "Failed to load wallet")
            }
        }
    }

    // ── Step 1: create PaymentIntent ──────────────────────────────────────────

    /**
     * Calls the backend to create a Stripe PaymentIntent.
     * On success sets [clientSecret] which triggers [WalletScreen] to call
     * `paymentSheet.presentWithPaymentIntent(clientSecret, config)`.
     */
    fun createTopUpIntent(amount: Double, currency: String = "INR") {
        viewModelScope.launch {
            _isTopUpLoading.value = true
            try {
                val response = api.createTopUpIntent(TopUpIntentRequest(amount, currency))
                // Store the pi_xxx ID so we can confirm after Stripe SDK completes
                pendingPaymentIntentId = response.paymentIntentId
                // Publishing the clientSecret triggers the LaunchedEffect in WalletScreen
                _clientSecret.value = response.clientSecret
            } catch (e: Exception) {
                val prev = _uiState.value
                _uiState.value = WalletUiState.Error("Could not initiate top-up: ${e.message}")
                kotlinx.coroutines.delay(2_500)
                _uiState.value = if (prev is WalletUiState.Success) prev
                                 else WalletUiState.Success(emptyList())
            } finally {
                _isTopUpLoading.value = false
            }
        }
    }

    // ── Step 2: confirm after Stripe SDK succeeds ─────────────────────────────

    /**
     * Called by [WalletScreen] when [PaymentSheetResult.Completed] fires.
     * Notifies the backend to credit the user's ledger balance.
     */
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
        _clientSecret.value            = null
        pendingPaymentIntentId         = null
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
