import SwiftUI

/// Full-screen overlay that gates access to the app when biometric re-authentication
/// is required (e.g. after the app returns from background).
struct BiometricLockView: View {

    // ── Dependencies ───────────────────────────────────────────────────────────

    @EnvironmentObject var biometricService: BiometricAuthService

    let onUnlock: () -> Void
    let onFallback: () -> Void

    // ── State ──────────────────────────────────────────────────────────────────

    @State private var isAuthenticating = false
    @State private var authFailed = false
    @State private var authFailureMessage = "Authentication failed. Try again."

    // ── Body ───────────────────────────────────────────────────────────────────

    var body: some View {
        ZStack {
            Color(.systemBackground)
                .ignoresSafeArea()

            VStack(spacing: 0) {
                Spacer()

                // AegisPay logo
                VStack(spacing: 16) {
                    Image(systemName: "shield.fill")
                        .font(.system(size: 64, weight: .medium))
                        .foregroundStyle(Color.aegisPrimary)
                        .symbolRenderingMode(.hierarchical)
                        .accessibilityHidden(true)

                    Text("AegisPay")
                        .font(.largeTitle)
                        .fontWeight(.bold)
                        .foregroundStyle(Color.aegisText)

                    Text("Your session is locked")
                        .font(.subheadline)
                        .foregroundStyle(Color.aegisTextMuted)
                }
                .padding(.bottom, 56)

                // Biometric icon + prompt
                VStack(spacing: 24) {
                    ZStack {
                        Circle()
                            .fill(Color.aegisPrimaryLight)
                            .frame(width: 88, height: 88)

                        Image(systemName: biometricIconName)
                            .font(.system(size: 40, weight: .light))
                            .foregroundStyle(Color.aegisPrimary)
                            .symbolEffect(.pulse, isActive: isAuthenticating)
                    }
                    .accessibilityHidden(true)

                    if authFailed {
                        Text(authFailureMessage)
                            .font(.callout)
                            .foregroundStyle(Color.aegisDanger)
                            .multilineTextAlignment(.center)
                            .transition(.opacity.combined(with: .move(edge: .top)))
                    }

                    // Primary unlock button
                    Button {
                        triggerBiometricAuth()
                    } label: {
                        HStack(spacing: 10) {
                            Image(systemName: biometricIconName)
                            Text(unlockButtonLabel)
                                .fontWeight(.semibold)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                        .background(Color.aegisPrimary)
                        .foregroundStyle(.white)
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                    }
                    .disabled(isAuthenticating)
                    .accessibilityLabel(unlockButtonLabel)
                    .accessibilityHint("Double-tap to authenticate using biometrics")
                    .accessibilityAddTraits(.isButton)
                }
                .padding(.horizontal, 32)

                Spacer()

                // Fallback — sign out
                Button {
                    onFallback()
                } label: {
                    Text("Sign out instead")
                        .font(.subheadline)
                        .foregroundStyle(Color.aegisTextMuted)
                        .underline()
                }
                .padding(.bottom, 40)
                .accessibilityLabel("Sign out instead")
                .accessibilityHint("Signs you out of AegisPay")
                .accessibilityAddTraits(.isButton)
            }
        }
        .onAppear {
            triggerBiometricAuth()
        }
        .animation(.easeInOut(duration: 0.2), value: authFailed)
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private var biometricIconName: String {
        switch biometricService.biometricType {
        case .faceID, .opticID:
            return "faceid"
        case .touchID:
            return "touchid"
        case .none:
            return "lock.fill"
        }
    }

    private var unlockButtonLabel: String {
        switch biometricService.biometricType {
        case .faceID:
            return "Unlock with Face ID"
        case .opticID:
            return "Unlock with Optic ID"
        case .touchID:
            return "Unlock with Touch ID"
        case .none:
            return "Unlock"
        }
    }

    private func triggerBiometricAuth() {
        guard !isAuthenticating else { return }
        isAuthenticating = true
        authFailed = false

        Task {
            let result = await biometricService.authenticate(reason: "Unlock AegisPay")
            isAuthenticating = false
            switch result {
            case .success:
                onUnlock()
            case .userCancelled:
                authFailed = false   // user intentionally cancelled — no error shown
            case .lockedOut:
                authFailed = true
                authFailureMessage = "Too many attempts. Use your passcode to unlock."
            case .notEnrolled:
                authFailed = true
                authFailureMessage = "Biometrics not set up. Enable them in Settings."
            case .failed(let msg):
                authFailed = true
                authFailureMessage = msg.isEmpty ? "Authentication failed. Try again." : msg
            }
        }
    }
}

// MARK: — Preview

#if DEBUG
#Preview {
    let service = BiometricAuthService()
    return BiometricLockView(onUnlock: {}, onFallback: {})
        .environmentObject(service)
}
#endif
