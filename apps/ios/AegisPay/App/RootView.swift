import SwiftUI

/// Top-level router — switches between auth and dashboard trees.
/// Also manages the biometric lock overlay when the app returns from background.
struct RootView: View {

    @EnvironmentObject var authStore: AuthStore

    @StateObject private var biometricService = BiometricAuthService()

    /// `true` when the app requires re-authentication before allowing access.
    @State private var isLocked = false

    var body: some View {
        Group {
            switch authStore.state {
            case .loading:
                SplashView()

            case .unauthenticated:
                LoginView()

            case .authenticated:
                MainTabView()
                    .overlay {
                        if isLocked {
                            BiometricLockView(
                                onUnlock: {
                                    isLocked = false
                                },
                                onFallback: {
                                    isLocked = false
                                    Task { await authStore.signOut() }
                                }
                            )
                            .environmentObject(biometricService)
                            .transition(.opacity)
                        }
                    }
            }
        }
        .animation(.easeInOut(duration: 0.25), value: authStore.state)
        .animation(.easeInOut(duration: 0.2), value: isLocked)
        // Lock when the app is about to go to the background.
        .onReceive(
            NotificationCenter.default.publisher(for: UIApplication.willResignActiveNotification)
        ) { _ in
            if biometricService.isEnabled {
                isLocked = true
            }
        }
        // Ensure the lock is showing when the app becomes active again.
        .onReceive(
            NotificationCenter.default.publisher(for: UIApplication.didBecomeActiveNotification)
        ) { _ in
            if biometricService.isEnabled && isLocked {
                // Already locked — SwiftUI overlay is visible; biometric prompt
                // will be triggered by BiometricLockView.onAppear automatically.
                isLocked = true
            }
        }
        .environmentObject(biometricService)
    }
}
