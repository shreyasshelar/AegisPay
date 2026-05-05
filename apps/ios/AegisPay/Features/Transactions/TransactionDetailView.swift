import SwiftUI

struct TransactionDetailView: View {
    @EnvironmentObject var authStore: AuthStore

    let transactionId: String
    @StateObject private var vm: TransactionDetailViewModel

    init(transactionId: String) {
        self.transactionId = transactionId
        _vm = StateObject(wrappedValue: TransactionDetailViewModel(transactionId: transactionId))
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                if vm.isLoading && vm.transaction == nil {
                    detailSkeleton
                } else if let tx = vm.transaction {
                    statusCard(tx)
                    if tx.status == .failed || tx.status == .rolledBack {
                        aiErrorCard(tx)
                    }
                    detailsCard(tx)
                } else if let error = vm.errorMessage {
                    errorCard(error)
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
        }
        .background(Color.aegisBg)
        .navigationTitle("Transaction")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            await vm.load()
            if let token = try? await authStore.validAccessToken() {
                vm.startLiveUpdates(
                    userId:      authStore.currentUser?.id ?? "",
                    accessToken: token
                )
            }
        }
        .onDisappear { vm.stopLiveUpdates() }
    }

    // ── Status card ───────────────────────────────────────────────────────────

    private func statusCard(_ tx: Transaction) -> some View {
        AegisCard {
            VStack(spacing: 20) {
                // Amount + badge
                VStack(spacing: 8) {
                    Text(tx.amount.formatted(currency: tx.currency))
                        .font(.aegisAmount)
                        .foregroundStyle(Color.aegisText)
                    AegisBadge(status: tx.status)
                }

                Divider()

                // Live indicator
                if !tx.status.isTerminal {
                    HStack(spacing: 8) {
                        Circle()
                            .fill(Color.aegisPrimary)
                            .frame(width: 8, height: 8)
                            .overlay(
                                Circle()
                                    .stroke(Color.aegisPrimary.opacity(0.4), lineWidth: 4)
                                    .scaleEffect(1.6)
                            )
                        Text("Live updates active")
                            .font(.aegisCaption)
                            .foregroundStyle(Color.aegisPrimary)
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(Color.aegisPrimaryLight)
                    .clipShape(Capsule())
                }

                // Timeline
                AegisStatusTimeline(status: tx.status)
            }
        }
    }

    // ── AI error resolution card ──────────────────────────────────────────────

    private func aiErrorCard(_ tx: Transaction) -> some View {
        AegisCard {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 8) {
                    Image(systemName: "lightbulb.fill")
                        .foregroundStyle(Color.aegisWarning)
                    Text("AI Error Explanation")
                        .font(.aegisSubhead)
                        .foregroundStyle(Color.aegisText)
                    Spacer()
                    if !vm.isResolvingError {
                        Button {
                            Task {
                                await vm.resolveError(reason: tx.failureReason ?? "UNKNOWN")
                            }
                        } label: {
                            Image(systemName: "arrow.clockwise")
                                .font(.system(size: 13))
                                .foregroundStyle(Color.aegisTextMuted)
                        }
                    }
                }

                Divider()

                if vm.isResolvingError {
                    HStack(spacing: 8) {
                        ProgressView()
                            .scaleEffect(0.8)
                        Text("Analysing failure…")
                            .font(.aegisBodySmall)
                            .foregroundStyle(Color.aegisTextMuted)
                    }
                } else if let resolution = vm.errorResolution {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(resolution.resolution)
                            .font(.aegisBody)
                            .foregroundStyle(Color.aegisText)
                        Text("Code: \(resolution.errorCode)")
                            .font(.aegisMonoSmall)
                            .foregroundStyle(Color.aegisTextMuted)
                    }
                } else if let reason = tx.failureReason {
                    Text(reason)
                        .font(.aegisMonoSmall)
                        .foregroundStyle(Color.aegisDanger)
                }
            }
        }
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Color.aegisWarning.opacity(0.3), lineWidth: 1)
        )
    }

    // ── Details card ──────────────────────────────────────────────────────────

    private func detailsCard(_ tx: Transaction) -> some View {
        AegisCard {
            VStack(alignment: .leading, spacing: 16) {
                Text("Details")
                    .font(.aegisSubhead)
                    .foregroundStyle(Color.aegisText)

                Divider()

                VStack(spacing: 12) {
                    detailRow("Transaction ID", value: tx.transactionId, monospaced: true, copyable: true)
                    detailRow("Payee ID",       value: tx.payeeId, monospaced: true, copyable: true)
                    detailRow("Initiated",      value: formatted(tx.initiatedAt))
                    if let completed = tx.completedAt {
                        detailRow("Completed", value: formatted(completed))
                    }
                    if let reason = tx.failureReason {
                        detailRow("Failure", value: reason, valueColor: Color.aegisDanger)
                    }
                    if let note = tx.note, !note.isEmpty {
                        detailRow("Note", value: note)
                    }
                    detailRow("Currency", value: tx.currency)
                }
            }
        }
    }

    private func detailRow(
        _ label:     String,
        value:       String,
        monospaced:  Bool = false,
        copyable:    Bool = false,
        valueColor:  Color = Color.aegisText
    ) -> some View {
        HStack(alignment: .top, spacing: 8) {
            Text(label)
                .font(.aegisBodySmall)
                .foregroundStyle(Color.aegisTextMuted)
                .frame(width: 100, alignment: .leading)
            Spacer()
            HStack(spacing: 6) {
                Text(value)
                    .font(monospaced ? .aegisMonoSmall : .aegisBodySmall)
                    .foregroundStyle(valueColor)
                    .multilineTextAlignment(.trailing)
                    .lineLimit(3)
                if copyable {
                    Button {
                        UIPasteboard.general.string = value
                        HapticFeedback.success()
                    } label: {
                        Image(systemName: "doc.on.doc")
                            .font(.system(size: 11))
                            .foregroundStyle(Color.aegisTextSubtle)
                    }
                }
            }
        }
    }

    // ── Error / skeleton ──────────────────────────────────────────────────────

    private func errorCard(_ message: String) -> some View {
        AegisCard {
            VStack(spacing: 12) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .font(.system(size: 36))
                    .foregroundStyle(Color.aegisWarning)
                Text("Failed to load transaction")
                    .font(.aegisSubhead)
                Text(message)
                    .font(.aegisBodySmall)
                    .foregroundStyle(Color.aegisTextMuted)
                    .multilineTextAlignment(.center)
                Button("Retry") { Task { await vm.load() } }
                    .aegisButtonStyle(.secondary)
            }
            .frame(maxWidth: .infinity)
        }
    }

    private var detailSkeleton: some View {
        AegisCard {
            VStack(spacing: 16) {
                SkeletonRect(width: 180, height: 40, radius: 8)
                SkeletonRect(width: 100, height: 24, radius: 12)
                Divider()
                ForEach(0..<5, id: \.self) { _ in
                    HStack {
                        SkeletonRect(width: 80, height: 12)
                        Spacer()
                        SkeletonRect(width: 120, height: 12)
                    }
                }
            }
        }
    }

    private func formatted(_ date: Date) -> String {
        let f = DateFormatter()
        f.dateStyle = .medium
        f.timeStyle = .short
        return f.string(from: date)
    }
}
