import SwiftUI

/// AegisPay brand color palette.
/// Use these tokens everywhere — never use raw SwiftUI colors directly.
extension Color {

    // ── Primary (blue) ─────────────────────────────────────────────────────

    static let aegisPrimary      = Color(hex: "#3b82f6") // primary-500
    static let aegisPrimaryDark  = Color(hex: "#1d4ed8") // primary-700
    static let aegisPrimaryLight = Color(hex: "#eff6ff") // primary-50

    // ── Semantic ────────────────────────────────────────────────────────────

    static let aegisSuccess      = Color(hex: "#22c55e") // success-500
    static let aegisSuccessLight = Color(hex: "#f0fdf4") // success-50
    static let aegisDanger       = Color(hex: "#ef4444") // danger-500
    static let aegisDangerLight  = Color(hex: "#fff1f2") // danger-50
    static let aegisWarning      = Color(hex: "#f59e0b") // warning-500
    static let aegisWarningLight = Color(hex: "#fffbeb") // warning-50

    // ── Neutrals ────────────────────────────────────────────────────────────

    static let aegisBg           = Color(hex: "#f8fafc") // slate-50
    static let aegisSurface      = Color.white
    static let aegisBorder       = Color(hex: "#e2e8f0") // slate-200
    static let aegisText         = Color(hex: "#0f172a") // slate-900
    static let aegisTextMuted    = Color(hex: "#64748b") // slate-500
    static let aegisTextSubtle   = Color(hex: "#94a3b8") // slate-400

    // ── Status color for TransactionStatus ──────────────────────────────────

    static func statusColor(for status: TransactionStatus) -> Color {
        switch status {
        case .initiated:   return aegisWarning
        case .reserved:    return aegisPrimary
        case .riskCleared: return Color(hex: "#8b5cf6")
        case .processing:  return aegisPrimary
        case .completed:   return aegisSuccess
        case .failed:      return aegisDanger
        case .rolledBack:  return aegisTextMuted
        }
    }
}

// ── Hex initialiser ────────────────────────────────────────────────────────────

extension Color {
    init(hex: String) {
        var h = hex.trimmingCharacters(in: .whitespacesAndNewlines)
        if h.hasPrefix("#") { h.removeFirst() }

        var rgb: UInt64 = 0
        Scanner(string: h).scanHexInt64(&rgb)

        let r = Double((rgb & 0xFF0000) >> 16) / 255
        let g = Double((rgb & 0x00FF00) >> 8)  / 255
        let b = Double( rgb & 0x0000FF)         / 255

        self.init(red: r, green: g, blue: b)
    }
}
