import SwiftUI

/// Top-level router — switches between auth and dashboard trees.
struct RootView: View {
    @EnvironmentObject var authStore: AuthStore

    var body: some View {
        Group {
            switch authStore.state {
            case .loading:
                SplashView()

            case .unauthenticated:
                LoginView()

            case .authenticated:
                MainTabView()
            }
        }
        .animation(.easeInOut(duration: 0.25), value: authStore.state)
    }
}
