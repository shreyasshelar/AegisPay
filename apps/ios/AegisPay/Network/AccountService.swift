import Foundation

final class AccountService {

    private let api: ApiClient

    init(api: ApiClient = .shared) { self.api = api }

    func getAccount(userId: String) async throws -> Account {
        try await api.get(path: "/api/v1/ledger/accounts/\(userId)")
    }
}
