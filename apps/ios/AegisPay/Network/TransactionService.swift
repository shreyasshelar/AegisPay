import Foundation

/// All transaction-related API calls.
final class TransactionService {

    private let api: ApiClient

    init(api: ApiClient = .shared) { self.api = api }

    func list(
        page:     Int     = 0,
        size:     Int     = 20,
        status:   String? = nil,
        fromDate: String? = nil,
        toDate:   String? = nil
    ) async throws -> PagedTransactions {
        var params: [String: String] = [
            "page": "\(page)",
            "size": "\(size)",
        ]
        if let status   { params["status"]   = status }
        if let fromDate { params["fromDate"] = fromDate }
        if let toDate   { params["toDate"]   = toDate }

        return try await api.get(path: "/api/v1/transactions", params: params)
    }

    func get(id: String) async throws -> Transaction {
        try await api.get(path: "/api/v1/transactions/\(id)")
    }

    func create(
        _ request:      CreateTransactionRequest,
        idempotencyKey: String
    ) async throws -> Transaction {
        try await api.post(
            path:           "/api/v1/transactions",
            body:           request,
            idempotencyKey: idempotencyKey
        )
    }
}
