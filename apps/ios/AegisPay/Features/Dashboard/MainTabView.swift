import SwiftUI

private let backOfficeRoles: Set<String> = ["BACK_OFFICE", "ADMIN", "MERCHANT_OPS"]

struct MainTabView: View {
    @EnvironmentObject var authStore: AuthStore
    @State private var selectedTab     = 0
    @State private var unreadCount     = 0

    /// True when the signed-in user has back-office access.
    private var isBackOfficeUser: Bool {
        let role = authStore.currentUser?.role ?? ""
        return backOfficeRoles.contains(role)
    }

    var body: some View {
        TabView(selection: $selectedTab) {
            DashboardView()
                .tabItem {
                    Label("Home", systemImage: selectedTab == 0 ? "house.fill" : "house")
                }
                .tag(0)

            TransactionListView()
                .tabItem {
                    Label(
                        "Transactions",
                        systemImage: selectedTab == 1 ? "list.bullet.rectangle.fill" : "list.bullet.rectangle"
                    )
                }
                .tag(1)

            SendMoneyView()
                .tabItem {
                    Label("Send", systemImage: "arrow.up.circle.fill")
                }
                .tag(2)

            NotificationsView()
                .tabItem {
                    Label("Alerts", systemImage: selectedTab == 3 ? "bell.fill" : "bell")
                }
                .badge(unreadCount > 0 ? unreadCount : 0)
                .tag(3)

            ProfileView()
                .tabItem {
                    Label("Profile", systemImage: selectedTab == 4 ? "person.fill" : "person")
                }
                .tag(4)

            // ── Back-office tab (role-gated) ───────────────────────────────────
            if isBackOfficeUser {
                BackOfficeView()
                    .tabItem {
                        Label(
                            "Admin",
                            systemImage: selectedTab == 5
                                ? "shield.lefthalf.filled"
                                : "shield.lefthalf.fill"
                        )
                    }
                    .tag(5)
            }
        }
        .tint(Color.aegisPrimary)
        // Clear badge when Notifications tab selected
        .onChange(of: selectedTab) { _, newTab in
            if newTab == 3 { unreadCount = 0 }
        }
        // Listen for incoming notifications via NotificationCenter (from PushNotificationHandler)
        .onReceive(NotificationCenter.default.publisher(for: .aegisNewNotification)) { _ in
            if selectedTab != 3 { unreadCount += 1 }
        }
    }
}

extension Notification.Name {
    static let aegisNewNotification = Notification.Name("AegisNewPushNotification")
}
