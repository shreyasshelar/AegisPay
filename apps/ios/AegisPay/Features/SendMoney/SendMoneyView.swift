import SwiftUI

private let CURRENCIES = ["INR", "USD", "EUR", "GBP"]

struct SendMoneyView: View {
    @EnvironmentObject var authStore: AuthStore
    @StateObject private var vm = SendMoneyViewModel()

    var body: some View {
        NavigationStack {
            Group {
                if vm.kycLoading {
                    // Brief loading state to avoid flash of blocked screen on fast connections
                    ProgressView()
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .background(Color.aegisBg)
                } else if let kyc = vm.kycStatus, kyc != .approved {
                    kycBlockedView
                } else {
                    sendWizard
                }
            }
            .navigationTitle("Send Money")
            .navigationBarTitleDisplayMode(.large)
            .task { await vm.loadKycStatus(userId: authStore.currentUser?.id ?? "") }
            .onAppear { Task { await vm.loadKycStatus(userId: authStore.currentUser?.id ?? "") } }
        }
    }

    // MARK: — KYC blocked screen

    private var kycBlockedView: some View {
        VStack(spacing: 24) {
            Spacer()
            VStack(spacing: 20) {
                ZStack {
                    Circle()
                        .fill(Color.aegisWarning.opacity(0.1))
                        .frame(width: 80, height: 80)
                    Image(systemName: "shield.slash.fill")
                        .font(.system(size: 34))
                        .foregroundStyle(Color.aegisWarning)
                }

                VStack(spacing: 8) {
                    Text("Identity verification required")
                        .font(.aegisSubhead)
                        .fontWeight(.semibold)
                        .foregroundStyle(Color.aegisText)
                    Text("You need to complete KYC verification before sending money. This protects you and your recipients from fraud.")
                        .font(.aegisBody)
                        .foregroundStyle(Color.aegisTextMuted)
                        .multilineTextAlignment(.center)
                }
            }
            .padding(.horizontal, 32)

            NavigationLink(destination: ProfileView()) {
                Label("Complete KYC now", systemImage: "arrow.right")
                    .font(.aegisBodySmall)
                    .fontWeight(.semibold)
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
                    .background(Color.aegisPrimary)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
            }
            .padding(.horizontal, 32)

            Spacer()
        }
        .background(Color.aegisBg)
    }

    // MARK: — Wizard

