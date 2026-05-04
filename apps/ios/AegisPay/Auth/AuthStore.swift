import SwiftUI
import AppAuth
import Combine

// ── Auth state machine ─────────────────────────────────────────────────────

enum AuthState: Equatable {
    case loading
    case unauthenticated
    case authenticated
}

// ── AuthStore ─────────────────────────────────────────────────────────────────

/// Observable auth state. Orchestrates AppAuth PKCE flow + token refresh.
@MainActor
final class AuthStore: ObservableObject {

    @Published private(set) var state: AuthState = .loading
    @Published private(set) var currentUser: AuthUser?
    @Published private(set) var lastError: AuthError?

    private let tokenStore = TokenStore.shared

    /// AppAuth in-progress authorization flow handle (must be kept alive).
    private var currentAuthorizationFlow: OIDExternalUserAgentSession?

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    init() {
        Task { await restoreSession() }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /// Begins the OAuth2 PKCE login flow using AppAuth + ASWebAuthenticationSession.
    func signIn(presentingViewController: UIViewController) async {
        do {
            let config = try await OIDAuthorizationService.discoverConfiguration(
                forIssuer: AppConfig.keycloakIssuer
            )

            let request = OIDAuthorizationRequest(
                configuration:              config,
                clientId:                   AppConfig.oauthClientId,
                clientSecret:               nil,
                scopes:                     AppConfig.oauthScopes,
                redirectURL:                AppConfig.oauthRedirectURI,
                responseType:               OIDResponseTypeCode,
                additionalParameters:       nil
            )

            let authResponse = try await withCheckedThrowingContinuation {
                (continuation: CheckedContinuation<OIDAuthState, Error>) in

                currentAuthorizationFlow =
                    OIDAuthState.authState(
                        byPresenting: request,
                        presenting:   presentingViewController
                    ) { authState, error in
                        if let authState {
                            continuation.resume(returning: authState)
                        } else {
                            continuation.resume(throwing: error ?? AuthError.unknown)
                        }
                    }
            }

            try persistTokens(from: authResponse)
            state = .authenticated

        } catch {
            lastError = AuthError.from(error)
        }
    }

    /// Handles the redirect URL returned by the OS (called from onOpenURL).
    func handleRedirectURL(_ url: URL) {
        currentAuthorizationFlow?.resumeExternalUserAgentFlow(with: url)
    }

    /// Returns a valid access token, refreshing if necessary.
    /// Throws `AuthError.sessionExpired` if refresh also fails.
    func validAccessToken() async throws -> String {
        if tokenStore.isAccessTokenValid, let token = tokenStore.accessToken {
            return token
        }
        guard tokenStore.canRefresh else {
            await signOut()
            throw AuthError.sessionExpired
        }
        return try await refreshTokens()
    }

    func signOut() async {
        tokenStore.clear()
        currentUser  = nil
        state        = .unauthenticated
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private func restoreSession() async {
        if tokenStore.isAccessTokenValid {
            currentUser = buildUser()
            state = .authenticated
        } else if tokenStore.canRefresh {
            do {
                _ = try await refreshTokens()
                currentUser = buildUser()
                state = .authenticated
            } catch {
                state = .unauthenticated
            }
        } else {
            state = .unauthenticated
        }
    }

    @discardableResult
    private func refreshTokens() async throws -> String {
        guard let refreshToken = tokenStore.refreshToken else {
            throw AuthError.sessionExpired
        }

        let config = try await OIDAuthorizationService.discoverConfiguration(
            forIssuer: AppConfig.keycloakIssuer
        )

        let tokenRequest = OIDTokenRequest(
            configuration: config,
            grantType:     OIDGrantTypeRefreshToken,
            authorizationCode: nil,
            redirectURL:   AppConfig.oauthRedirectURI,
            clientID:      AppConfig.oauthClientId,
            clientSecret:  nil,
            scope:         nil,
            refreshToken:  refreshToken,
            codeVerifier:  nil,
            additionalParameters: nil
        )

        let response = try await withCheckedThrowingContinuation {
            (continuation: CheckedContinuation<OIDTokenResponse, Error>) in
            OIDAuthorizationService.perform(tokenRequest) { response, error in
                if let response {
                    continuation.resume(returning: response)
                } else {
                    continuation.resume(throwing: error ?? AuthError.refreshFailed)
                }
            }
        }

        guard let accessToken = response.accessToken else {
            throw AuthError.refreshFailed
        }

        tokenStore.store(
            accessToken:  accessToken,
            refreshToken: response.refreshToken ?? refreshToken,
            idToken:      response.idToken,
            expiresIn:    response.accessTokenExpirationDate?.timeIntervalSinceNow ?? 3600,
            userId:       tokenStore.userId,
            userRole:     tokenStore.userRole
        )

        return accessToken
    }

    private func persistTokens(from authState: OIDAuthState) throws {
        guard let accessToken  = authState.lastTokenResponse?.accessToken,
              let refreshToken = authState.lastTokenResponse?.refreshToken
        else {
            throw AuthError.missingTokens
        }

        let expiresIn = authState.lastTokenResponse?.accessTokenExpirationDate?
            .timeIntervalSinceNow ?? 3600

        // Extract userId and role from ID token claims (best-effort)
        var userId: String?
        var userRole: String?
        if let idToken = authState.lastTokenResponse?.idToken {
            let claims = decodeJWTPayload(idToken)
            userId   = claims["sub"] as? String
            let roles = (claims["realm_access"] as? [String: Any])?["roles"] as? [String] ?? []
            userRole = roles.contains("ADMIN") ? "ADMIN"
                     : roles.contains("BACK_OFFICE") ? "BACK_OFFICE"
                     : "CUSTOMER"
        }

        tokenStore.store(
            accessToken:  accessToken,
            refreshToken: refreshToken,
            idToken:      authState.lastTokenResponse?.idToken,
            expiresIn:    expiresIn,
            userId:       userId,
            userRole:     userRole
        )
    }

    private func buildUser() -> AuthUser? {
        guard let userId = tokenStore.userId else { return nil }
        return AuthUser(
            id:   userId,
            role: tokenStore.userRole ?? "CUSTOMER"
        )
    }

    /// Decodes the payload segment of a JWT without verification (claims only).
    private func decodeJWTPayload(_ jwt: String) -> [String: Any] {
        let segments = jwt.split(separator: ".")
        guard segments.count == 3 else { return [:] }
        var base64 = String(segments[1])
        // Pad to multiple of 4
        let remainder = base64.count % 4
        if remainder != 0 { base64 += String(repeating: "=", count: 4 - remainder) }
        guard let data = Data(base64Encoded: base64),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
        else { return [:] }
        return json
    }
}

// ── Supporting types ──────────────────────────────────────────────────────────

struct AuthUser: Equatable {
    let id:   String
    let role: String
}

enum AuthError: LocalizedError {
    case unknown
    case sessionExpired
    case refreshFailed
    case missingTokens
    case wrapped(Error)

    var errorDescription: String? {
        switch self {
        case .unknown:        return "An unknown auth error occurred."
        case .sessionExpired: return "Your session has expired. Please sign in again."
        case .refreshFailed:  return "Failed to refresh your session."
        case .missingTokens:  return "Authentication succeeded but tokens were missing."
        case .wrapped(let e): return e.localizedDescription
        }
    }

    static func from(_ error: Error) -> AuthError {
        if let ae = error as? AuthError { return ae }
        return .wrapped(error)
    }
}
