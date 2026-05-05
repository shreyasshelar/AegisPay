import LocalAuthentication
import SwiftUI

// MARK: — BiometricType

enum BiometricType {
    case none
    case touchID
    case faceID
    case opticID
}

// MARK: — BiometricAuthService

/// Wraps LocalAuthentication to provide biometric availability, type detection,
/// user preference persistence, and async authentication.
@MainActor
final class BiometricAuthService: ObservableObject {

    @Published private(set) var isAvailable: Bool = false
    @Published private(set) var biometricType: BiometricType = .none

    // ── Preference — backed by UserDefaults ────────────────────────────────────

    var isEnabled: Bool {
        get { UserDefaults.standard.bool(forKey: "aegispay.biometric.enabled") }
        set {
            UserDefaults.standard.set(newValue, forKey: "aegispay.biometric.enabled")
            // Notify any bound SwiftUI views about the change.
            objectWillChange.send()
        }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    init() {
        checkAvailability()
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /// Interrogates `LAContext` and updates `isAvailable` and `biometricType`.
    func checkAvailability() {
        let context = LAContext()
        var error: NSError?
        let canEvaluate = context.canEvaluatePolicy(
            .deviceOwnerAuthenticationWithBiometrics,
            error: &error
        )

        isAvailable = canEvaluate

        if canEvaluate {
            switch context.biometryType {
            case .touchID:
                biometricType = .touchID
            case .faceID:
                biometricType = .faceID
            case .opticID:
                biometricType = .opticID
            case .none:
                biometricType = .none
            @unknown default:
                biometricType = .none
            }
        } else {
            biometricType = .none
        }
    }

    /// Prompts the user to authenticate with biometrics.
    /// Returns `true` on success, `false` on any failure — the caller decides
    /// what to do (e.g. fall back to password or sign out).
    func authenticate(reason: String) async -> Bool {
        let context = LAContext()
        var canError: NSError?

        guard context.canEvaluatePolicy(
            .deviceOwnerAuthenticationWithBiometrics,
            error: &canError
        ) else {
            return false
        }

        do {
            let success = try await context.evaluatePolicy(
                .deviceOwnerAuthenticationWithBiometrics,
                localizedReason: reason
            )
            return success
        } catch {
            // LAError cases (userCancel, biometryLockout, etc.) all map to false.
            return false
        }
    }
}
