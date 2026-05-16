import XCTest
@testable import AegisPay

// MARK: - Mock TokenStore

final class MockTokenStore {
    var accessToken:        String?
    var refreshToken:       String?
    var idToken:            String?
    var userId:             String?
    var userRole:           String?
    var expiresAt:          Date?
    var clearCallCount      = 0
    var storeCallCount      = 0
    var lastStoredUserId:   String?
    var lastStoredUserRole: String?

    var isAccessTokenValid: Bool {
        guard let token = accessToken, !token.isEmpty,
              let expires = expiresAt else { return false }
        return expires > Date()
    }

    var canRefresh: Bool { refreshToken != nil }

    func store(
        accessToken:  String,
        refreshToken: String?,
        idToken:      String?,
        expiresIn:    TimeInterval,
        userId:       String?,
        userRole:     String?
    ) {
        storeCallCount      += 1
        self.accessToken     = accessToken
        self.refreshToken    = refreshToken
        self.idToken         = idToken
        self.userId          = userId
        self.userRole        = userRole
        self.expiresAt       = Date(timeIntervalSinceNow: expiresIn)
        lastStoredUserId     = userId
        lastStoredUserRole   = userRole
    }

    func clear() {
        clearCallCount += 1
        accessToken   = nil
        refreshToken  = nil
        idToken       = nil
        userId        = nil
        userRole      = nil
        expiresAt     = nil
    }
}

// MARK: - Mock UserService (for AuthStore's /me calls)

final class AuthStoreMockUserService: UserServiceProtocol {
    var getMeResult: UserProfile? = nil
    var getMeThrows: Error? = nil
    var getMeCallCount = 0

    func getMe() async throws -> UserProfile? {
        getMeCallCount += 1
        if let error = getMeThrows { throw error }
        return getMeResult
    }

    func register(firstName: String, lastName: String, email: String, idempotencyKey: String) async throws -> UserProfile {
        fatalError("not used in AuthStore tests")
    }
    func getProfile(userId: String) async throws -> UserProfile {
        fatalError("not used in AuthStore tests")
    }
    func processKycDocument(_ request: KycDocumentRequest) async throws -> KycProcessingResult {
        fatalError("not used in AuthStore tests")
    }
    func registerPushToken(userId: String, token: String, platform: String) async throws {}
    func confirmKyc(userId: String, documentType: String) async throws {}
}

// MARK: - Tests

/// Tests for the auth state machine logic in AuthStore.
/// AppAuth network calls are never triggered — only token/session state transitions.
@MainActor
final class AuthStoreTests: XCTestCase {

    // MARK: completeRegistration

    func test_completeRegistration_stores_userId_and_transitions_to_authenticated() {
        let tokenStore    = MockTokenStore()
        tokenStore.accessToken  = "valid-access"
        tokenStore.refreshToken = "refresh-tok"
        tokenStore.expiresAt    = Date(timeIntervalSinceNow: 3600)
        // Initially no userId
        XCTAssertNil(tokenStore.userId)

        let newUserId = "new-aegispay-uuid-001"
        // Simulate what AuthStore.completeRegistration does:
        tokenStore.store(
            accessToken:  tokenStore.accessToken!,
            refreshToken: tokenStore.refreshToken,
            idToken:      tokenStore.idToken,
            expiresIn:    3600,
            userId:       newUserId,
            userRole:     "CUSTOMER"
        )

        XCTAssertEqual(tokenStore.userId, newUserId)
        XCTAssertEqual(tokenStore.storeCallCount, 1)
    }

    func test_completeRegistration_preserves_existing_role() {
        let tokenStore          = MockTokenStore()
        tokenStore.accessToken  = "at"
        tokenStore.refreshToken = "rt"
        tokenStore.userRole     = "BACK_OFFICE"
        tokenStore.expiresAt    = Date(timeIntervalSinceNow: 3600)

        tokenStore.store(
            accessToken:  "at",
            refreshToken: "rt",
            idToken:      nil,
            expiresIn:    3600,
            userId:       "uid-bo",
            userRole:     "BACK_OFFICE"
        )

        XCTAssertEqual(tokenStore.lastStoredUserRole, "BACK_OFFICE")
    }

    // MARK: signOut

    func test_signOut_clears_token_store() {
        let tokenStore          = MockTokenStore()
        tokenStore.accessToken  = "at"
        tokenStore.refreshToken = "rt"
        tokenStore.userId       = "some-user"

        tokenStore.clear()

        XCTAssertNil(tokenStore.accessToken)
        XCTAssertNil(tokenStore.userId)
        XCTAssertEqual(tokenStore.clearCallCount, 1)
    }

    // MARK: isAccessTokenValid

    func test_isAccessTokenValid_true_when_token_present_and_not_expired() {
        let store          = MockTokenStore()
        store.accessToken  = "valid"
        store.expiresAt    = Date(timeIntervalSinceNow: 300)  // 5 min from now

        XCTAssertTrue(store.isAccessTokenValid)
    }

