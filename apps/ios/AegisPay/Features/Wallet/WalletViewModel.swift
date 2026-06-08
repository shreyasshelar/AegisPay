import Foundation
import Combine

// ── Constants ─────────────────────────────────────────────────────────────────

/// Hard INR balance ceiling — Stripe KYC threshold.
/// Topping up is blocked when the INR account would exceed this.
let BALANCE_LIMIT_INR: Decimal = 100_000

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

    /// Live ECB-sourced FX rates (1 INR → X foreign currency).
    @Published private(set) var fxRates: FxRates = FxRates(usd: 0.01190, eur: 0.01099, gbp: 0.00936)
    @Published private(set) var fxLoading: Bool = false

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

    // ── Load accounts + FX rates ──────────────────────────────────────────────

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
        loadFxRates()
    }

    func loadFxRates() {
        Task {
            fxLoading = true
            fxRates   = await FxRateService.shared.rates()
            fxLoading = false
        }
    }

    // ── Balance limit helpers ─────────────────────────────────────────────────

    /// INR available balance (nil when no INR account).
    var inrAvailable: Decimal? {
        accounts.first(where: { $0.currency == "INR" })?.availableBalance
    }

    /// Headroom left until the INR wallet cap.
    var inrRoom: Decimal {
        max(BALANCE_LIMIT_INR - (inrAvailable ?? 0), 0)
    }

    /// `true` when adding `amount` INR would breach the limit.
    func wouldExceedLimit(amount: Decimal) -> Bool {
        (inrAvailable ?? 0) + amount > BALANCE_LIMIT_INR
    }

    /// Human-readable wallet limit line, e.g. "Wallet limit: ₹1,00,000 (~£936 live)"
    func limitLine(currency: String) -> String {
        let limitFormatted = BALANCE_LIMIT_INR.formatted(currency: "INR")
        if currency == "INR" {
            return "Wallet limit: \(limitFormatted)"
        }
        if let converted = fxRates.convert(inr: BALANCE_LIMIT_INR, to: currency) {
            let convFormatted = converted.formatted(currency: currency)
            return "Wallet limit: \(limitFormatted) (~\(convFormatted) live)"
        }
        return "Wallet limit: \(limitFormatted)"
    }

    /// Headroom line, e.g. "Room: ₹99,800 (~£934 live)"
    func roomLine(currency: String) -> String {
        let room = inrRoom
        let roomFormatted = room.formatted(currency: "INR")
        if currency == "INR" {
            return "Room: \(roomFormatted)"
        }
        if let converted = fxRates.convert(inr: room, to: currency) {
            let convFormatted = converted.formatted(currency: currency)
            return "Room: \(roomFormatted) (~\(convFormatted) live)"
        }
        return "Room: \(roomFormatted)"
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
