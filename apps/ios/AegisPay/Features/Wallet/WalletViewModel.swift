import Foundation
import Combine

// ── State ─────────────────────────────────────────────────────────────────────

enum TopUpPhase: Equatable {
    case idle
    case creatingIntent
    /// Stripe SDK should be presented with this clientSecret
    case awaitingStripeConfirmation(clientSecret: String)
    case confirmingWithBackend
    case success
    case failure(message: String)
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

@MainActor
final class WalletViewModel: ObservableObject {

    // ── Published ──────────────────────────────────────────────────────────────
    @Published var accounts:     [Account]  = []
    @Published var isLoading:    Bool       = false
    @Published var errorMessage: String?    = nil
    @Published var topUpPhase:   TopUpPhase = .idle

    /**
     * The PaymentIntent ID (e.g. "pi_xxx") extracted from the clientSecret.
     * The WalletView passes this to `confirmTopUp` after Stripe SDK reports `.completed`.
     */
    private(set) var pendingPaymentIntentId: String? = nil

    // ── Dependencies ──────────────────────────────────────────────────────────
    private let walletService: WalletService

    init(walletService: WalletService = WalletService()) {
        self.walletService = walletService
    }

    // ── Load accounts ─────────────────────────────────────────────────────────

    func loadAccounts() {
        Task {
            isLoading    = true
            errorMessage = nil
            defer { isLoading = false }
            do {
                accounts = try await walletService.getMyAccounts()
            } catch {
                errorMessage = error.localizedDescription
            }
        }
    }

    // ── Step 1: create PaymentIntent ──────────────────────────────────────────

    /**
     * Calls the backend to create a Stripe PaymentIntent.
     * On success transitions `topUpPhase` to `.awaitingStripeConfirmation(clientSecret:)`
     * so `WalletView` can build and present the Stripe `PaymentSheet`.
     */
    func startTopUp(amount: Decimal, currency: String = "INR") {
        guard topUpPhase == .idle else { return }
        topUpPhase = .creatingIntent
        Task {
            do {
                let intent = try await walletService.createTopUpIntent(amount: amount, currency: currency)
                // Store the pi_xxx so confirmTopUp can send it to the backend
                pendingPaymentIntentId = intent.paymentIntentId
                topUpPhase = .awaitingStripeConfirmation(clientSecret: intent.clientSecret)
            } catch {
                topUpPhase = .failure(message: "Could not start top-up: \(error.localizedDescription)")
            }
        }
    }

    // ── Step 2: confirm after Stripe SDK succeeds ─────────────────────────────

    /**
     * Called by `WalletView` when `PaymentSheetResult.completed` fires.
     * Notifies the backend to credit the user's ledger balance.
     */
    func confirmTopUp(paymentIntentId: String) {
        topUpPhase = .confirmingWithBackend
        Task {
            do {
                try await walletService.confirmTopUp(paymentIntentId: paymentIntentId)
                pendingPaymentIntentId = nil
                topUpPhase = .success
                loadAccounts()   // refresh balance
            } catch {
                topUpPhase = .failure(
                    message: "Payment confirmed but failed to credit wallet: \(error.localizedDescription)"
                )
            }
        }
    }

    // ── Called by WalletView on PaymentSheet failure ───────────────────────────

    func handlePaymentSheetFailure(_ message: String) {
        pendingPaymentIntentId = nil
        topUpPhase = .failure(message: message)
    }

    func resetTopUpPhase() {
        pendingPaymentIntentId = nil
        topUpPhase = .idle
    }
}
