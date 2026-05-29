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

    /// Submits a document image to the AI platform for async processing via multipart/form-data.
    /// The server returns 202 Accepted immediately; the result is delivered via WebSocket / push
    /// notification when the background pipeline finishes (up to ~6 min).
    ///
    /// - Parameters:
    ///   - data: Raw image bytes (JPEG or PNG, max 5 MB).
    ///   - mimeType: MIME type of the image, e.g. `"image/jpeg"`.
    ///   - documentType: One of `NATIONAL_ID`, `PASSPORT`, `DRIVING_LICENSE`, `PAN_CARD`.
    ///   - registeredName: Optional name for cross-matching against the document.
    func processKycDocument(
        data:           Data,
        mimeType:       String,
        documentType:   String,
        registeredName: String?
    ) async throws {
        let extension_ = mimeType.contains("png") ? "png" : "jpg"
        try await api.postMultipart(
            path:     "/api/v1/ai/kyc/process",
            fileData: data,
            mimeType: mimeType,
            fileName: "kyc_document.\(extension_)",
            additionalFields: [
                "documentType":    documentType,
                "registeredName":  registeredName,
            ]
        )
    }

    /// Registers the device push token with the backend for targeted notifications.
    func registerPushToken(userId: String, token: String, platform: String) async throws {
        struct Body: Encodable { let token: String; let platform: String }
        let _: EmptyResponse = try await api.post(
            path: "/api/v1/users/\(userId)/push-token",
            body: Body(token: token, platform: platform)
        )
    }

    /// Persists a Firebase-OTP-verified phone number to the AegisPay backend.
    /// Pass `nil` to remove an existing phone number.
    /// Called immediately after `Auth.auth().signIn(with: credential)` succeeds.
    func updatePhone(userId: String, phone: String?) async throws -> UserProfile {
        struct Body: Encodable { let phone: String? }
        return try await api.patch(
            path: "/api/v1/users/\(userId)/phone",
            body: Body(phone: phone)
        )
    }
}

/// Placeholder for PATCH endpoints that return 204 No Content.
private struct EmptyResponse: Decodable {}
