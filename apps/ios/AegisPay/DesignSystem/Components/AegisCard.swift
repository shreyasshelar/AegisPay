import SwiftUI

/// Base card container — white surface, rounded corners, subtle shadow.
struct AegisCard<Content: View>: View {
    let content: () -> Content
    var padding: CGFloat = 16

    var body: some View {
        content()
            .padding(padding)
            .background(Color.aegisSurface)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .shadow(color: .black.opacity(0.06), radius: 6, x: 0, y: 2)
    }
}

// ── Stat card ─────────────────────────────────────────────────────────────────

struct AegisStatCard: View {
    let label:    String
    let value:    String
    let icon:     String      // SF Symbol name
    let iconTint: Color

    var body: some View {
        AegisCard {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Text(label)
                        .font(.aegisCaption)
                        .foregroundStyle(Color.aegisTextMuted)
                    Spacer()
                    Image(systemName: icon)
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(iconTint)
                        .padding(8)
                        .background(iconTint.opacity(0.12))
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                }
                Text(value)
                    .font(.aegisHeadline)
                    .foregroundStyle(Color.aegisText)
            }
        }
    }
}
