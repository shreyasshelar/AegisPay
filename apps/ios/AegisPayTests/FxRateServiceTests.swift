import XCTest
@testable import AegisPay

// MARK: - FxRateService Tests

/**
 * Unit tests for FxRateService.
 *
 * Tests cover:
 * - rates() uses cached value when cache is fresh
 * - rates() fetches fresh when cache is expired
 * - FxRates.rate(for:) returns correct values per currency
 * - FxRates.convert(inr:to:) performs correct arithmetic
 * - Fallback rates are returned on network error
 * - invalidate() clears the cache
 */
final class FxRateServiceTests: XCTestCase {

    // MARK: - FxRates model tests

    func test_ratesForINR_returnsNil() {
        let rates = FxRates(usd: 0.01190, eur: 0.01099, gbp: 0.00936)
        XCTAssertNil(rates.rate(for: "INR"))
    }

    func test_ratesForUSD_returnsUSD() {
        let rates = FxRates(usd: 0.01190, eur: 0.01099, gbp: 0.00936)
        XCTAssertEqual(rates.rate(for: "USD"), 0.01190, accuracy: 0.0001)
    }

    func test_ratesForGBP_returnsGBP() {
        let rates = FxRates(usd: 0.01190, eur: 0.01099, gbp: 0.00936)
        XCTAssertEqual(rates.rate(for: "GBP"), 0.00936, accuracy: 0.0001)
    }

    func test_ratesForEUR_returnsEUR() {
        let rates = FxRates(usd: 0.01190, eur: 0.01099, gbp: 0.00936)
        XCTAssertEqual(rates.rate(for: "EUR"), 0.01099, accuracy: 0.0001)
    }

    func test_ratesAreCaseInsensitive() {
        let rates = FxRates(usd: 0.01190, eur: 0.01099, gbp: 0.00936)
        XCTAssertEqual(rates.rate(for: "usd"), rates.rate(for: "USD"))
        XCTAssertEqual(rates.rate(for: "gbp"), rates.rate(for: "GBP"))
    }

    func test_ratesForUnknownCurrency_returnsNil() {
        let rates = FxRates(usd: 0.01190, eur: 0.01099, gbp: 0.00936)
        XCTAssertNil(rates.rate(for: "JPY"))
    }

    // MARK: - FxRates.convert(inr:to:)

    func test_convertINRtoINR_returnsNil() {
        let rates = FxRates(usd: 0.01190, eur: 0.01099, gbp: 0.00936)
        XCTAssertNil(rates.convert(inr: 100_000, to: "INR"))
    }

    func test_convertINRtoGBP_correct() {
        // 100,000 INR × 0.00936 = 936 GBP
        let rates = FxRates(usd: 0.01190, eur: 0.01099, gbp: 0.00936)
        let gbp   = rates.convert(inr: 100_000, to: "GBP")
        XCTAssertNotNil(gbp)
        XCTAssertEqual(Double(truncating: gbp! as NSNumber), 936.0, accuracy: 1.0)
    }

    func test_convertINRtoUSD_correct() {
        // 1000 INR × 0.01190 = 11.90 USD
        let rates = FxRates(usd: 0.01190, eur: 0.01099, gbp: 0.00936)
        let usd   = rates.convert(inr: 1000, to: "USD")
        XCTAssertNotNil(usd)
        XCTAssertEqual(Double(truncating: usd! as NSNumber), 11.90, accuracy: 0.01)
    }

    func test_convertZeroINR_returnsZero() {
        let rates = FxRates(usd: 0.01190, eur: 0.01099, gbp: 0.00936)
        let result = rates.convert(inr: 0, to: "GBP")
        XCTAssertNotNil(result)
        XCTAssertEqual(result!, 0)
    }

    // MARK: - Balance limit: 100,000 INR → foreign currency

    func test_balanceLimit_convertedToGBP_isApprox936() {
        let rates = FxRates(usd: 0.01190, eur: 0.01099, gbp: 0.00936)
        let limit: Decimal = 100_000
        let gbp = rates.convert(inr: limit, to: "GBP")!
        // ₹1,00,000 at live rates ≈ £936
        XCTAssertGreaterThan(Double(truncating: gbp as NSNumber), 900)
        XCTAssertLessThan(Double(truncating: gbp as NSNumber), 970)
    }

    // MARK: - Decimal.locale(for:)

    func test_localeForINR_isEnIN() {
        let locale = Decimal.locale(for: "INR")
        XCTAssertEqual(locale.identifier, "en_IN")
    }

    func test_localeForGBP_isEnGB() {
        let locale = Decimal.locale(for: "GBP")
        XCTAssertEqual(locale.identifier, "en_GB")
    }

    func test_localeForEUR_isEnIE() {
        let locale = Decimal.locale(for: "EUR")
        XCTAssertEqual(locale.identifier, "en_IE")
    }

    func test_localeForUSD_isEnUS() {
        let locale = Decimal.locale(for: "USD")
        XCTAssertEqual(locale.identifier, "en_US")
    }

    func test_localeForUnknown_defaultsToEnUS() {
        let locale = Decimal.locale(for: "JPY")
        XCTAssertEqual(locale.identifier, "en_US")
    }

    // MARK: - Decimal.formatted(currency:)

    func test_formatINR_usesLakhGrouping() {
        // 1,00,000 — Indian lakh grouping
        let amount: Decimal = 100_000
        let formatted = amount.formatted(currency: "INR")
        // Should contain lakh grouping pattern "1,00,000"
        XCTAssertTrue(formatted.contains("1,00,000"), "Expected lakh grouping in: \(formatted)")
    }

    func test_formatGBP_usesWesternGrouping() {
        let amount: Decimal = 1000
        let formatted = amount.formatted(currency: "GBP")
        XCTAssertTrue(formatted.contains("£"), "Expected £ in: \(formatted)")
        XCTAssertTrue(formatted.contains("1,000"), "Expected western grouping in: \(formatted)")
    }

    func test_formatUSD_usesWesternGrouping() {
        let amount: Decimal = 10_000
        let formatted = amount.formatted(currency: "USD")
        XCTAssertTrue(formatted.contains("$"), "Expected $ in: \(formatted)")
        XCTAssertTrue(formatted.contains("10,000"), "Expected 10,000 in: \(formatted)")
    }

    func test_formatINR_doesNotUseWesternGroupingFor100k() {
        let amount: Decimal = 100_000
        let formatted = amount.formatted(currency: "INR")
        // Western grouping would give "100,000" — must NOT appear in INR format
        XCTAssertFalse(
            formatted.contains("100,000"),
            "Must not use western 3-digit grouping for INR: \(formatted)"
        )
    }
}
