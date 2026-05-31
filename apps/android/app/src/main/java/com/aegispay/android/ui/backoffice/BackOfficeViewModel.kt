package com.aegispay.android.ui.backoffice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aegispay.android.network.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── UI state ──────────────────────────────────────────────────────────────────

data class BackOfficeUiState(
    // ── Risk cases ──────────────────────────────────────────────────────
    val isLoadingCases:  Boolean               = true,
    val cases:           List<RiskCase>        = emptyList(),
    val casesError:      String?               = null,
    val selectedCase:    RiskCase?             = null,
    val explanation:     FraudExplainResponse? = null,
    val isExplaining:    Boolean               = false,
    val explainError:    String?               = null,

    // ── Incident triage ─────────────────────────────────────────────────
    val serviceName:  String  = "",
    val incidentDesc: String  = "",
    val triageReport: String? = null,
    val isTriaging:   Boolean = false,
    val triageError:  String? = null,
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class BackOfficeViewModel @Inject constructor(
    private val api: AegisApiService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackOfficeUiState())
    val uiState: StateFlow<BackOfficeUiState> = _uiState.asStateFlow()

    init { loadRiskCases() }

    // ── Risk cases ────────────────────────────────────────────────────────────

    fun loadRiskCases() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCases = true, casesError = null) }
            runCatching { api.listRiskCases() }
                .onSuccess { paged ->
                    _uiState.update { it.copy(isLoadingCases = false, cases = paged.content) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoadingCases = false, casesError = e.message) }
                }
        }
    }

    fun selectCase(rc: RiskCase) {
        _uiState.update { it.copy(selectedCase = rc, explanation = null, explainError = null) }
    }

    fun explainFraud() {
        val rc = _uiState.value.selectedCase ?: return
        if (rc.ruleFlagKeys.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isExplaining = true, explainError = null) }
            runCatching {
                api.explainFraud(
                    FraudExplainRequest(
                        transactionId = rc.transactionId,
                        riskScore     = rc.riskScore,
                        flaggedRules  = rc.ruleFlagKeys,
                    )
                )
            }
                .onSuccess { res ->
                    _uiState.update { it.copy(isExplaining = false, explanation = res) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isExplaining = false, explainError = e.message) }
                }
        }
    }

    // ── Incident triage ───────────────────────────────────────────────────────

    fun onServiceNameChange(v: String)  = _uiState.update { it.copy(serviceName = v) }
    fun onIncidentDescChange(v: String) = _uiState.update { it.copy(incidentDesc = v) }

    val canTriage: Boolean
        get() = _uiState.value.serviceName.isNotBlank() &&
                _uiState.value.incidentDesc.isNotBlank()

    fun runTriage() {
        val state = _uiState.value
        if (!canTriage) return
        viewModelScope.launch {
            _uiState.update { it.copy(isTriaging = true, triageReport = null, triageError = null) }
            runCatching {
                api.triageIncident(
                    TriageIncidentRequest(
                        serviceName         = state.serviceName.trim(),
                        incidentDescription = state.incidentDesc.trim(),
                    )
                )
            }
                .onSuccess { res ->
                    _uiState.update { it.copy(isTriaging = false, triageReport = res.analysis) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isTriaging = false, triageError = e.message) }
                }
        }
    }

    fun resetTriage() {
        _uiState.update { it.copy(serviceName = "", incidentDesc = "", triageReport = null, triageError = null) }
    }
}
