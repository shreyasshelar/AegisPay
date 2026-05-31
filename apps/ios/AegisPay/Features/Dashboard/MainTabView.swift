import SwiftUI

private let backOfficeRoles: Set<String> = ["BACK_OFFICE", "ADMIN", "MERCHANT_OPS"]
private let adminRoles:      Set<String> = ["ADMIN"]

struct MainTabView: View {
    @EnvironmentObject var authStore: AuthStore
    @State private var selectedTab     = 0
    @State private var unreadCount     = 0
    @State private var didSetInitialTab = false

    /// True when the signed-in user has back-office access.
    private var isBackOfficeUser: Bool {
        let role = authStore.currentUser?.role ?? ""
        return backOfficeRoles.contains(role)
    }

    /// True when the signed-in user is an ADMIN (can access Triage Agent).
    private var isAdminUser: Bool {
        let role = authStore.currentUser?.role ?? ""
        return adminRoles.contains(role)
    }

    var body: some View {
        TabView(selection: $selectedTab) {
            // ── Customer tabs — hidden for staff roles ────────────────────────
            // Mirrors the web sidebar: isStaffRole(role) hides NAV_ITEMS so
            // admins and back-office users don't see Dashboard, Send, etc.
            if !isBackOfficeUser {
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

                WalletView()
                    .tabItem {
                        Label("Wallet", systemImage: selectedTab == 3 ? "wallet.pass.fill" : "wallet.pass")
                    }
                    .tag(3)

                NotificationsView()
                    .tabItem {
                        Label("Alerts", systemImage: selectedTab == 4 ? "bell.fill" : "bell")
                    }
                    .badge(unreadCount > 0 ? unreadCount : 0)
                    .tag(4)

                ProfileView()
                    .tabItem {
                        Label("Profile", systemImage: selectedTab == 5 ? "person.fill" : "person")
                    }
                    .tag(5)
            }

            // ── Back-office tab (role-gated) ───────────────────────────────────
            if isBackOfficeUser {
                BackOfficeView()
                    .tabItem {
                        Label(
                            "Admin",
                            systemImage: selectedTab == 6
                                ? "shield.lefthalf.filled"
                                : "shield.lefthalf.fill"
                        )
                    }
                    .tag(6)
            }

            // ── AI Triage tab (ADMIN only) ────────────────────────────────────
            if isAdminUser {
                TriageView()
                    .tabItem {
                        Label(
                            "Triage",
                            systemImage: selectedTab == 7
                                ? "stethoscope.circle.fill"
                                : "stethoscope.circle"
                        )
                    }
                    .tag(7)
            }
        }
        .tint(Color.aegisPrimary)
        // Set role-based initial tab — mirrors web ROLE_LANDING and Android nav host.
        // Admin → Triage (7), Back-office → Admin (6), Customer → Home (0).
        // Only runs once: guard prevents re-entry on subsequent onAppear events.
        .task {
            guard !didSetInitialTab else { return }
            didSetInitialTab = true
            if isAdminUser      { selectedTab = 7 }
            else if isBackOfficeUser { selectedTab = 6 }
        }
        // Clear badge when Notifications tab selected
        .onChange(of: selectedTab) { _, newTab in
            if newTab == 4 { unreadCount = 0 }   // tab 4 = Alerts
        }
        // Listen for incoming notifications via NotificationCenter (from PushNotificationHandler)
        .onReceive(NotificationCenter.default.publisher(for: .aegisNewNotification)) { _ in
            if selectedTab != 4 { unreadCount += 1 }
        }
    }
}

extension Notification.Name {
    static let aegisNewNotification = Notification.Name("AegisNewPushNotification")
}
