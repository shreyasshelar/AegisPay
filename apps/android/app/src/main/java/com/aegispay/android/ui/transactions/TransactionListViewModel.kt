package com.aegispay.android.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aegispay.android.auth.AuthRepository
import com.aegispay.android.network.AegisApiService
import com.aegispay.android.network.Transaction
import com.aegispay.android.network.TransactionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TransactionListUiState(
    val isLoading:      Boolean           = true,
    val isRefreshing:   Boolean           = false,
    val transactions:   List<Transaction> = emptyList(),
    val error:          String?           = null,
    val currentPage:    Int               = 0,
    val totalPages:     Int               = 1,
    // Filters
    val statusFilter:   TransactionStatus? = null,
    val fromDate:       String?            = null,
    val toDate:         String?            = null,
)

@HiltViewModel
class TransactionListViewModel @Inject constructor(
    private val api:            AegisApiService,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionListUiState())
    val uiState: StateFlow<TransactionListUiState> = _uiState.asStateFlow()

    val currentUserId: String?
        get() = authRepository.currentUserId

    init { loadPage(0) }

    // ── Filter mutations ──────────────────────────────────────────────────────

    fun setStatusFilter(status: TransactionStatus?) {
        _uiState.update { it.copy(statusFilter = status) }
        loadPage(0)
    }

    fun setFromDate(date: String?) {
        _uiState.update { it.copy(fromDate = date) }
        loadPage(0)
    }

    fun setToDate(date: String?) {
        _uiState.update { it.copy(toDate = date) }
        loadPage(0)
    }

    fun clearFilters() {
        _uiState.update { it.copy(statusFilter = null, fromDate = null, toDate = null) }
        loadPage(0)
    }

    val hasActiveFilters: Boolean
        get() = _uiState.value.let {
            it.statusFilter != null || it.fromDate != null || it.toDate != null
        }

    // ── Loading ───────────────────────────────────────────────────────────────

    fun loadPage(page: Int) {
        viewModelScope.launch {
            val isRefresh = page == 0 && _uiState.value.transactions.isNotEmpty()
            _uiState.update {
                it.copy(
                    isLoading    = !isRefresh,
                    isRefreshing = isRefresh,
                    error        = null,
                )
            }
            val state = _uiState.value
            runCatching {
                api.listTransactions(
                    page   = page,
                    size   = 20,
                    status = state.statusFilter?.name,
                )
            }
                .onSuccess { result ->
                    _uiState.update {
                        it.copy(
                            isLoading    = false,
                            isRefreshing = false,
                            transactions = if (page == 0) result.content
                                           else it.transactions + result.content,
                            currentPage  = result.number,
                            totalPages   = result.totalPages,
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, isRefreshing = false, error = e.message) }
                }
        }
    }

    fun loadNextPage() {
        val state = _uiState.value
        if (!state.isLoading && !state.isRefreshing && state.currentPage + 1 < state.totalPages) {
            loadPage(state.currentPage + 1)
        }
    }

    fun refresh() = loadPage(0)
}