    private var sendWizard: some View {
        VStack(spacing: 0) {
            // Step indicator (hidden on status screen)
            if vm.step != .status {
                stepIndicator
                    .padding(.horizontal, 24)
                    .padding(.top, 16)
                    .padding(.bottom, 8)
            }

            ScrollView {
                VStack(spacing: 16) {
                    switch vm.step {
                    case .payee:  payeeStep
                    case .amount: amountStep
                    case .review: reviewStep
                    case .status: statusStep
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            }
        }
        .background(Color.aegisBg)
        .animation(.easeInOut(duration: 0.25), value: vm.step)
    }

    // MARK: — Step indicator

    private var stepIndicator: some View {
        let steps = ["Payee", "Amount", "Review"]
        let stepMap: [SendStep: Int] = [.payee: 0, .amount: 1, .review: 2]
        let idx = stepMap[vm.step] ?? 0

        return HStack(spacing: 0) {
            ForEach(Array(steps.enumerated()), id: \.offset) { i, label in
                HStack(spacing: 0) {
                    // Circle
                    ZStack {
                        Circle()
                            .fill(i <= idx ? Color.aegisPrimary : Color.aegisBorder)
                            .frame(width: 28, height: 28)
                        if i < idx {
                            Image(systemName: "checkmark")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundStyle(.white)
                        } else {
                            Text("\(i + 1)")
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundStyle(i <= idx ? .white : Color.aegisTextSubtle)
                        }
                    }
                    // Label
                    Text(label)
                        .font(.aegisCaption)
                        .fontWeight(i == idx ? .semibold : .regular)
                        .foregroundStyle(i == idx ? Color.aegisPrimary : Color.aegisTextSubtle)
                        .padding(.leading, 4)

                    // Connector
                    if i < steps.count - 1 {
                        Rectangle()
                            .fill(i < idx ? Color.aegisPrimary : Color.aegisBorder)
                            .frame(height: 2)
                            .padding(.horizontal, 8)
                    }
                }
            }
        }
    }

    // MARK: — Step 1: Payee

    private var payeeStep: some View {
        VStack(spacing: 16) {
            AegisCard {
                VStack(spacing: 20) {
                    stepHeader(
                        icon: "person.circle.fill",
                        title: "Who are you sending to?",
                        subtitle: "Enter the recipient's AegisPay user ID"
                    )

                    AegisTextField(
                        label:            "Payee ID (UUID)",
                        placeholder:      "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
                        text:             $vm.payeeId,
                        error:            vm.payeeIdError,
                        autocapitalization: .never
                    )

                    Text("You can find a recipient's user ID on their AegisPay profile page.")
                        .font(.aegisCaption)
                        .foregroundStyle(Color.aegisTextSubtle)
                        .padding(10)
                        .background(Color.aegisSurface)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                }
            }

            Button { vm.goTo(.amount) } label: {
                Label("Continue", systemImage: "arrow.right")
            }
            .aegisButtonStyle(.primary, fullWidth: true)
            .disabled(!vm.isPayeeValid)
        }
    }

    // MARK: — Step 2: Amount

    private var amountStep: some View {
        VStack(spacing: 16) {
            AegisCard {
                VStack(spacing: 20) {
                    stepHeader(
                        icon: "banknote.fill",
                        title: "How much to send?",
                        subtitle: "Enter the amount and an optional note"
                    )

                    // Amount + currency row
                    HStack(alignment: .top, spacing: 12) {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Amount")
                                .font(.aegisBodySmall)
                                .fontWeight(.medium)
                                .foregroundStyle(Color.aegisText)
                            AegisAmountField(
                                currency:  vm.currency,
                                text:      $vm.amountText,
                                error:     vm.amountError
                            )
                        }

                        VStack(alignment: .leading, spacing: 6) {
                            Text("Currency")
                                .font(.aegisBodySmall)
                                .fontWeight(.medium)
                                .foregroundStyle(Color.aegisText)
                            Picker("Currency", selection: $vm.currency) {
                                ForEach(CURRENCIES, id: \.self) { Text($0).tag($0) }
                            }
                            .pickerStyle(.menu)
                            .frame(height: 46)
                            .padding(.horizontal, 12)
                            .background(Color.aegisSurface)
                            .clipShape(RoundedRectangle(cornerRadius: 10))
                            .overlay(
                                RoundedRectangle(cornerRadius: 10)
                                    .stroke(Color.aegisBorder, lineWidth: 1.5)
                            )
                        }
                    }

                    // Note
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Note (optional)")
                            .font(.aegisBodySmall)
                            .fontWeight(.medium)
                            .foregroundStyle(Color.aegisText)
                        TextField("What's this for?", text: $vm.note, axis: .vertical)
                            .font(.aegisBody)
                            .lineLimit(2...4)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 12)
                            .background(Color.aegisSurface)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                            .overlay(
                                RoundedRectangle(cornerRadius: 12)
                                    .stroke(Color.aegisBorder, lineWidth: 1.5)
                            )
                    }
                }
            }

            HStack(spacing: 12) {
                Button { vm.back() } label: {
                    Label("Back", systemImage: "arrow.left")
                }
                .aegisButtonStyle(.secondary, fullWidth: true)

                Button { vm.goTo(.review) } label: {
                    Label("Review", systemImage: "arrow.right")
                }
                .aegisButtonStyle(.primary, fullWidth: true)
                .disabled(!vm.isAmountValid)
            }
        }
    }

    // MARK: — Step 3: Review

    private var reviewStep: some View {
        VStack(spacing: 16) {
            AegisCard {
                VStack(spacing: 20) {
                    stepHeader(
                        icon: "checkmark.shield.fill",
                        title: "Review your transfer",
                        subtitle: "Double-check the details before sending"
                    )

                    // Amount display
                    VStack(spacing: 6) {
                        Text(formattedAmount)
                            .font(.system(size: 34, weight: .bold, design: .rounded))
                            .foregroundStyle(Color.aegisText)
                        Text("Total Amount")
                            .font(.aegisCaption)
                            .fontWeight(.medium)
                            .foregroundStyle(Color.aegisTextSubtle)
                            .textCase(.uppercase)
                            .tracking(1.5)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(Color.aegisSurface)
                    .clipShape(RoundedRectangle(cornerRadius: 12))

                    Divider()

                    // Detail rows
                    VStack(spacing: 12) {
                        reviewRow(label: "To", value: vm.payeeId, mono: true)
                        reviewRow(label: "Currency", value: vm.currency)
                        if !vm.note.isEmpty {
                            reviewRow(label: "Note", value: vm.note)
                        }
                    }
                }
            }

            // Risk notice
            HStack(spacing: 10) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .foregroundStyle(Color.aegisWarning)
                Text("This transfer will undergo real-time risk assessment. Funds are reserved immediately and released only after all checks pass.")
                    .font(.aegisCaption)
                    .foregroundStyle(Color.aegisWarning)
                    .lineSpacing(3)
            }
            .padding(12)
            .background(Color.aegisWarningLight)
            .clipShape(RoundedRectangle(cornerRadius: 10))

            if let err = vm.submissionError {
                errorBanner(err)
            }

            HStack(spacing: 12) {
                Button { vm.back() } label: {
                    Label("Edit", systemImage: "arrow.left")
                }
                .aegisButtonStyle(.secondary, fullWidth: true)
                .disabled(vm.isSubmitting)

                Button {
                    Task { await vm.submit() }
                } label: {
                    Label(
                        vm.isSubmitting ? "Sending…" : "Confirm & Send",
                        systemImage: "paperplane.fill"
                    )
                }
                .aegisButtonStyle(.primary, loading: vm.isSubmitting, fullWidth: true)
            }
        }
    }

    // MARK: — Step 4: Status

    private var statusStep: some View {
        VStack(spacing: 16) {
            if let tx = vm.createdTx {
                // Main status card
                AegisCard {
                    VStack(spacing: 20) {
                        // Icon + amount + label
                        VStack(spacing: 8) {
                            statusIcon(tx.status)

                            Text(formattedAmount)
                                .font(.system(size: 28, weight: .bold, design: .rounded))
                                .foregroundStyle(Color.aegisText)

                            Text(statusLabel(tx.status))
                                .font(.aegisBodySmall)
                                .fontWeight(.semibold)
                                .foregroundStyle(statusLabelColor(tx.status))
                        }
                        .frame(maxWidth: .infinity)

                        // Live badge
                        if !tx.status.isTerminal {
                            HStack(spacing: 8) {
                                ProgressView()
                                    .scaleEffect(0.8)
                                    .tint(Color.aegisPrimary)
                                Text("Live updates active")
                                    .font(.aegisCaption)
                                    .foregroundStyle(Color.aegisPrimary)
                            }
                            .padding(.horizontal, 14)
                            .padding(.vertical, 8)
                            .background(Color.aegisPrimaryLight)
                            .clipShape(Capsule())
                        }

                        // Timeline
                        AegisStatusTimeline(status: tx.status)
                    }
                }

                // AI error card — failure only
                if tx.status == .failed || tx.status == .rolledBack {
                    aiErrorCard(tx)
                }

                // CTA buttons
                VStack(spacing: 12) {
                    NavigationLink(destination: TransactionDetailView(transactionId: tx.transactionId)) {
                        Label("View Full Details", systemImage: "arrow.up.right.square")
                    }
                    .aegisButtonStyle(.primary, fullWidth: true)

                    Button {
                        vm.reset()
                    } label: {
                        Label(
                            (tx.status == .failed || tx.status == .rolledBack) ? "Try Again" : "Send Another",
                            systemImage: "arrow.counterclockwise"
                        )
                    }
                    .aegisButtonStyle(.secondary, fullWidth: true)
                }

            } else {
                // Loading placeholder before tx is available
                VStack(spacing: 16) {
                    ProgressView()
                        .scaleEffect(1.4)
                        .tint(Color.aegisPrimary)
                    Text("Starting transfer…")
                        .font(.aegisBody)
                        .foregroundStyle(Color.aegisTextMuted)
                }
                .frame(maxWidth: .infinity, minHeight: 200)
            }
        }
        .task {
            if let token = try? await authStore.validAccessToken() {
                vm.startLiveUpdates(
                    userId:      authStore.currentUser?.id ?? "",
                    accessToken: token
                )
            }
        }
        .onDisappear { vm.stopLiveUpdates() }
    }

    // MARK: — AI error card

    private func aiErrorCard(_ tx: Transaction) -> some View {
        AegisCard {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 8) {
                    Image(systemName: "lightbulb.fill")
                        .foregroundStyle(Color.aegisWarning)
                    Text("What went wrong?")
                        .font(.aegisBodySmall)
                        .fontWeight(.semibold)
                        .foregroundStyle(Color.aegisText)
                }

                if vm.isResolvingError {
                    HStack(spacing: 8) {
                        ProgressView().scaleEffect(0.8)
                        Text("Analysing failure…")
                            .font(.aegisBodySmall)
                            .foregroundStyle(Color.aegisTextMuted)
                    }
                } else if let resolution = vm.errorResolution {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(resolution.resolution)
                            .font(.aegisBodySmall)
                            .foregroundStyle(Color.aegisText)
                            .lineSpacing(4)
                        Text("Code: \(resolution.errorCode)")
                            .font(.system(.caption, design: .monospaced))
                            .foregroundStyle(Color.aegisWarning)
                    }
                } else if let reason = tx.failureReason {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(reason)
                            .font(.system(.caption, design: .monospaced))
                            .foregroundStyle(Color.aegisTextMuted)
                        Button {
                            Task { await vm.resolveError(reason: reason) }
                        } label: {
                            Label("Retry Analysis", systemImage: "arrow.clockwise")
                                .font(.aegisCaption)
                        }
                        .buttonStyle(.plain)
                        .foregroundStyle(Color.aegisWarning)
                    }
                }
            }
        }
        .background(Color.aegisWarningLight)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Color.aegisWarning.opacity(0.3), lineWidth: 1)
        )
    }

    // MARK: — Helpers

    private var formattedAmount: String {
        guard let amt = vm.amount else { return "\(vm.currency) \(vm.amountText)" }
        let formatter = NumberFormatter()
        formatter.numberStyle = .currency
        formatter.currencyCode = vm.currency
        formatter.maximumFractionDigits = 2
        return formatter.string(from: amt as NSDecimalNumber) ?? "\(vm.currency) \(amt)"
    }

    private func stepHeader(icon: String, title: String, subtitle: String) -> some View {
        HStack(spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 10)
                    .fill(Color.aegisPrimaryLight)
                    .frame(width: 40, height: 40)
                Image(systemName: icon)
                    .font(.system(size: 18))
                    .foregroundStyle(Color.aegisPrimary)
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.aegisSubhead)
                    .foregroundStyle(Color.aegisText)
                Text(subtitle)
                    .font(.aegisCaption)
                    .foregroundStyle(Color.aegisTextSubtle)
            }
            Spacer()
        }
    }

    private func reviewRow(label: String, value: String, mono: Bool = false) -> some View {
        HStack(alignment: .top) {
            Text(label)
                .font(.aegisBodySmall)
                .foregroundStyle(Color.aegisTextMuted)
            Spacer()
            Text(value)
                .font(mono ? .system(.caption, design: .monospaced) : .aegisBodySmall)
                .fontWeight(mono ? .regular : .medium)
                .foregroundStyle(Color.aegisText)
                .multilineTextAlignment(.trailing)
                .lineLimit(mono ? 2 : 3)
        }
    }

    private func errorBanner(_ message: String) -> some View {
        HStack(spacing: 10) {
            Image(systemName: "xmark.circle.fill")
                .foregroundStyle(Color.aegisDanger)
            Text(message)
                .font(.aegisBodySmall)
                .foregroundStyle(Color.aegisDanger)
                .lineLimit(3)
            Spacer()
        }
        .padding(14)
        .background(Color.aegisDangerLight)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    @ViewBuilder
    private func statusIcon(_ status: TransactionStatus) -> some View {
        switch status {
        case .completed:
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 64))
                .foregroundStyle(Color.aegisSuccess)
        case .failed, .rolledBack:
            Image(systemName: "xmark.circle.fill")
                .font(.system(size: 64))
                .foregroundStyle(Color.aegisDanger)
        default:
            ZStack {
                Circle()
                    .fill(Color.aegisPrimaryLight)
                    .frame(width: 64, height: 64)
                ProgressView()
                    .scaleEffect(1.2)
                    .tint(Color.aegisPrimary)
            }
        }
    }

    private func statusLabel(_ status: TransactionStatus) -> String {
        switch status {
        case .completed:              return "Transfer Complete"
        case .failed, .rolledBack:    return "Transfer Failed"
        default:                      return "Transfer in Progress…"
        }
    }

    private func statusLabelColor(_ status: TransactionStatus) -> Color {
        switch status {
        case .completed:           return Color.aegisSuccess
        case .failed, .rolledBack: return Color.aegisDanger
        default:                   return Color.aegisPrimary
        }
    }
}
