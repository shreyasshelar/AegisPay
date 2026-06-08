import Foundation

// ── FX Rate Service ───────────────────────────────────────────────────────────
//
// Fetches live exchange rates from Frankfurter API (ECB-sourced, no API key).
// Endpoint: https://api.frankfurter.app/latest?base=INR&symbols=USD,EUR,GBP
// Response: { "rates": { "USD": 0.01190, "EUR": 0.01099, "GBP": 0.00936 } }
// Meaning: 1 INR = rates[currency] units of that currency.
//
// 1-hour in-memory cache — avoids hammering the free API.
//
// Usage:
//   let rates = try await FxRateService.shared.rates()
//   let inUSD = inrAmount * Decimal(rates["USD"] ?? 0)
// ─────────────────────────────────────────────────────────────────────────────

struct FxRates {
    let usd: Double
    let eur: Double
    let gbp: Double

    /// Returns the rate for 1 INR → `currency`, or nil if currency == "INR".
    func rate(for currency: String) -> Double? {
        switch currency.uppercased() {
        case "USD": return usd
        case "EUR": return eur
        case "GBP": return gbp
        default:    return nil
        }
    }

    /// Convert an INR amount to the target currency.
    func convert(inr: Decimal, to currency: String) -> Decimal? {
        guard let r = rate(for: currency) else { return nil }
        return inr * Decimal(r)
    }
}

@MainActor
final class FxRateService {

    static let shared = FxRateService()
    private init() {}

    private var cached: FxRates?
    private var fetchedAt: Date?
    private let cacheTTL: TimeInterval = 3600 // 1 hour

    private let endpoint = URL(string: "https://api.frankfurter.app/latest?base=INR&symbols=USD,EUR,GBP")!

    // ── Public API ────────────────────────────────────────────────────────────

    func rates() async -> FxRates {
        if let cached, let fetchedAt, Date().timeIntervalSince(fetchedAt) < cacheTTL {
            return cached
        }
        do {
            let fresh = try await fetch()
            cached    = fresh
            fetchedAt = Date()
            return fresh
        } catch {
            // Return stale cache if available; otherwise fall back to hard-coded ECB mid rates.
            return cached ?? FxRates(usd: 0.01190, eur: 0.01099, gbp: 0.00936)
        }
    }

    /// Invalidate cache (useful for manual refresh).
    func invalidate() { cached = nil; fetchedAt = nil }

    // ── Private ───────────────────────────────────────────────────────────────

    private func fetch() async throws -> FxRates {
        let (data, _) = try await URLSession.shared.data(from: endpoint)
        let decoded   = try JSONDecoder().decode(FrankfurterResponse.self, from: data)
        return FxRates(
            usd: decoded.rates["USD"] ?? 0.01190,
            eur: decoded.rates["EUR"] ?? 0.01099,
            gbp: decoded.rates["GBP"] ?? 0.00936
        )
    }
}

// ── Frankfurter response shape ────────────────────────────────────────────────

private struct FrankfurterResponse: Decodable {
    let rates: [String: Double]
}
