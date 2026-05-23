import Foundation

/// Calls the risk-engine API on behalf of back-office users.
@MainActor
final class RiskService {

    private let api: ApiClient

    init(api: ApiClient = .shared) { self.api = api }

    // ── Risk cases ────────────────────────────────────────────────────────────

    /// GET /api/v1/risk/cases — returns paged content.
    func listRiskCases(page: Int = 0, size: Int = 50) async throws -> [RiskCase] {
        struct Paged: Decodable { let content: [RiskCase] }
        let result: Paged = try await api.get(
            path:   "/api/v1/risk/cases",
            params: ["page": "\(page)", "size": "\(size)"]
        )
        return result.content
    }
}
