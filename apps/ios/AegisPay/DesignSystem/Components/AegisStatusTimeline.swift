import SwiftUI

private let STEPS: [(label: String, status: TransactionStatus)] = [
    ("Initiated",      .initiated),
    ("Funds Reserved", .reserved),
    ("Risk Cleared",   .riskCleared),
    ("Processing",     .processing),
    ("Completed",      .completed),
]

private let STATUS_ORDER: [TransactionStatus: Int] = {
    var map = [TransactionStatus: Int]()
    for (i, step) in STEPS.enumerated() { map[step.status] = i }
    map[.failed]     = -1
    map[.rolledBack] = -1
    return map
}()

/// Vertical status timeline — mirrors StatusTimeline.tsx in the web design-system.
struct AegisStatusTimeline: View {
    let status: TransactionStatus

    private var currentIndex: Int {
        STATUS_ORDER[status] ?? 0
    }

    private var isFailed: Bool {
        status == .failed || status == .rolledBack
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ForEach(Array(STEPS.enumerated()), id: \.offset) { index, step in
                HStack(alignment: .center, spacing: 14) {
                    // Circle indicator
                    ZStack {
                        Circle()
                            .fill(circleColor(index: index))
                            .frame(width: 28, height: 28)
                        Image(systemName: iconName(index: index))
                            .font(.system(size: 12, weight: .bold))
                            .foregroundStyle(.white)
                    }

                    // Label
                    VStack(alignment: .leading, spacing: 2) {
                        Text(step.label)
                            .font(.aegisBodySmall)
                            .fontWeight(index == currentIndex && !isFailed ? .semibold : .regular)
                            .foregroundStyle(
                                index <= currentIndex && !isFailed
                                    ? Color.aegisText
                                    : Color.aegisTextSubtle
                            )
                    }
                    Spacer()
                }
                // Connector line
                if index < STEPS.count - 1 {
                    Rectangle()
                        .fill(index < currentIndex && !isFailed ? Color.aegisPrimary : Color.aegisBorder)
                        .frame(width: 2, height: 20)
                        .padding(.leading, 13)
                }
            }

            // Failure state
            if isFailed {
                HStack(spacing: 14) {
                    ZStack {
                        Circle()
                            .fill(Color.aegisDanger)
                            .frame(width: 28, height: 28)
                        Image(systemName: "xmark")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundStyle(.white)
                    }
                    Text(status == .failed ? "Failed" : "Rolled Back")
                        .font(.aegisBodySmall)
                        .fontWeight(.semibold)
                        .foregroundStyle(Color.aegisDanger)
                    Spacer()
                }
                .padding(.top, 0)
            }
        }
    }

    private func circleColor(index: Int) -> Color {
        guard !isFailed else { return index < currentIndex ? Color.aegisPrimary : Color.aegisBorder }
        if index < currentIndex  { return Color.aegisSuccess }
        if index == currentIndex { return Color.aegisPrimary }
        return Color.aegisBorder
    }

    private func iconName(index: Int) -> String {
        guard !isFailed else {
            return index < currentIndex ? "checkmark" : "circle"
        }
        if index < currentIndex  { return "checkmark" }
        if index == currentIndex { return status.isTerminal ? "checkmark" : "clock" }
        return "circle"
    }
}
