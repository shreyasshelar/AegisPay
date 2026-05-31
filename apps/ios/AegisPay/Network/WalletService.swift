import Foundation

// ── WalletService ─────────────────────────────────────────────────────────────
// All wallet top-up API calls.  Follows the same pattern as AccountService.

@MainActor
final class WalletService {

    private let api: ApiClient

    init(api: ApiClient = .shared) { self.api = api }

    // ── Balance ───────────────────────────────────────────────────────────────

    /// Returns the authenticated user's ledger accounts.
    func getMyAccounts() async throws -> [Account] {
        try await api.get(path: "/api/v1/ledger/accounts/me")
    }

    // ── Top-up ────────────────────────────────────────────────────────────────

    /// Step 1: create a Stripe PaymentIntent on the backend.
    /// The `clientSecret` in the response is passed to the Stripe iOS SDK.
    func createTopUpIntent(amount: Decimal, currency: String = "INR") async throws -> TopUpIntentResponse {
        let body = TopUpIntentRequest(amount: amount, currency: currency)
        return try await api.post(path: "/api/v1/ledger/topup/intent", body: body)
    }

    /// Step 2: after the Stripe SDK confirms payment, notify the backend to credit balance.
    func confirmTopUp(paymentIntentId: String) async throws {
        let body = TopUpConfirmRequest(paymentIntentId: paymentIntentId)
        // Confirm returns Void wrapped in ApiResponse<null>; decode as EmptyResponse.
        let _: EmptyResponse = try await api.post(path: "/api/v1/ledger/topup/confirm", body: body)
    }
}

private struct EmptyResponse: Decodable {}
