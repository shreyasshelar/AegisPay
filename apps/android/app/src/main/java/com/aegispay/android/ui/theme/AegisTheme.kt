package com.aegispay.android.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Brand palette ─────────────────────────────────────────────────────────────

object AegisColor {
    // Primary (blue)
    val Primary      = Color(0xFF3B82F6)  // primary-500
    val PrimaryDark  = Color(0xFF1D4ED8)  // primary-700
    val PrimaryLight = Color(0xFFEFF6FF)  // primary-50

    // Semantic
    val Success      = Color(0xFF22C55E)
    val SuccessLight = Color(0xFFF0FDF4)
    val Danger       = Color(0xFFEF4444)
    val DangerLight  = Color(0xFFFFF1F2)
    val Warning      = Color(0xFFF59E0B)
    val WarningLight = Color(0xFFFFFBEB)

    // Neutrals
    val Bg           = Color(0xFFF8FAFC)  // slate-50
    val Surface      = Color(0xFFFFFFFF)
    val Border       = Color(0xFFE2E8F0)  // slate-200
    val Text         = Color(0xFF0F172A)  // slate-900
    val TextMuted    = Color(0xFF64748B)  // slate-500
    val TextSubtle   = Color(0xFF94A3B8)  // slate-400
}

// ── Material3 colour scheme ──────────────────────────────────────────────────

private val AegisLightColorScheme = lightColorScheme(
    primary          = AegisColor.Primary,
    onPrimary        = Color.White,
    primaryContainer = AegisColor.PrimaryLight,
    secondary        = AegisColor.Success,
    onSecondary      = Color.White,
    error            = AegisColor.Danger,
    onError          = Color.White,
    background       = AegisColor.Bg,
    onBackground     = AegisColor.Text,
    surface          = AegisColor.Surface,
    onSurface        = AegisColor.Text,
    surfaceVariant   = AegisColor.PrimaryLight,
    outline          = AegisColor.Border,
)

// ── Theme ─────────────────────────────────────────────────────────────────────

@Composable
fun AegisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AegisLightColorScheme,
        typography  = AegisTypography,
        content     = content,
    )
}

// ── Status colours ────────────────────────────────────────────────────────────

import com.aegispay.android.network.TransactionStatus

fun statusColor(status: TransactionStatus): Color = when (status) {
    TransactionStatus.INITIATED    -> AegisColor.Warning
    TransactionStatus.RESERVED     -> AegisColor.Primary
    TransactionStatus.RISK_CLEARED -> Color(0xFF8B5CF6)
    TransactionStatus.PROCESSING   -> AegisColor.Primary
    TransactionStatus.COMPLETED    -> AegisColor.Success
    TransactionStatus.FAILED       -> AegisColor.Danger
    TransactionStatus.ROLLED_BACK  -> AegisColor.TextMuted
}

fun statusLightColor(status: TransactionStatus): Color = when (status) {
    TransactionStatus.COMPLETED    -> AegisColor.SuccessLight
    TransactionStatus.FAILED,
    TransactionStatus.ROLLED_BACK  -> AegisColor.DangerLight
    else                           -> AegisColor.PrimaryLight
}
