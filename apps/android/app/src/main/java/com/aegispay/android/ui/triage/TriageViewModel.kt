package com.aegispay.android.ui.triage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aegispay.android.network.AegisApiService
import com.aegispay.android.network.TriageIncidentRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.aegispay.android.ui.triage.TriageSessionStore
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
    private val api:          AegisApiService,
    private val sessionStore: TriageSessionStore,   // @Singleton — outlives this VM
) : ViewModel() {

    private val _formState = MutableStateFlow(TriageUiState())

    // Merge local form state with the singleton session history so the UI only
    // needs to observe one flow — sessions survive screen navigation.
    val uiState: StateFlow<TriageUiState> = combine(
        _formState,
        sessionStore.sessions,
    ) { form, sessions ->
        form.copy(sessions = sessions, expandedId = form.expandedId)
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, TriageUiState())

    val canTriage: Boolean
        get() = _formState.value.serviceName.isNotBlank() &&
                _formState.value.description.isNotBlank()

    fun onServiceNameChange(v: String) = _formState.update { it.copy(serviceName = v) }
    fun onDescriptionChange(v: String) = _formState.update { it.copy(description = v) }

    /** Pre-fill form from a failed transaction context (e.g. opened from transaction detail). */
    fun prefill(transactionId: String?, serviceName: String?) {
        _formState.update {
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
        val state = _formState.value
        viewModelScope.launch {
            _formState.update { it.copy(isTriaging = true, triageError = null) }
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
                    sessionStore.add(session)   // persisted in @Singleton store
                    _formState.update {
                        it.copy(isTriaging = false, expandedId = session.id)
                    }
                }
                .onFailure { e ->
                    _formState.update { it.copy(isTriaging = false, triageError = e.message) }
                }
        }
    }

    fun toggleExpanded(id: String) {
        _formState.update { it.copy(expandedId = if (it.expandedId == id) null else id) }
    }

    fun clearSessions() {
        sessionStore.clear()
        _formState.update { it.copy(expandedId = null) }
    }
}
