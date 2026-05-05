import Foundation

final class UserService {

    private let api: ApiClient

    init(api: ApiClient = .shared) { self.api = api }

    func getProfile(userId: String) async throws -> UserProfile {
        try await api.get(path: "/api/v1/users/\(userId)")
    }

    /// Submits a document image to the AI platform for OCR + quality + tamper analysis.
    /// Returns `KycProcessingResult` (quality score, extracted fields, tampering detection).
    func processKycDocument(_ request: KycDocumentRequest) async throws -> KycProcessingResult {
        try await api.post(path: "/api/v1/ai/kyc/process", body: request)
    }

    /// Registers the device push token with the backend for targeted notifications.
    func registerPushToken(userId: String, token: String, platform: String) async throws {
        struct Body: Encodable { let token: String; let platform: String }
        let _: EmptyResponse = try await api.post(
            path: "/api/v1/users/\(userId)/push-token",
            body: Body(token: token, platform: platform)
        )
    }

    /// Confirms the AI-extracted KYC data and transitions user status to DOCUMENT_SUBMITTED.
    func confirmKyc(userId: String, documentType: String) async throws {
        struct Body: Encodable { let documentType: String }
        let _: EmptyResponse = try await api.patch(
            path: "/api/v1/users/\(userId)/kyc",
            body: Body(documentType: documentType)
        )
    }
}

/// Placeholder for PATCH endpoints that return 204 No Content.
private struct EmptyResponse: Decodable {}
