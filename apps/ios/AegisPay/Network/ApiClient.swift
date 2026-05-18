import Foundation

// ── API Error ─────────────────────────────────────────────────────────────────

struct ApiError: LocalizedError {
    let statusCode: Int
    let message:    String
    let errorCode:  String?

    var errorDescription: String? { message }

    static let unknown = ApiError(statusCode: 0, message: "Unknown error", errorCode: nil)
}

// ── Response envelope ─────────────────────────────────────────────────────────

/// Matches the `ApiResponse<T>` envelope returned by every AegisPay backend endpoint:
/// `{ "success": true, "data": <T>, "timestamp": "..." }`
private struct ApiEnvelope<T: Decodable>: Decodable {
    let success: Bool
    let data:    T?
    let error:   ErrorBody?
}

private struct ErrorBody: Decodable {
    let message:   String?
    let errorCode: String?
    let code:      String?   // some error shapes use "code" instead of "errorCode"

    var resolvedCode: String? { errorCode ?? code }
}

// ── ApiClient ─────────────────────────────────────────────────────────────────

/// Centralised URLSession-based HTTP client.
/// Automatically injects Bearer token, X-Correlation-ID, and handles 401.
@MainActor
final class ApiClient: ObservableObject {

    static let shared = ApiClient()

    private let baseURL:   URL
    private let session:   URLSession
    private let decoder:   JSONDecoder
    private var authStore: AuthStore?

    private init() {
        baseURL = AppConfig.apiBaseURL

        let config                    = URLSessionConfiguration.default
        config.timeoutIntervalForRequest  = 30
        config.timeoutIntervalForResource = 60
        session = URLSession(
            configuration: config,
            delegate: CertificatePinningDelegate(),
            delegateQueue: nil
        )

        decoder = JSONDecoder()
        decoder.keyDecodingStrategy   = .convertFromSnakeCase
        decoder.dateDecodingStrategy  = .iso8601
    }

    /// Must be called once `AuthStore` is available (e.g. from the App entry point).
    func configure(authStore: AuthStore) {
        self.authStore = authStore
    }

    // ── Public request methods ─────────────────────────────────────────────────

    func get<T: Decodable>(
        path:   String,
        params: [String: String] = [:]
    ) async throws -> T {
        try await request(method: "GET", path: path, params: params, body: nil as EmptyBody?)
    }

    func post<B: Encodable, T: Decodable>(
        path: String,
        body: B,
        idempotencyKey: String? = nil
    ) async throws -> T {
        try await request(method: "POST", path: path, params: [:], body: body, idempotencyKey: idempotencyKey)
    }

    func patch<B: Encodable, T: Decodable>(
        path: String,
        body: B
    ) async throws -> T {
        try await request(method: "PATCH", path: path, params: [:], body: body)
    }

    // ── Core request ──────────────────────────────────────────────────────────

    private func request<B: Encodable, T: Decodable>(
        method:         String,
        path:           String,
        params:         [String: String] = [:],
        body:           B?,
        idempotencyKey: String? = nil
    ) async throws -> T {

        // Build URL
        guard var components = URLComponents(url: baseURL.appendingPathComponent(path), resolvingAgainstBaseURL: false) else {
            throw ApiError(statusCode: 0, message: "Invalid URL: \(path)", errorCode: "INVALID_URL")
        }
        if !params.isEmpty {
            components.queryItems = params.map { URLQueryItem(name: $0.key, value: $0.value) }
        }
        guard let resolvedURL = components.url else {
            throw ApiError(statusCode: 0, message: "Could not resolve URL for path: \(path)", errorCode: "INVALID_URL")
        }
        var request = URLRequest(url: resolvedURL)
        request.httpMethod = method

        // Headers
        let token = try await authStore?.validAccessToken()
        if let token { request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(UUID().uuidString,   forHTTPHeaderField: "X-Correlation-ID")
        if let key = idempotencyKey {
            request.setValue(key, forHTTPHeaderField: "X-Idempotency-Key")
        }

        // Body
        if let body, method != "GET" {
            let encoder = JSONEncoder()
            encoder.keyEncodingStrategy = .convertToSnakeCase
            request.httpBody = try encoder.encode(body)
        }

        // Execute
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw ApiError.unknown }

        // 401 → sign out
        if http.statusCode == 401 {
            await authStore?.signOut()
            throw ApiError(statusCode: 401, message: "Session expired", errorCode: "UNAUTHORIZED")
        }

        // Error responses
        guard (200..<300).contains(http.statusCode) else {
            // Try to unwrap ApiResponse envelope error first
            let envelope = try? JSONDecoder().decode(ApiEnvelope<EmptyBody>.self, from: data)
            let errBody  = envelope?.error
            throw ApiError(
                statusCode: http.statusCode,
                message:    errBody?.message ?? HTTPURLResponse.localizedString(forStatusCode: http.statusCode),
                errorCode:  errBody?.resolvedCode
            )
        }

        // Unwrap ApiResponse<T> envelope → extract `.data` payload
        // Every backend endpoint returns { success: true, data: <T>, timestamp: "..." }
        let envelope = try decoder.decode(ApiEnvelope<T>.self, from: data)
        guard let payload = envelope.data else {
            throw ApiError(statusCode: http.statusCode, message: "Empty response payload", errorCode: "EMPTY_PAYLOAD")
        }
        return payload
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private struct EmptyBody: Encodable {}
