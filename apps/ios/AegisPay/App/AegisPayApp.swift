import SwiftUI
import StripePaymentSheet

// MARK: — AppDelegate (APNs callbacks)

final class AppDelegate: NSObject, UIApplicationDelegate {

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        // Initialize Stripe with the publishable key before any PaymentSheet usage.
        // pk_live_* is injected by CI via STRIPE_PUBLISHABLE_KEY in Info.plist.
        StripeAPI.defaultPublishableKey = AppConfig.stripePublishableKey
        STPAPIClient.shared.publishableKey = AppConfig.stripePublishableKey

        PushNotificationHandler.shared.requestPermissionAndRegister()
        return true
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        // userId resolved from AuthStore at app level; safe to access keychain directly
        let userId = TokenStore.shared.userId ?? ""
        Task { @MainActor in
            PushNotificationHandler.shared.didRegisterWithToken(deviceToken, userId: userId)
        }
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        // Non-fatal — app works without push; log only
        print("[APNs] Registration failed: \(error.localizedDescription)")
    }
}

// MARK: — App entry point

@main
struct AegisPayApp: App {

    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    @StateObject private var authStore = AuthStore()
    @StateObject private var apiClient = ApiClient.shared

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(authStore)
                .environmentObject(apiClient)
                .onOpenURL { url in
                    // Handle both OAuth redirect URIs (aegispay://) and
                    // Universal Links (https://api.aegispay.shreyasshelar.uk/...)
                    if url.scheme == "aegispay" && url.host != "app" {
                        // OAuth redirect callback
                        authStore.handleRedirectURL(url)
                    } else {
                        // Deep link (universal link or custom scheme deep link)
                        DeepLinkRouter.shared.handle(url: url)
                    }
                }
        }
    }
}
