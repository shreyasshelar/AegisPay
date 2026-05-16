import XCTest
@testable import AegisPay

// MARK: - Mock UserService

final class MockUserService: UserServiceProtocol {
    // Stub results for register()
    var registerResult: Result<UserProfile, Error> = .failure(TestError.notConfigured)
    var registerCallCount = 0
    var lastRegisterFirstName: String?
    var lastRegisterLastName:  String?
    var lastRegisterEmail:     String?
    var lastIdempotencyKey:    String?

    func register(
        firstName:      String,
        lastName:       String,
        email:          String,
        idempotencyKey: String
    ) async throws -> UserProfile {
        registerCallCount += 1
        lastRegisterFirstName = firstName
        lastRegisterLastName  = lastName
        lastRegisterEmail     = email
        lastIdempotencyKey    = idempotencyKey
        return try registerResult.get()
    }

    // Stub results for getMe()
    var getMeResult: UserProfile? = nil
    func getMe() async throws -> UserProfile? { getMeResult }

    // Stub results for getProfile()
    func getProfile(userId: String) async throws -> UserProfile {
        fatalError("not implemented in mock")
    }

    // Other unused stubs
    func processKycDocument(_ request: KycDocumentRequest) async throws -> KycProcessingResult {
        fatalError("not implemented in mock")
    }
    func registerPushToken(userId: String, token: String, platform: String) async throws {}
    func confirmKyc(userId: String, documentType: String) async throws {}
}

enum TestError: Error {
    case notConfigured
    case network
    case conflict
}

// MARK: - Tests

@MainActor
final class OnboardingViewModelTests: XCTestCase {

    var sut: OnboardingViewModel!
    var mockUserService: MockUserService!

    override func setUp() async throws {
        mockUserService = MockUserService()
        sut = OnboardingViewModel(userService: mockUserService)
    }

    override func tearDown() async throws {
        sut = nil
        mockUserService = nil
    }

    // MARK: Email validation

    func test_emailError_nil_for_valid_email() {
        sut.email = "alice@example.com"
        XCTAssertNil(sut.emailError)
    }

    func test_emailError_set_for_malformed_email() {
        sut.email = "not-an-email"
        XCTAssertNotNil(sut.emailError)
    }

    func test_emailError_nil_when_empty_field() {
        sut.email = ""
        XCTAssertNil(sut.emailError, "Empty email should not show an error (not yet touched)")
    }

    func test_emailError_set_for_missing_tld() {
        sut.email = "alice@example"
        XCTAssertNotNil(sut.emailError)
    }

    func test_emailError_set_for_email_with_spaces() {
        sut.email = "ali ce@example.com"
        XCTAssertNotNil(sut.emailError)
    }

    // MARK: Name validation

    func test_firstNameError_nil_when_empty() {
        sut.firstName = ""
        XCTAssertNil(sut.firstNameError)
    }

    func test_firstNameError_set_for_single_character() {
        sut.firstName = "A"
        XCTAssertNotNil(sut.firstNameError)
    }

    func test_firstNameError_nil_for_two_characters() {
        sut.firstName = "Al"
        XCTAssertNil(sut.firstNameError)
    }

    func test_lastNameError_nil_for_valid_name() {
        sut.lastName = "Smith"
        XCTAssertNil(sut.lastNameError)
    }

    func test_lastNameError_set_for_single_character() {
        sut.lastName = "S"
        XCTAssertNotNil(sut.lastNameError)
    }

    // MARK: isValid guard

    func test_isValid_false_when_fields_empty() {
        XCTAssertFalse(sut.isValid)
    }

    func test_isValid_false_when_email_missing() {
        sut.firstName = "Alice"
        sut.lastName  = "Smith"
        XCTAssertFalse(sut.isValid)
    }

    func test_isValid_false_when_email_invalid() {
        sut.firstName = "Alice"
        sut.lastName  = "Smith"
        sut.email     = "bad-email"
        XCTAssertFalse(sut.isValid)
    }

    func test_isValid_true_when_all_fields_correct() {
        sut.firstName = "Alice"
        sut.lastName  = "Smith"
        sut.email     = "alice@example.com"
        XCTAssertTrue(sut.isValid)
    }

    // MARK: Successful registration

    func test_register_calls_userService_with_correct_fields() async {
        mockUserService.registerResult = .success(fakeProfile(id: "uid-001"))
        sut.firstName = "Alice"
        sut.lastName  = "Smith"
        sut.email     = "alice@example.com"

        _ = await sut.register()

        XCTAssertEqual(mockUserService.lastRegisterFirstName, "Alice")
        XCTAssertEqual(mockUserService.lastRegisterLastName,  "Smith")
        XCTAssertEqual(mockUserService.lastRegisterEmail,     "alice@example.com")
    }

