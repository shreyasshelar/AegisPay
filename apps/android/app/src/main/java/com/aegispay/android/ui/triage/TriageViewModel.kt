package com.aegispay.android.ui.triage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aegispay.android.network.AegisApiService
import com.aegispay.android.network.TriageIncidentRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

// ── Session model ─────────────────────────────────────────────────────────────

data class TriageSession(
    val id:          String    = UUID.randomUUID().toString(),
    val serviceName: String,
    val description: String,
    val analysis:    String,
    val degraded:    Boolean   = false,
    val timestamp:   Instant   = Instant.now(),
)

// ── UI state ──────────────────────────────────────────────────────────────────

data class TriageUiState(
    val serviceName:  String          = "",
    val description:  String          = "",
    val isTriaging:   Boolean         = false,
    val triageError:  String?         = null,
    val sessions:     List<TriageSession> = emptyList(),
    val expandedId:   String?         = null,
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class TriageViewModel @Inject constructor(
    private val api: AegisApiService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TriageUiState())
    val uiState: StateFlow<TriageUiState> = _uiState.asStateFlow()

    val canTriage: Boolean
        get() = _uiState.value.serviceName.isNotBlank() &&
                _uiState.value.description.isNotBlank()

    fun onServiceNameChange(v: String) = _uiState.update { it.copy(serviceName = v) }
    fun onDescriptionChange(v: String) = _uiState.update { it.copy(description = v) }

    /** Pre-fill form from a failed transaction context (e.g. opened from transaction detail). */
    fun prefill(transactionId: String?, serviceName: String?) {
        _uiState.update {
            it.copy(
                serviceName  = serviceName ?: it.serviceName,
                description  = if (transactionId != null && it.description.isBlank())
                    "Transaction $transactionId failed. Investigate root cause and recommend mitigation."
                else it.description,
            )
        }
    }

    fun runTriage() {
        if (!canTriage) return
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isTriaging = true, triageError = null) }
            runCatching {
                api.triageIncident(
                    TriageIncidentRequest(
                        serviceName         = state.serviceName.trim(),
                        incidentDescription = state.description.trim(),
                    )
                )
            }
                .onSuccess { res ->
                    val session = TriageSession(
                        serviceName = state.serviceName.trim(),
                        description = state.description.trim(),
                        analysis    = res.analysis,
                        degraded    = res.analysis.startsWith("⚠"),
                    )
                    _uiState.update {
                        it.copy(
                            isTriaging  = false,
                            sessions    = listOf(session) + it.sessions,
                            expandedId  = session.id,
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isTriaging = false, triageError = e.message) }
                }
        }
    }

    fun toggleExpanded(id: String) {
        _uiState.update { it.copy(expandedId = if (it.expandedId == id) null else id) }
    }

    fun clearSessions() = _uiState.update { it.copy(sessions = emptyList(), expandedId = null) }
}
