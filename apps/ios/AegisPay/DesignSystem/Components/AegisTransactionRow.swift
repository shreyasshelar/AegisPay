import SwiftUI

/// Single transaction row for list views.
struct AegisTransactionRow: View {
    let transaction:   Transaction
    let currentUserId: String

    private var isSent: Bool { transaction.payerId == currentUserId }

    private var directionIcon: String {
        isSent ? "arrow.up.right.circle.fill" : "arrow.down.left.circle.fill"
    }
    private var directionTint: Color {
        isSent ? Color.aegisDanger : Color.aegisSuccess
    }
    private var amountPrefix: String { isSent ? "−" : "+" }

    var body: some View {
        HStack(spacing: 14) {
            // Direction icon
            Image(systemName: directionIcon)
                .font(.system(size: 32))
                .foregroundStyle(directionTint)
                .frame(width: 40)

            // Labels
            VStack(alignment: .leading, spacing: 3) {
                Text(isSent ? "To \(maskId(transaction.payeeId))"
                           : "From \(maskId(transaction.payerId))")
                    .font(.aegisBodySmall)
                    .fontWeight(.medium)
                    .foregroundStyle(Color.aegisText)
                    .lineLimit(1)

                Text(relativeDate(transaction.initiatedAt))
                    .font(.aegisCaption)
                    .foregroundStyle(Color.aegisTextMuted)
            }

            Spacer()

            // Amount + badge
            VStack(alignment: .trailing, spacing: 4) {
                Text("\(amountPrefix)\(formatAmount(transaction.amount, currency: transaction.currency))")
                    .font(.aegisBodySmall)
                    .fontWeight(.semibold)
                    .foregroundStyle(isSent ? Color.aegisDanger : Color.aegisSuccess)

                AegisBadge(status: transaction.status)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .contentShape(Rectangle())
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private func maskId(_ id: String) -> String {
        String(id.prefix(8)) + "…"
    }

    private func formatAmount(_ amount: Decimal, currency: String) -> String {
        amount.formatted(currency: currency)
    }

    private func relativeDate(_ date: Date) -> String {
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .abbreviated
        return formatter.localizedString(for: date, relativeTo: Date())
    }
}
