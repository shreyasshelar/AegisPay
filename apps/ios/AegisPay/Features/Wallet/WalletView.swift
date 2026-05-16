import SwiftUI
import StripePaymentSheet

// ── WalletView ────────────────────────────────────────────────────────────────

struct WalletView: View {

    @StateObject private var viewModel = WalletViewModel()

    @State private var showTopUpSheet  = false
    @State private var amountText:  String  = ""
    @State private var amountError: String? = nil

    // Stripe PaymentSheet state
    @State private var paymentSheet: PaymentSheet?

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 20) {

                    // ── Balance cards ─────────────────────────────────────────
                    if viewModel.isLoading && viewModel.accounts.isEmpty {
                        ProgressView("Loading wallet…")
                            .frame(maxWidth: .infinity, minHeight: 120)
                    } else if viewModel.accounts.isEmpty {
                        emptyWalletCard
                    } else {
                        ForEach(viewModel.accounts) { account in
                            BalanceCard(account: account)
                        }
                    }

                    // ── Add money button ──────────────────────────────────────
                    Button {
                        amountText  = ""
                        amountError = nil
                        showTopUpSheet = true
                    } label: {
                        Label("Add Money", systemImage: "plus.circle.fill")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .controlSize(.large)
                    .tint(Color.aegisPrimary)
                    .disabled(viewModel.topUpPhase == .creatingIntent
                              || viewModel.topUpPhase == .confirmingWithBackend)

                    // ── Phase feedback ────────────────────────────────────────
                    topUpFeedback
                }
                .padding()
            }
            .navigationTitle("My Wallet")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button { viewModel.loadAccounts() } label: {
                        Image(systemName: "arrow.clockwise")
                    }
                }
            }
            .onAppear { viewModel.loadAccounts() }

            // ── General error alert ───────────────────────────────────────────
            .alert("Error", isPresented: Binding(
                get: { viewModel.errorMessage != nil },
                set: { if !$0 { viewModel.errorMessage = nil } }
            )) {
                Button("OK", role: .cancel) { viewModel.errorMessage = nil }
            } message: {
                Text(viewModel.errorMessage ?? "")
            }

            // ── Amount entry bottom sheet ─────────────────────────────────────
            .sheet(isPresented: $showTopUpSheet) {
                TopUpAmountSheet(
                    amountText:  $amountText,
                    amountError: $amountError,
                    isLoading:   viewModel.topUpPhase == .creatingIntent,
                    onConfirm: { amount in
                        showTopUpSheet = false
                        viewModel.startTopUp(amount: amount)
                    },
                    onCancel: { showTopUpSheet = false }
                )
                .presentationDetents([.medium])
            }

            // ── Stripe PaymentSheet ───────────────────────────────────────────
            // When the backend returns a clientSecret, build a PaymentSheet and present it.
            .onChange(of: viewModel.topUpPhase) { _, phase in
                if case .awaitingStripeConfirmation(let clientSecret) = phase {
                    var config = PaymentSheet.Configuration()
                    config.merchantDisplayName  = "AegisPay"
                    config.allowsDelayedPaymentMethods = false
                    paymentSheet = PaymentSheet(
                        paymentIntentClientSecret: clientSecret,
                        configuration: config
                    )
                }
            }
            // paymentSheet(isPresented:) modifier — presents as soon as paymentSheet is non-nil
            .paymentSheet(
                isPresented: Binding(
                    get: { paymentSheet != nil },
                    set: { if !$0 { paymentSheet = nil } }
                ),
                paymentSheet: paymentSheet ?? dummyPaymentSheet(),
                onCompletion: handlePaymentSheetResult
            )
        }
    }

    // ── Stripe result handler ─────────────────────────────────────────────────

    private func handlePaymentSheetResult(_ result: PaymentSheetResult) {
        paymentSheet = nil
        switch result {
        case .completed:
            // Stripe SDK confirms the payment; notify our backend to credit balance.
            if let piId = viewModel.pendingPaymentIntentId {
                viewModel.confirmTopUp(paymentIntentId: piId)
            }
        case .canceled:
            viewModel.resetTopUpPhase()
        case .failed(let error):
            viewModel.handlePaymentSheetFailure(error.localizedDescription)
        }
    }

    // PaymentSheet requires a non-optional binding; provide a harmless placeholder.
    // It is never presented because the binding's get returns false when paymentSheet == nil.
    private func dummyPaymentSheet() -> PaymentSheet {
        PaymentSheet(
            paymentIntentClientSecret: "pi_placeholder_secret_placeholder",
            configuration: PaymentSheet.Configuration()
        )
    }

    // ── Sub-views ─────────────────────────────────────────────────────────────

    @ViewBuilder
    private var emptyWalletCard: some View {
        AegisCard {
            VStack(spacing: 10) {
                Image(systemName: "wallet.pass")
                    .font(.system(size: 44))
                    .foregroundStyle(Color.secondary)
                Text("Your wallet will appear here once your account is set up.")
                    .font(.subheadline)
                    .foregroundStyle(Color.secondary)
                    .multilineTextAlignment(.center)
            }
            .padding()
        }
    }

    @ViewBuilder
    private var topUpFeedback: some View {
        switch viewModel.topUpPhase {
        case .creatingIntent, .confirmingWithBackend:
            HStack(spacing: 10) {
                ProgressView()
                Text(viewModel.topUpPhase == .creatingIntent
                     ? "Preparing payment…"
                     : "Crediting wallet…")
                    .font(.subheadline)
                    .foregroundStyle(Color.secondary)
            }
        case .success:
            HStack(spacing: 8) {
                Image(systemName: "checkmark.circle.fill")
                    .foregroundStyle(Color.green)
                Text("Top-up successful! Your balance has been updated.")
                    .font(.subheadline)
                    .foregroundStyle(Color.green)
            }
            .padding()
            .background(Color.green.opacity(0.08), in: RoundedRectangle(cornerRadius: 10))
            .onAppear {
                DispatchQueue.main.asyncAfter(deadline: .now() + 3) {
                    viewModel.resetTopUpPhase()
                }
            }
        case .failure(let message):
            HStack(alignment: .top, spacing: 8) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .foregroundStyle(Color.red)
                VStack(alignment: .leading, spacing: 4) {
                    Text("Top-up failed")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.red)
                    Text(message)
                        .font(.caption)
                        .foregroundStyle(Color.secondary)
                }
                Spacer()
                Button { viewModel.resetTopUpPhase() } label: {
                    Image(systemName: "xmark").font(.caption)
                }
                .foregroundStyle(Color.secondary)
            }
            .padding()
            .background(Color.red.opacity(0.07), in: RoundedRectangle(cornerRadius: 10))
        default:
            EmptyView()
        }
    }
}