    func test_register_trims_whitespace_from_names() async {
        mockUserService.registerResult = .success(fakeProfile(id: "uid-trim"))
        sut.firstName = "  Alice  "
        sut.lastName  = "  Smith  "
        sut.email     = "alice@example.com"

        _ = await sut.register()

        XCTAssertEqual(mockUserService.lastRegisterFirstName, "Alice")
        XCTAssertEqual(mockUserService.lastRegisterLastName,  "Smith")
    }

    func test_register_returns_userId_on_success() async {
        mockUserService.registerResult = .success(fakeProfile(id: "returned-uuid"))
        sut.firstName = "Alice"
        sut.lastName  = "Smith"
        sut.email     = "alice@example.com"

        let userId = await sut.register()

        XCTAssertEqual(userId, "returned-uuid")
    }

    func test_register_returns_nil_when_isValid_false() async {
        // Email not set → isValid = false
        sut.firstName = "Alice"
        sut.lastName  = "Smith"

        let userId = await sut.register()

        XCTAssertNil(userId)
        XCTAssertEqual(mockUserService.registerCallCount, 0)
    }

    // MARK: Error handling

    func test_register_sets_errorMessage_on_network_failure() async {
        mockUserService.registerResult = .failure(TestError.network)
        sut.firstName = "Alice"
        sut.lastName  = "Smith"
        sut.email     = "alice@example.com"

        let userId = await sut.register()

        XCTAssertNil(userId)
        XCTAssertNotNil(sut.errorMessage)
        XCTAssertFalse(sut.isSubmitting)
    }

    func test_register_clears_errorMessage_on_retry_success() async {
        // First attempt fails
        mockUserService.registerResult = .failure(TestError.network)
        sut.firstName = "Alice"
        sut.lastName  = "Smith"
        sut.email     = "alice@example.com"
        _ = await sut.register()
        XCTAssertNotNil(sut.errorMessage)

        // Second attempt succeeds
        mockUserService.registerResult = .success(fakeProfile(id: "uid-retry"))
        _ = await sut.register()

        XCTAssertNil(sut.errorMessage)
    }

    // MARK: Idempotency key

    func test_same_idempotency_key_reused_across_retries() async {
        // First call fails
        mockUserService.registerResult = .failure(TestError.network)
        sut.firstName = "Alice"
        sut.lastName  = "Smith"
        sut.email     = "alice@example.com"
        _ = await sut.register()
        let key1 = mockUserService.lastIdempotencyKey

        // Retry succeeds
        mockUserService.registerResult = .success(fakeProfile(id: "uid-key"))
        _ = await sut.register()
        let key2 = mockUserService.lastIdempotencyKey

        XCTAssertEqual(key1, key2, "Idempotency key must be stable across retries")
        XCTAssertNotNil(key1, "Idempotency key must not be nil")
    }

    // MARK: isSubmitting state

    func test_isSubmitting_is_false_after_successful_register() async {
        mockUserService.registerResult = .success(fakeProfile(id: "uid-sub"))
        sut.firstName = "Alice"
        sut.lastName  = "Smith"
        sut.email     = "alice@example.com"

        _ = await sut.register()

        XCTAssertFalse(sut.isSubmitting)
    }

    func test_isSubmitting_is_false_after_failed_register() async {
        mockUserService.registerResult = .failure(TestError.network)
        sut.firstName = "Alice"
        sut.lastName  = "Smith"
        sut.email     = "alice@example.com"

        _ = await sut.register()

        XCTAssertFalse(sut.isSubmitting)
    }

    // MARK: Helpers

    private func fakeProfile(id: String) -> UserProfile {
        UserProfile(
            id:        id,
            externalId: "ext-id",
            email:     "alice@example.com",
            firstName: "Alice",
            lastName:  "Smith",
            role:      "CUSTOMER",
            kycStatus: "PENDING",
            active:    true
        )
    }
}

// MARK: - Protocol for testability

/// Defines the UserService interface so tests can inject a mock.
/// The real `UserService` should conform to this protocol.
protocol UserServiceProtocol {
    func register(firstName: String, lastName: String, email: String, idempotencyKey: String) async throws -> UserProfile
    func getMe() async throws -> UserProfile?
    func getProfile(userId: String) async throws -> UserProfile
    func processKycDocument(_ request: KycDocumentRequest) async throws -> KycProcessingResult
    func registerPushToken(userId: String, token: String, platform: String) async throws
    func confirmKyc(userId: String, documentType: String) async throws
}
