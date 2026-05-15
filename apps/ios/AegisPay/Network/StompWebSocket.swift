import Foundation

/// Raw STOMP-over-WebSocket client (no external library).
/// Connects, authenticates with Bearer JWT, subscribes to the user's
/// personal notification queue, and delivers parsed payloads via `onMessage`.
final class StompWebSocket: NSObject {

    typealias MessageHandler = (TransactionWebSocketNotification) -> Void

    private var socket:  URLSessionWebSocketTask?
    private var session: URLSession?
    private var reconnectTask: Task<Void, Never>?

    private let userId:    String
    private let wsBaseURL: String
    private let onMessage: MessageHandler

    private var isStopped = false

    /// Tracks consecutive failures for exponential back-off.
    /// Reset to 0 on a successful CONNECTED frame.
    private var reconnectAttempts = 0
    private let maxReconnectDelay: Double = 60   // cap at 60 s

    init(userId: String, wsBaseURL: String, onMessage: @escaping MessageHandler) {
        self.userId    = userId
        self.wsBaseURL = wsBaseURL
        self.onMessage = onMessage
        super.init()
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    func connect(accessToken: String) {
        isStopped = false
        openSocket(accessToken: accessToken)
    }

    func disconnect() {
        isStopped        = true
        reconnectAttempts = 0
        reconnectTask?.cancel()
        socket?.cancel(with: .goingAway, reason: nil)
        socket  = nil
        session = nil
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private func openSocket(accessToken: String) {
        let urlString = "\(wsBaseURL)/ws/websocket"
        guard let url = URL(string: urlString) else { return }

        session = URLSession(configuration: .default, delegate: nil, delegateQueue: nil)
        socket  = session?.webSocketTask(with: url)
        socket?.resume()

        sendStompConnect(accessToken: accessToken)
        receiveLoop(accessToken: accessToken)
    }

    private func sendStompConnect(accessToken: String) {
        let frame = stompFrame(
            command: "CONNECT",
            headers: [
                "accept-version": "1.2",
                "heart-beat":     "10000,10000",
                "Authorization":  "Bearer \(accessToken)",
            ],
            body: ""
        )
        socket?.send(.string(frame)) { _ in }
    }

    private func sendSubscribe() {
        let frame = stompFrame(
            command: "SUBSCRIBE",
            headers: [
                "id":          "sub-0",
                "destination": "/user/\(userId)/queue/notifications",
            ],
            body: ""
        )
        socket?.send(.string(frame)) { _ in }
    }

    private func receiveLoop(accessToken: String) {
        socket?.receive { [weak self] result in
            guard let self, !isStopped else { return }
            switch result {
            case .success(let message):
                switch message {
                case .string(let text):
                    self.handleFrame(text)
                default:
                    break
                }
                self.receiveLoop(accessToken: accessToken)

            case .failure:
                guard !self.isStopped else { return }
                // Exponential back-off: 5 s → 10 s → 20 s → 40 s → cap 60 s
                let delay = min(5.0 * pow(2.0, Double(self.reconnectAttempts)), self.maxReconnectDelay)
                self.reconnectAttempts = min(self.reconnectAttempts + 1, 10)
                self.reconnectTask = Task {
                    try? await Task.sleep(for: .seconds(delay))
                    guard !self.isStopped else { return }
                    self.openSocket(accessToken: accessToken)
                }
            }
        }
    }

    private func handleFrame(_ raw: String) {
        if raw.hasPrefix("CONNECTED") {
            reconnectAttempts = 0   // successful handshake — reset back-off counter
            sendSubscribe()
            return
        }
        guard raw.hasPrefix("MESSAGE"),
              let bodyRange = raw.range(of: "\n\n") else { return }

        var body = String(raw[bodyRange.upperBound...])
        if body.hasSuffix("\0") { body.removeLast() }

        guard let data = body.data(using: .utf8),
              let notification = try? JSONDecoder().decode(
                  TransactionWebSocketNotification.self, from: data
              )
        else { return }

        DispatchQueue.main.async { [weak self] in
            self?.onMessage(notification)
        }
    }

    // ── STOMP frame builder ───────────────────────────────────────────────────

    private func stompFrame(
        command: String,
        headers: [String: String],
        body:    String
    ) -> String {
        let headerStr = headers.map { "\($0.key):\($0.value)" }.joined(separator: "\n")
        return "\(command)\n\(headerStr)\n\n\(body)\0"
    }
}

// ── WebSocket notification model ──────────────────────────────────────────────

struct TransactionWebSocketNotification: Decodable {
    let type:          String
    let title:         String
    let body:          String
    let transactionId: String?
}
