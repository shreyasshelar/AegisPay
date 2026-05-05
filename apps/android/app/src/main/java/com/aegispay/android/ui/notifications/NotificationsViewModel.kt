package com.aegispay.android.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aegispay.android.network.AegisApiService
import com.aegispay.android.network.PushNotification
import com.aegispay.android.push.NotificationBadgeState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationsUiState(
    val isLoading:     Boolean                = true,
    val notifications: List<PushNotification> = emptyList(),
    val error:         String?                = null,
)

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val api:                    AegisApiService,
    private val notificationBadgeState: NotificationBadgeState,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    init {
        // Clear the badge the moment this screen is created (user opened Notifications)
        notificationBadgeState.reset()
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { api.listNotifications(page = 0, size = 50) }
                .onSuccess { page ->
                    _uiState.update { it.copy(isLoading = false, notifications = page.content) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }
}
