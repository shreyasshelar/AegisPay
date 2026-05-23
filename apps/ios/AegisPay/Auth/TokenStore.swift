import Foundation
import KeychainAccess

/// Thread-safe Keychain-backed token store.
/// All tokens are stored in the Keychain with `.whenUnlockedThisDeviceOnly`
/// accessibility to prevent them appearing in iCloud backups.
final class TokenStore {

    nonisolated(unsafe) static let shared = TokenStore()

    private let keychain: Keychain

    private enum Key: String {
        case accessToken    = "access_token"
        case refreshToken   = "refresh_token"
        case idToken        = "id_token"
        case expiresAt      = "expires_at"    // stored as ISO-8601 string
        case userId         = "user_id"
        case userRole       = "user_role"
    }

    private init() {
        keychain = Keychain(service: AppConfig.keychainService)
            .accessibility(.whenUnlockedThisDeviceOnly)
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    var accessToken: String? {
        get { try? keychain.get(Key.accessToken.rawValue) }
        set { set(newValue, for: .accessToken) }
    }

    var refreshToken: String? {
        get { try? keychain.get(Key.refreshToken.rawValue) }
        set { set(newValue, for: .refreshToken) }
    }

    var idToken: String? {
        get { try? keychain.get(Key.idToken.rawValue) }
        set { set(newValue, for: .idToken) }
    }

    var expiresAt: Date? {
        get {
            guard let raw = try? keychain.get(Key.expiresAt.rawValue) else { return nil }
            return ISO8601DateFormatter().date(from: raw)
        }
        set {
            let raw = newValue.map { ISO8601DateFormatter().string(from: $0) }
            set(raw, for: .expiresAt)
        }
    }

    var userId: String? {
        get { try? keychain.get(Key.userId.rawValue) }
        set { set(newValue, for: .userId) }
    }

    var userRole: String? {
        get { try? keychain.get(Key.userRole.rawValue) }
        set { set(newValue, for: .userRole) }
    }

    // ── Derived ───────────────────────────────────────────────────────────────

    /// True if the access token exists and has at least 60 s left.
    var isAccessTokenValid: Bool {
        guard accessToken != nil, let exp = expiresAt else { return false }
        return exp.timeIntervalSinceNow > 60
    }

    /// True if there's a refresh token available.
    var canRefresh: Bool { refreshToken != nil }

    // ── Mutations ─────────────────────────────────────────────────────────────

    func store(
        accessToken:  String,
        refreshToken: String?,
        idToken:      String?,
        expiresIn:    TimeInterval,
        userId:       String?,
        userRole:     String?
    ) {
        self.accessToken  = accessToken
        self.refreshToken = refreshToken
        self.idToken      = idToken
        self.expiresAt    = Date().addingTimeInterval(expiresIn)
        self.userId       = userId
        self.userRole     = userRole
    }

    func clear() {
        try? keychain.removeAll()
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private func set(_ value: String?, for key: Key) {
        if let value {
            try? keychain.set(value, key: key.rawValue)
        } else {
            try? keychain.remove(key.rawValue)
        }
    }
}
