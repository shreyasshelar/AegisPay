package com.aegispay.android.push

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton that tracks the unread notification count in-memory.
 * Incremented by [AegisFcmService] and by the STOMP WebSocket listener
 * in DashboardViewModel. Reset when the user opens the Notifications screen.
 */
@Singleton
class NotificationBadgeState @Inject constructor() {
    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count.asStateFlow()

    fun increment() { _count.value++ }
    fun reset()     { _count.value = 0 }
}
