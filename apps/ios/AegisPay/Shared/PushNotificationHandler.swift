import UIKit
import UserNotifications

/// Handles APNs registration, token forwarding to backend, and foreground
/// notification display. Wire into AegisPayApp via AppDelegate.
@MainActor
final class PushNotificationHandler: NSObject, UNUserNotificationCenterDelegate {

    static let shared = PushNotificationHandler()

    private let userService = UserService()

    // ── Request permission & register ─────────────────────────────────────────

    func requestPermissionAndRegister() {
        UNUserNotificationCenter.current().delegate = self
        UNUserNotificationCenter.current().requestAuthorization(
            options: [.alert, .sound, .badge]
        ) { granted, _ in
            guard granted else { return }
            DispatchQueue.main.async {
                UIApplication.shared.registerForRemoteNotifications()
            }
        }
    }

    // ── Handle APNs device token ──────────────────────────────────────────────

    /// Call from AppDelegate.application(_:didRegisterForRemoteNotificationsWithDeviceToken:)
    func didRegisterWithToken(_ tokenData: Data, userId: String) {
        let token = tokenData.map { String(format: "%02x", $0) }.joined()
        Task {
            try? await userService.registerPushToken(userId: userId, token: token, platform: "ios")
        }
    }

    // ── Foreground notification display ───────────────────────────────────────

    /// Show banner even when app is in foreground
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler handler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        // Post internal event so MainTabView can update its badge
        NotificationCenter.default.post(name: .aegisNewNotification, object: nil)
        handler([.banner, .sound, .badge])
    }

    /// Clear badge when user opens the app via notification
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler handler: @escaping () -> Void
    ) {
        UIApplication.shared.applicationIconBadgeNumber = 0
        handler()
    }
}
