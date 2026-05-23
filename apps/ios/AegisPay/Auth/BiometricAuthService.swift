import LocalAuthentication
import SwiftUI

// MARK: — BiometricType

enum BiometricType {
    case none
    case touchID
    case faceID
    case opticID
}

// MARK: — BiometricAuthResult

enum BiometricAuthResult: Equatable {
    case success
    case userCancelled
    case lockedOut           // too many failures — passcode required
    case notEnrolled         // biometrics not set up on device
    case failed(String)      // other LAError or system failure

    var isSuccess: Bool { if case .success = self { return true }; return false }
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
    /// Returns a `BiometricAuthResult` so callers can differentiate cancel,
    /// lockout, not-enrolled, and genuine failure rather than treating all
    /// as the same error state.
    func authenticate(reason: String) async -> BiometricAuthResult {
        let context = LAContext()
        var canError: NSError?

        guard context.canEvaluatePolicy(
            .deviceOwnerAuthenticationWithBiometrics,
            error: &canError
        ) else {
            let code = (canError as? LAError)?.code
            if code == .biometryNotEnrolled { return .notEnrolled }
            if code == .biometryLockout     { return .lockedOut }
            return .failed(canError?.localizedDescription ?? "Biometrics unavailable")
        }

        do {
            try await context.evaluatePolicy(
                .deviceOwnerAuthenticationWithBiometrics,
                localizedReason: reason
            )
            return .success
        } catch let error as LAError {
            switch error.code {
            case .userCancel, .appCancel, .systemCancel:
                return .userCancelled
            case .biometryLockout:
                return .lockedOut
            case .biometryNotEnrolled:
                return .notEnrolled
            default:
                return .failed(error.localizedDescription)
            }
        } catch {
            return .failed(error.localizedDescription)
        }
    }
}