// ── Balance Card ──────────────────────────────────────────────────────────────

private struct BalanceCard: View {
    let account: Account

    private var formattedBalance: String { format(account.availableBalance) }
    private var formattedReserved: String { format(account.reservedBalance) }

    private func format(_ amount: Decimal) -> String {
        let nf = NumberFormatter()
        nf.numberStyle           = .currency
        nf.currencyCode          = account.currency
        nf.maximumFractionDigits = 2
        return nf.string(from: amount as NSDecimalNumber) ?? "\(amount)"
    }

    var body: some View {
        AegisCard {
            VStack(alignment: .leading, spacing: 6) {
                Label(account.currency, systemImage: "banknote")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(Color.aegisPrimary)

                Text(formattedBalance)
                    .font(.system(size: 34, weight: .bold, design: .rounded))
                    .foregroundStyle(Color.primary)

                if account.reservedBalance > 0 {
                    Text("Reserved: \(formattedReserved)")
                        .font(.caption)
                        .foregroundStyle(Color.secondary)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding()
        }
    }
}

// ── Top-up amount sheet ───────────────────────────────────────────────────────

private struct TopUpAmountSheet: View {
    @Binding var amountText:  String
    @Binding var amountError: String?
    let isLoading: Bool
    let onConfirm: (Decimal) -> Void
    let onCancel:  () -> Void

    private let presets: [Int] = [500, 1_000, 2_000, 5_000]

    var body: some View {
        NavigationView {
            Form {
                Section("Amount (INR)") {
                    TextField("e.g. 1000", text: $amountText)
                        .keyboardType(.decimalPad)

                    if let err = amountError {
                        Text(err)
                            .font(.caption)
                            .foregroundStyle(Color.red)
                    }
                }

                Section("Quick amounts") {
                    LazyVGrid(
                        columns: Array(repeating: .init(.flexible()), count: 4),
                        spacing: 10
                    ) {
                        ForEach(presets, id: \.self) { preset in
                            Button("₹\(preset)") {
                                amountText  = "\(preset)"
                                amountError = nil
                            }
                            .buttonStyle(.bordered)
                            .tint(amountText == "\(preset)" ? .aegisPrimary : .secondary)
                        }
                    }
                }

                Section {
                    Button {
                        guard let amount = Decimal(string: amountText), amount >= 1 else {
                            amountError = "Enter a valid amount (minimum ₹1)"
                            return
                        }
                        amountError = nil
                        onConfirm(amount)
                    } label: {
                        if isLoading {
                            HStack {
                                ProgressView()
                                Text("Preparing…")
                            }
                            .frame(maxWidth: .infinity, alignment: .center)
                        } else {
                            Text("Continue to Payment")
                                .frame(maxWidth: .infinity, alignment: .center)
                        }
                    }
                    .disabled(isLoading)
                }
            }
            .navigationTitle("Add Money")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel", action: onCancel)
                }
            }
        }
    }
}
