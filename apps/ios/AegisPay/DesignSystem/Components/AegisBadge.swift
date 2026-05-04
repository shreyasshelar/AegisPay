import SwiftUI

/// Status badge matching the web design-system Badge component.
struct AegisBadge: View {
    let status: TransactionStatus

    var body: some View {
        HStack(spacing: 5) {
            Circle()
                .fill(Color.statusColor(for: status))
                .frame(width: 6, height: 6)
            Text(status.displayLabel)
                .font(.aegisCaption)
                .fontWeight(.semibold)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 5)
        .background(Color.statusColor(for: status).opacity(0.12))
        .clipShape(Capsule())
        .foregroundStyle(Color.statusColor(for: status))
    }
}
