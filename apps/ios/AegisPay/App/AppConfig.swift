import Foundation

/// Centralised configuration — values come from Info.plist (injected by CI/CD).
/// Never hardcode secrets; they are injected via Xcode build settings from
/// environment variables in CI.
enum AppConfig {

    // ── OAuth2 / PKCE ─────────────────────────────────────────────────────────
    static let keycloakIssuer: URL = {
        let raw = Bundle.main.object(forInfoDictionaryKey: "KEYCLOAK_ISSUER") as? String
            ?? "http://localhost:8180/realms/aegispay"
        return URL(string: raw)!
    }()

    static let oauthClientId: String =
        Bundle.main.object(forInfoDictionaryKey: "OAUTH_CLIENT_ID") as? String
        ?? "aegispay-ios"

    /// Must match the URL scheme registered in Info.plist under CFBundleURLTypes.
    static let oauthRedirectURI: URL =
        URL(string: "aegispay://oauth/callback")!

    static let oauthScopes: [String] =
        ["openid", "email", "profile", "offline_access"]

    // ── Backend ───────────────────────────────────────────────────────────────
    static let apiBaseURL: URL = {
        let raw = Bundle.main.object(forInfoDictionaryKey: "API_BASE_URL") as? String
            ?? "http://localhost:8080"
        return URL(string: raw)!
    }()

    static let wsBaseURL: String =
        Bundle.main.object(forInfoDictionaryKey: "WS_BASE_URL") as? String
        ?? "ws://localhost:8090"

    // ── Keychain ──────────────────────────────────────────────────────────────
    static let keychainService = "io.aegispay.app"

    // ── Certificate Pinning ───────────────────────────────────────────────────
    /// SHA-256 base64 hashes of the server's SubjectPublicKeyInfo (SPKI) bytes.
    /// In production these are injected via the `PINNED_CERT_HASHES` Info.plist
    /// key as a comma-separated string.
    /// When empty (local dev), the `CertificatePinningDelegate` passes all
    /// challenges through so development still works without a pinned cert.
    static let pinnedCertificateHashes: [String] = {
        guard let raw = Bundle.main.object(forInfoDictionaryKey: "PINNED_CERT_HASHES") as? String,
              !raw.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        else {
            return []
        }
        return raw
            .split(separator: ",")
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
    }()
}
