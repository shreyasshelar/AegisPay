import XCTest
@testable import AegisPay

// MARK: - SendMoneyViewModel Tests

/**
 * Unit tests for SendMoneyViewModel.
 *
 * Tests cover:
 * - updateRiskWarning() with INR amounts below and above the ₹10,000 threshold
 * - updateRiskWarning() with foreign-currency amounts converted to INR
 * - Warning cleared on reset()
 * - Warning cleared when amount falls below threshold
 * - Amount validation (amountError property)
 */
@MainActor
final class SendMoneyViewModelTests: XCTestCase {

    private var vm: SendMoneyViewModel!
    private let liveRates = FxRates(usd: 0.01190, eur: 0.01099, gbp: 0.00936)

    override func setUp() {
        super.setUp()
        vm = SendMoneyViewModel()
        vm.fxRates = liveRates
    }

    // MARK: - INR risk warning

    func test_riskWarning_belowThreshold_nil() {
        vm.currency   = "INR"
        vm.amountText = "9999"
        vm.updateRiskWarning()
        XCTAssertNil(vm.riskWarning)
    }

    func test_riskWarning_atExactThreshold_shown() {
        vm.currency   = "INR"
        vm.amountText = "10000"
        vm.updateRiskWarning()
        XCTAssertNotNil(vm.riskWarning)
    }

    func test_riskWarning_aboveThreshold_shown() {
        vm.currency   = "INR"
        vm.amountText = "50000"
        vm.updateRiskWarning()
        XCTAssertNotNil(vm.riskWarning)
    }

    func test_riskWarning_forINR_containsThresholdMessage() {
        vm.currency   = "INR"
        vm.amountText = "15000"
        vm.updateRiskWarning()
        let warning = vm.riskWarning!
        XCTAssertTrue(warning.contains("10,000"), "Should mention ₹10,000: \(warning)")
        XCTAssertTrue(warning.contains("risk"), "Should mention risk: \(warning)")
    }

    func test_riskWarning_emptyAmount_clearsWarning() {
        // First trigger a warning
        vm.currency   = "INR"
        vm.amountText = "15000"
        vm.updateRiskWarning()
        XCTAssertNotNil(vm.riskWarning)

        // Then clear the amount
        vm.amountText = ""
        vm.updateRiskWarning()
        XCTAssertNil(vm.riskWarning)
    }

    func test_riskWarning_invalidText_clearsWarning() {
        vm.currency   = "INR"
        vm.amountText = "not-a-number"
        vm.updateRiskWarning()
        XCTAssertNil(vm.riskWarning)
    }

    func test_riskWarning_zeroAmount_clearsWarning() {
        vm.currency   = "INR"
        vm.amountText = "0"
        vm.updateRiskWarning()
        XCTAssertNil(vm.riskWarning)
    }

    // MARK: - Foreign currency risk warning

    func test_riskWarning_GBP_200_exceedsThreshold() {
        // 200 GBP / 0.00936 ≈ 21,367 INR → exceeds ₹10,000
        vm.currency   = "GBP"
        vm.amountText = "200"
        vm.updateRiskWarning()
        XCTAssertNotNil(vm.riskWarning, "200 GBP ≈ ₹21,367 — should trigger warning")
    }

    func test_riskWarning_GBP_10_belowThreshold() {
        // 10 GBP / 0.00936 ≈ 1,068 INR → below ₹10,000
        vm.currency   = "GBP"
        vm.amountText = "10"
        vm.updateRiskWarning()
        XCTAssertNil(vm.riskWarning, "10 GBP ≈ ₹1,068 — should NOT trigger warning")
    }

    func test_riskWarning_USD_120_exceedsThreshold() {
        // 120 USD / 0.01190 ≈ 10,084 INR → exceeds threshold
        vm.currency   = "USD"
        vm.amountText = "120"
        vm.updateRiskWarning()
        XCTAssertNotNil(vm.riskWarning, "120 USD ≈ ₹10,084 — should trigger warning")
    }

    func test_riskWarning_USD_50_belowThreshold() {
        // 50 USD / 0.01190 ≈ 4,202 INR → below ₹10,000
        vm.currency   = "USD"
        vm.amountText = "50"
        vm.updateRiskWarning()
        XCTAssertNil(vm.riskWarning, "50 USD ≈ ₹4,202 — should NOT trigger warning")
    }

    func test_riskWarning_EUR_110_exceedsThreshold() {
        // 110 EUR / 0.01099 ≈ 10,009 INR → exceeds threshold
        vm.currency   = "EUR"
        vm.amountText = "110"
        vm.updateRiskWarning()
        XCTAssertNotNil(vm.riskWarning, "110 EUR ≈ ₹10,009 — should trigger warning")
    }

    // MARK: - reset() clears warning

    func test_reset_clearsRiskWarning() {
        vm.currency   = "INR"
        vm.amountText = "50000"
        vm.updateRiskWarning()
        XCTAssertNotNil(vm.riskWarning)

        vm.reset()
        XCTAssertNil(vm.riskWarning)
    }

    func test_reset_clearsAllFields() {
        vm.payeeId    = "some-uuid"
        vm.amountText = "5000"
        vm.currency   = "GBP"
        vm.note       = "birthday"
        vm.reset()

        XCTAssertEqual(vm.payeeId, "")
        XCTAssertEqual(vm.amountText, "")
        XCTAssertEqual(vm.currency, "INR")
        XCTAssertEqual(vm.note, "")
        XCTAssertNil(vm.riskWarning)
        XCTAssertEqual(vm.step, .payee)
    }

    // MARK: - Amount validation

    func test_amountError_nil_whenEmpty() {
        vm.amountText = ""
        XCTAssertNil(vm.amountError)
    }

    func test_amountError_nil_whenValid() {
        vm.amountText = "1500"
        XCTAssertNil(vm.amountError)
    }

    func test_amountError_nonNil_whenZero() {
        vm.amountText = "0"
        XCTAssertNotNil(vm.amountError)
    }

    func test_amountError_nonNil_whenNegative() {
        vm.amountText = "-100"
        XCTAssertNotNil(vm.amountError)
    }

    func test_amountError_nonNil_whenExceedsMax() {
        vm.amountText = "1000001"
        XCTAssertNotNil(vm.amountError)
    }

    func test_amountError_nonNil_whenNonNumeric() {
        vm.amountText = "abc"
        XCTAssertNotNil(vm.amountError)
    }

    // MARK: - Payee validation

    func test_payeeIdError_nil_whenValidUUID() {
        vm.payeeId = "550e8400-e29b-41d4-a716-446655440000"
        XCTAssertNil(vm.payeeIdError)
    }

    func test_payeeIdError_nil_whenEmpty() {
        vm.payeeId = ""
        XCTAssertNil(vm.payeeIdError)
    }

    func test_payeeIdError_nonNil_whenInvalidFormat() {
        vm.payeeId = "not-a-uuid"
        XCTAssertNotNil(vm.payeeIdError)
    }
}