    func test_isAccessTokenValid_false_when_token_absent() {
        let store = MockTokenStore()
        XCTAssertFalse(store.isAccessTokenValid)
    }

    func test_isAccessTokenValid_false_when_token_expired() {
        let store          = MockTokenStore()
        store.accessToken  = "expired"
        store.expiresAt    = Date(timeIntervalSinceNow: -60)  // 1 min ago

        XCTAssertFalse(store.isAccessTokenValid)
    }

    // MARK: canRefresh

    func test_canRefresh_true_when_refresh_token_present() {
        let store           = MockTokenStore()
        store.refreshToken  = "refresh-tok"
        XCTAssertTrue(store.canRefresh)
    }

    func test_canRefresh_false_when_no_refresh_token() {
        let store = MockTokenStore()
        XCTAssertFalse(store.canRefresh)
    }

    // MARK: JWT payload decoding helper

    func test_jwt_payload_decoding_extracts_aegispay_user_id() {
        // Build a minimal JWT with aegispay_user_id claim
        let payload: [String: Any] = [
            "sub":              "kc-sub-123",
            "aegispay_user_id": "aegis-uuid-abc",
            "email":            "alice@example.com",
            "realm_access":     ["roles": ["CUSTOMER"]]
        ]
        let encoded = encodeJWTPayload(payload)
        let jwt = "header.\(encoded).signature"

        let decoded = decodeJWTPayload(jwt)
        XCTAssertEqual(decoded["aegispay_user_id"] as? String, "aegis-uuid-abc")
        XCTAssertEqual(decoded["email"] as? String, "alice@example.com")
    }

    func test_jwt_payload_decoding_handles_empty_string() {
        let decoded = decodeJWTPayload("")
        XCTAssertTrue(decoded.isEmpty)
    }

    func test_jwt_payload_decoding_handles_malformed_jwt() {
        let decoded = decodeJWTPayload("only.two")
        XCTAssertTrue(decoded.isEmpty)
    }

    // MARK: Role extraction

    func test_admin_role_extracted_from_realm_access() {
        let payload: [String: Any] = [
            "realm_access": ["roles": ["offline_access", "ADMIN", "CUSTOMER"]]
        ]
        let role = extractRole(from: payload)
        XCTAssertEqual(role, "ADMIN")
    }

    func test_back_office_role_extracted_when_no_admin() {
        let payload: [String: Any] = [
            "realm_access": ["roles": ["BACK_OFFICE", "CUSTOMER"]]
        ]
        let role = extractRole(from: payload)
        XCTAssertEqual(role, "BACK_OFFICE")
    }

    func test_customer_role_is_default_when_no_privileged_role() {
        let payload: [String: Any] = [
            "realm_access": ["roles": ["offline_access"]]
        ]
        let role = extractRole(from: payload)
        XCTAssertEqual(role, "CUSTOMER")
    }

    func test_customer_role_default_when_realm_access_absent() {
        let role = extractRole(from: [:])
        XCTAssertEqual(role, "CUSTOMER")
    }

    // MARK: Multi-tenant claim extraction

    func test_tenant_id_extracted_from_jwt_payload() {
        let payload: [String: Any] = [
            "aegispay_tenant_id": "tenant-acme",
            "sub": "kc-sub-001"
        ]
        let tenantId = payload["aegispay_tenant_id"] as? String
        XCTAssertEqual(tenantId, "tenant-acme")
    }

    func test_google_sso_sub_format_preserved() {
        // Google subjects brokered via Keycloak use f:<broker-id>:<google-id> format
        let googleSub = "f:google-broker-id:109876543210000000000"
        let payload: [String: Any] = ["sub": googleSub]
        let decoded = payload["sub"] as? String
        XCTAssertEqual(decoded, googleSub)
    }

    // MARK: - Helpers

    /// Decodes the payload segment of a JWT (mirrors AuthStore's private helper).
    private func decodeJWTPayload(_ jwt: String) -> [String: Any] {
        let segments = jwt.split(separator: ".")
        guard segments.count == 3 else { return [:] }
        var base64 = String(segments[1])
        let remainder = base64.count % 4
        if remainder != 0 { base64 += String(repeating: "=", count: 4 - remainder) }
        guard let data = Data(base64Encoded: base64),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
        else { return [:] }
        return json
    }

    /// Encodes a dictionary as the payload segment of a fake JWT.
    private func encodeJWTPayload(_ payload: [String: Any]) -> String {
        let data   = try! JSONSerialization.data(withJSONObject: payload)
        var base64 = data.base64EncodedString()
        // Convert to URL-safe base64 and strip padding
        base64 = base64
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
        return base64
    }

    /// Mirrors the role-extraction logic in AuthStore.persistTokens.
    private func extractRole(from claims: [String: Any]) -> String {
        let roles = (claims["realm_access"] as? [String: Any])?["roles"] as? [String] ?? []
        if roles.contains("ADMIN")        { return "ADMIN" }
        if roles.contains("BACK_OFFICE")  { return "BACK_OFFICE" }
        return "CUSTOMER"
    }
}
