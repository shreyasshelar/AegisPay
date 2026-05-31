package com.aegispay.android.ui.triage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Application-scoped store that keeps AI triage session history alive
 * independently of which NavBackStackEntry is on top.
 *
 * Mirrors the web's Zustand `useTriageStore` — sessions persist for the
 * lifetime of the app process (cleared only on explicit "Clear all" or
 * process death), so navigating between Back Office, Risk, and Triage
 * never loses investigation context.
 */
@Singleton
class TriageSessionStore @Inject constructor() {

    private val _sessions = MutableStateFlow<List<TriageSession>>(emptyList())
    val sessions: StateFlow<List<TriageSession>> = _sessions.asStateFlow()

    fun add(session: TriageSession)   = _sessions.update { listOf(session) + it }
    fun clear()                       = _sessions.update { emptyList() }
}
