import Foundation

@MainActor
final class NotificationService {

    private let api: ApiClient

    init(api: ApiClient = .shared) { self.api = api }

    func list(page: Int = 0, size: Int = 30) async throws -> PagedNotifications {
        try await api.get(
            path:   "/api/v1/notifications",
            params: ["page": "\(page)", "size": "\(size)"]
        )
    }
}
