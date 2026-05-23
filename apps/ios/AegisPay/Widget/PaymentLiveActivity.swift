// ─────────────────────────────────────────────────────────────────────────────
// AegisPay — Payment Live Activity (WidgetKit / ActivityKit)
//
// Displays a payment's real-time status on the Dynamic Island and Lock Screen.
// Lifecycle: started when the user initiates a payment, updated as the saga
// progresses, ended when the transaction reaches a terminal state.
//
// Requirements:
//   • Xcode target: AegisPay Widget Extension (or share the existing app target)
//   • Info.plist key: NSSupportsLiveActivities = YES  (in the main app target)
//   • iOS 16.2+ for Dynamic Island support; degrades gracefully to lock-screen
//     banner on 16.1.
// ─────────────────────────────────────────────────────────────────────────────

@preconcurrency import ActivityKit
import SwiftUI
import WidgetKit

// ── Activity Attributes ───────────────────────────────────────────────────────

/// Static data that does not change once the activity starts.
struct PaymentActivityAttributes: ActivityAttributes {

    /// The dynamic state updated as the saga progresses.
    struct ContentState: Codable, Hashable {
        var status:       PaymentStatus
        var statusLabel:  String
        var amount:       Decimal
        var currency:     String

        enum PaymentStatus: String, Codable {
            case initiated   = "INITIATED"
            case reserved    = "RESERVED"
            case processing  = "PROCESSING"
            case completed   = "COMPLETED"
            case failed      = "FAILED"

            var systemImage: String {
                switch self {
                case .initiated:   return "clock"
                case .reserved:    return "lock.fill"
                case .processing:  return "arrow.triangle.2.circlepath"
                case .completed:   return "checkmark.circle.fill"
                case .failed:      return "xmark.circle.fill"
                }
            }

            var tintColor: Color {
                switch self {
                case .initiated:   return .secondary
                case .reserved:    return .blue
                case .processing:  return .orange
                case .completed:   return .green
                case .failed:      return .red
                }
            }
        }
    }

    // Static: set at activity creation, never updated
    let transactionId: String
    let payeeName:     String
}

// ── Live Activity Lifecycle Helper ────────────────────────────────────────────

/// Helper used from ``TransactionViewModel`` to start, update, and end the live activity.
@MainActor
final class PaymentLiveActivityManager: ObservableObject {

    private var currentActivity: Activity<PaymentActivityAttributes>?

    // MARK: Start

    func startActivity(
        transactionId: String,
        payeeName:     String,
        amount:        Decimal,
        currency:      String
    ) {
        guard ActivityAuthorizationInfo().areActivitiesEnabled else { return }

        let attributes = PaymentActivityAttributes(
            transactionId: transactionId,
            payeeName:     payeeName
        )
        let initialState = PaymentActivityAttributes.ContentState(
            status:      .initiated,
            statusLabel: "Initiating payment…",
            amount:      amount,
            currency:    currency
        )

        do {
            currentActivity = try Activity.request(
                attributes:      attributes,
                contentState:    initialState,
                pushType:        nil   // update via app, not push tokens
            )
        } catch {
            // Live Activities not supported on this device / OS version
        }
    }

    // MARK: Update

    func updateActivity(status: PaymentActivityAttributes.ContentState.PaymentStatus,
                        label: String) async {
        guard let activity = currentActivity else { return }
        var state = activity.contentState
        state.status      = status
        state.statusLabel = label
        await activity.update(using: state)
    }

    // MARK: End

    func endActivity(finalStatus: PaymentActivityAttributes.ContentState.PaymentStatus,
                     label: String) async {
        guard let activity = currentActivity else { return }
        var state = activity.contentState
        state.status      = finalStatus
        state.statusLabel = label
        await activity.end(using: state, dismissalPolicy: .after(.now + 5))
        currentActivity = nil
    }
}

// ── Widget Views ──────────────────────────────────────────────────────────────

/// Lock-screen / notification banner view
struct PaymentLiveActivityView: View {

    let context: ActivityViewContext<PaymentActivityAttributes>

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: context.state.status.systemImage)
                .font(.title2)
                .foregroundColor(context.state.status.tintColor)
                .symbolEffect(.variableColor.iterative, isActive: context.state.status == .processing)

            VStack(alignment: .leading, spacing: 2) {
                Text("Payment to \(context.attributes.payeeName)")
                    .font(.caption)
                    .foregroundColor(.secondary)
                Text(context.state.statusLabel)
                    .font(.subheadline.weight(.semibold))
                    .foregroundColor(.primary)
            }

            Spacer()

            Text(amountString)
                .font(.headline.monospacedDigit())
                .foregroundColor(.primary)
        }
        .padding()
        .activityBackgroundTint(Color(.systemBackground).opacity(0.85))
    }

    private var amountString: String {
        let formatter = NumberFormatter()
        formatter.numberStyle   = .currency
        formatter.currencyCode  = context.state.currency
        return formatter.string(from: context.state.amount as NSDecimalNumber) ?? ""
    }
}

// ── Dynamic Island ────────────────────────────────────────────────────────────

@available(iOS 16.2, *)
struct PaymentDynamicIsland: Widget {

    var body: some WidgetConfiguration {
        ActivityConfiguration(for: PaymentActivityAttributes.self) { context in
            // Lock-screen / banner
            PaymentLiveActivityView(context: context)
        } dynamicIsland: { context in
            DynamicIsland {
                // Expanded view (long-press)
                DynamicIslandExpandedRegion(.leading) {
                    Image(systemName: context.state.status.systemImage)
                        .foregroundColor(context.state.status.tintColor)
                        .font(.title3)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    Text(amountString(context: context))
                        .font(.caption.monospacedDigit())
                }
                DynamicIslandExpandedRegion(.bottom) {
                    Text(context.state.statusLabel)
                        .font(.caption2)
                        .foregroundColor(.secondary)
                }
            } compactLeading: {
                Image(systemName: "creditcard.fill")
                    .foregroundColor(.accentColor)
            } compactTrailing: {
                Image(systemName: context.state.status.systemImage)
                    .foregroundColor(context.state.status.tintColor)
            } minimal: {
                Image(systemName: context.state.status.systemImage)
                    .foregroundColor(context.state.status.tintColor)
            }
            .contentMargins(.horizontal, 8, for: .expanded)
        }
    }

    private func amountString(context: ActivityViewContext<PaymentActivityAttributes>) -> String {
        let f = NumberFormatter()
        f.numberStyle  = .currency
        f.currencyCode = context.state.currency
        return f.string(from: context.state.amount as NSDecimalNumber) ?? ""
    }
}
