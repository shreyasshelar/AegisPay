import SwiftUI

@MainActor
final class TransactionDetailViewModel: ObservableObject {

    @Published private(set) var transaction:      Transaction?
    @Published private(set) var isLoading         = false
    @Published private(set) var errorMessage:     String?

    // AI error resolution
    @Published private(set) var errorResolution:  ErrorResolutionResponse?
    @Published private(set) var isResolvingError  = false

    private let service   = TransactionService()
    private let aiService = AiService()
    private var socket:    StompWebSocket?
    private var pollTask:  Task<Void, Never>?

    let transactionId: String

    init(transactionId: String) {
        self.transactionId = transactionId
    }

    // ── Load ──────────────────────────────────────────────────────────────────

    func load() async {
        isLoading    = true
        errorMessage = nil
        do {
            let tx = try await service.get(id: transactionId)
            transaction = tx
            // Auto-trigger AI resolution on first load of a failed transaction
            if (tx.status == .failed || tx.status == .rolledBack),
               let reason = tx.failureReason,
               errorResolution == nil,
               !isResolvingError
            {
                await resolveError(reason: reason)
            }
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }

    func resolveError(reason: String) async {
        guard !isResolvingError else { return }
        isResolvingError = true
        do {
            errorResolution = try await aiService.resolveError(
                ErrorResolutionRequest(
                    errorCode:    reason.components(separatedBy: ":").first?.trimmingCharacters(in: .whitespaces) ?? "UNKNOWN",
                    errorMessage: reason
                )
            )
        } catch {
            // Non-fatal — just leave errorResolution nil
        }
        isResolvingError = false
    }

    // ── Live updates ──────────────────────────────────────────────────────────

    func startLiveUpdates(userId: String, accessToken: String) {
        guard !(transaction?.status.isTerminal ?? false) else { return }

        socket = StompWebSocket(
            userId:    userId,
            wsBaseURL: AppConfig.wsBaseURL,
            onMessage: { [weak self] notification in
                guard let self,
                      notification.transactionId == self.transactionId
                else { return }
                Task { await self.load() }
            }
        )
        socket?.connect(accessToken: accessToken)

        // Polling fallback: every 3 s until terminal state
        pollTask = Task {
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(3))
                guard !Task.isCancelled else { break }
                await load()
                if transaction?.status.isTerminal == true { break }
            }
        }
    }

    func stopLiveUpdates() {
        socket?.disconnect()
        socket = nil
        pollTask?.cancel()
        pollTask = nil
    }
}
