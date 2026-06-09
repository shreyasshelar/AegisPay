import XCTest
@testable import AegisPay

// MARK: - WalletViewModel Tests

/**
 * Unit tests for WalletViewModel.
 *
 * Tests cover:
 * - BALANCE_LIMIT_INR constant
 * - inrAvailable, inrRoom computed properties
 * - wouldExceedLimit guard
 * - limitLine / roomLine human-readable strings
 * - FX-converted display with live rates
 */
@MainActor
final class WalletViewModelTests: XCTestCase {

    // MARK: - BALANCE_LIMIT_INR

    func test_balanceLimitINR_is100k() {
        XCTAssertEqual(BALANCE_LIMIT_INR, 100_000)
    }

    // MARK: - inrAvailable

    func test_inrAvailable_noAccounts_returnsNil() {
        let vm = WalletViewModel(walletService: MockWalletService())
        // accounts array is empty by default
        XCTAssertNil(vm.inrAvailable)
    }

    func test_inrAvailable_withINRAccount_returnsBalance() {
        let vm = WalletViewModel(walletService: MockWalletService())
        vm.accounts = [makeAccount(currency: "INR", available: 45_000)]
        XCTAssertEqual(vm.inrAvailable, 45_000)
    }

    func test_inrAvailable_withOnlyGBPAccount_returnsNil() {
        let vm = WalletViewModel(walletService: MockWalletService())
        vm.accounts = [makeAccount(currency: "GBP", available: 500)]
        XCTAssertNil(vm.inrAvailable)
    }

    // MARK: - inrRoom

    func test_inrRoom_noAccounts_isFullLimit() {
        let vm = WalletViewModel(walletService: MockWalletService())
        XCTAssertEqual(vm.inrRoom, BALANCE_LIMIT_INR)
    }

    func test_inrRoom_zeroBalance_isFullLimit() {
        let vm = WalletViewModel(walletService: MockWalletService())
        vm.accounts = [makeAccount(currency: "INR", available: 0)]
        XCTAssertEqual(vm.inrRoom, BALANCE_LIMIT_INR)
    }

    func test_inrRoom_halfBalance_isHalfLimit() {
        let vm = WalletViewModel(walletService: MockWalletService())
        vm.accounts = [makeAccount(currency: "INR", available: 50_000)]
        XCTAssertEqual(vm.inrRoom, 50_000)
    }

    func test_inrRoom_atLimit_isZero() {
        let vm = WalletViewModel(walletService: MockWalletService())
        vm.accounts = [makeAccount(currency: "INR", available: 100_000)]
        XCTAssertEqual(vm.inrRoom, 0)
    }

    func test_inrRoom_neverNegative() {
        let vm = WalletViewModel(walletService: MockWalletService())
        // Hypothetically over-limit (shouldn't happen in practice but guard for safety)
        vm.accounts = [makeAccount(currency: "INR", available: 120_000)]
        XCTAssertGreaterThanOrEqual(vm.inrRoom, 0)
    }

    // MARK: - wouldExceedLimit

    func test_wouldExceedLimit_smallAmount_false() {
        let vm = WalletViewModel(walletService: MockWalletService())
        vm.accounts = [makeAccount(currency: "INR", available: 50_000)]
        XCTAssertFalse(vm.wouldExceedLimit(amount: 1_000))
    }

    func test_wouldExceedLimit_exactlyAtLimit_false() {
        let vm = WalletViewModel(walletService: MockWalletService())
        vm.accounts = [makeAccount(currency: "INR", available: 90_000)]
        // 90,000 + 10,000 = 100,000 (not exceeding)
        XCTAssertFalse(vm.wouldExceedLimit(amount: 10_000))
    }

    func test_wouldExceedLimit_oneRupeeOver_true() {
        let vm = WalletViewModel(walletService: MockWalletService())
        vm.accounts = [makeAccount(currency: "INR", available: 90_000)]
        // 90,000 + 10,001 = 100,001 → exceeds
        XCTAssertTrue(vm.wouldExceedLimit(amount: 10_001))
    }

    func test_wouldExceedLimit_noAccounts_largeAmount_true() {
        let vm = WalletViewModel(walletService: MockWalletService())
        // No INR account, inrAvailable = 0, room = 100,000
        XCTAssertTrue(vm.wouldExceedLimit(amount: 100_001))
    }

    // MARK: - limitLine

    func test_limitLine_INR_containsLimit() {
        let vm = WalletViewModel(walletService: MockWalletService())
        let line = vm.limitLine(currency: "INR")
        XCTAssertTrue(line.contains("Wallet limit"), "Should contain 'Wallet limit': \(line)")
        // INR formatted with lakh grouping: ₹1,00,000
        XCTAssertTrue(line.contains("1,00,000"), "Should contain lakh-formatted amount: \(line)")
    }

    func test_limitLine_GBP_containsLiveLabel() {
        let vm = WalletViewModel(walletService: MockWalletService())
        vm.fxRates = FxRates(usd: 0.01190, eur: 0.01099, gbp: 0.00936)
        let line = vm.limitLine(currency: "GBP")
        XCTAssertTrue(line.contains("live"), "Non-INR limit line should say 'live': \(line)")
        XCTAssertTrue(line.contains("£"), "Should show GBP symbol: \(line)")
    }

    func test_limitLine_INR_doesNotContainLiveLabel() {
        let vm = WalletViewModel(walletService: MockWalletService())
        let line = vm.limitLine(currency: "INR")
        // INR limit line should NOT show "live" (no conversion needed)
        XCTAssertFalse(line.contains("live"), "INR limit should not have live label: \(line)")
    }

    // MARK: - roomLine

    func test_roomLine_fullRoom_showsFullLimit() {
        let vm = WalletViewModel(walletService: MockWalletService())
        // No accounts → room = 100,000
        let line = vm.roomLine(currency: "INR")
        XCTAssertTrue(line.contains("Room"), "Should contain 'Room': \(line)")
        XCTAssertTrue(line.contains("1,00,000"), "Full room should show full limit: \(line)")
    }

    func test_roomLine_partialRoom_showsCorrectAmount() {
        let vm = WalletViewModel(walletService: MockWalletService())
        vm.accounts = [makeAccount(currency: "INR", available: 50_000)]
        let line = vm.roomLine(currency: "INR")
        // Room = 50,000 INR
        XCTAssertTrue(line.contains("50,000"), "Should show 50,000: \(line)")
    }

    // MARK: - Helpers

    private func makeAccount(currency: String, available: Decimal) -> Account {
        Account(
            id:               UUID().uuidString,
            userId:           UUID().uuidString,
            currency:         currency,
            availableBalance: available,
            reservedBalance:  0
        )
    }
}

// MARK: - MockWalletService

private final class MockWalletService: WalletService {
    var accountsToReturn: [Account] = []
    var shouldThrow = false

    override func getMyAccounts() async throws -> [Account] {
        if shouldThrow { throw URLError(.notConnectedToInternet) }
        return accountsToReturn
    }

    override func createTopUpIntent(amount: Decimal, currency: String) async throws -> TopUpIntentResponse {
        throw URLError(.notConnectedToInternet)
    }

    override func confirmTopUp(paymentIntentId: String) async throws {
        if shouldThrow { throw URLError(.notConnectedToInternet) }
    }
}
