import Foundation

/// Centralized AI platform API calls.
@MainActor
final class AiService {

    private let api: ApiClient

    init(api: ApiClient = .shared) { self.api = api }

    // ── Error resolution ──────────────────────────────────────────────────────

    func resolveError(_ request: ErrorResolutionRequest) async throws -> ErrorResolutionResponse {
        try await api.post(path: "/api/v1/ai/errors/resolve", body: request)
    }

    // ── Fraud explanation ─────────────────────────────────────────────────────

    func explainFraud(_ request: FraudExplainRequest) async throws -> FraudExplainResponse {
        try await api.post(path: "/api/v1/ai/fraud/explain", body: request)
    }

    // ── Incident triage ───────────────────────────────────────────────────────

    func triageIncident(serviceName: String, incidentDescription: String) async throws -> String {
        struct Req: Encodable { let serviceName, incidentDescription: String }
        struct Res: Decodable { let analysis: String }
        let result: Res = try await api.post(
            path: "/api/v1/ai/incidents/triage",
            body: Req(serviceName: serviceName, incidentDescription: incidentDescription)
        )
        return result.analysis
    }

    // ── KYC document processing ───────────────────────────────────────────────

    func processKycDocument(_ request: KycDocumentRequest) async throws -> KycProcessingResult {
        try await api.post(path: "/api/v1/ai/kyc/process", body: request)
    }
}
