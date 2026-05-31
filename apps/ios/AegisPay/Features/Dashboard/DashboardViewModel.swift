import SwiftUI
import Combine

@MainActor
final class DashboardViewModel: ObservableObject {

    // ── Published state ────────────────────────────────────────────────────────
    @Published private(set) var account:      Account?
    @Published private(set) var recentTx:     [Transaction] = []
    @Published private(set) var kycStatus:    KycStatus?
    @Published private(set) var isLoading     = false
    @Published private(set) var errorMessage: String?

    // ── Services ───────────────────────────────────────────────────────────────
    private let accountSvc     = AccountService()
    private let transactionSvc = TransactionService()
    private let userSvc        = UserService()

    // ── WebSocket ──────────────────────────────────────────────────────────────
    private var socket: StompWebSocket?

    // ── Public ─────────────────────────────────────────────────────────────────

    func load(userId: String) async {
        isLoading    = true
        errorMessage = nil
        do {
            async let acc     = accountSvc.getAccount(userId: userId)
            async let txs     = transactionSvc.list(page: 0, size: 8)
            async let profile = userSvc.getProfile(userId: userId)
            account   = try await acc
            recentTx  = try await txs.content
            kycStatus = (try? await profile)?.kycStatus
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }

    func connectSocket(userId: String, accessToken: String) {
        socket = StompWebSocket(
            userId:    userId,
            wsBaseURL: AppConfig.wsBaseURL,
            onMessage: { [weak self] notification in
                guard let self else { return }
                // Refresh on any transaction update
                Task { await self.load(userId: userId) }
            }
        )
        socket?.connect(accessToken: accessToken)
    }

    func disconnectSocket() {
        socket?.disconnect()
        socket = nil
    }

    // ── Derived ────────────────────────────────────────────────────────────────

    var completedCount: Int { recentTx.filter { $0.status == .completed }.count }
    var failedCount:    Int { recentTx.filter { $0.status == .failed }.count }
}
