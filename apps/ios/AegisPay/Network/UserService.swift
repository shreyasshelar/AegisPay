import Foundation

struct UserRegistrationRequest: Encodable {
    let firstName: String
    let lastName:  String
    let email:     String
}

@MainActor
final class UserService {

    private let api: ApiClient

    init(api: ApiClient = .shared) { self.api = api }

    func getProfile(userId: String) async throws -> UserProfile {
        try await api.get(path: "/api/v1/users/\(userId)")
    }

    /// Resolves the AegisPay profile for the authenticated user via JWT subject.
    /// Returns `nil` (404) when the user has not yet registered — callers should
    /// show the onboarding registration form instead of treating this as an error.
    func getMe() async throws -> UserProfile? {
        do {
            let profile: UserProfile = try await api.get(path: "/api/v1/users/me")
            return profile
        } catch let err as ApiError where err.statusCode == 404 {
            return nil
        }
    }

    /// Creates a new AegisPay account for first-time Keycloak users.
    /// Returns the created `UserProfile` (including the new `id`).
    func register(firstName: String, lastName: String, email: String, idempotencyKey: String) async throws -> UserProfile {
        try await api.post(
            path: "/api/v1/users/register",
            body: UserRegistrationRequest(firstName: firstName, lastName: lastName, email: email),
            idempotencyKey: idempotencyKey
        )
    }

    /// Submits a document image to the AI platform for async processing.
    /// The server returns 202 Accepted immediately; the result is delivered via WebSocket
    /// push notification when the background pipeline finishes (up to ~6 min).
    func processKycDocument(_ request: KycDocumentRequest) async throws {
        let _: EmptyResponse = try await api.post(path: "/api/v1/ai/kyc/process", body: request)
    }

    /// Registers the device push token with the backend for targeted notifications.
    func registerPushToken(userId: String, token: String, platform: String) async throws {
        struct Body: Encodable { let token: String; let platform: String }
        let _: EmptyResponse = try await api.post(
            path: "/api/v1/users/\(userId)/push-token",
            body: Body(token: token, platform: platform)
        )
    }
}

/// Placeholder for PATCH endpoints that return 204 No Content.
private struct EmptyResponse: Decodable {}
