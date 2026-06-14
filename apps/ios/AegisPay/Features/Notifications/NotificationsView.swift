import SwiftUI

@MainActor
final class NotificationsViewModel: ObservableObject {
    @Published private(set) var notifications: [PushNotification] = []
    @Published private(set) var isLoading = false
    @Published private(set) var errorMessage: String?

    private let service = NotificationService()

    func load() async {
        isLoading    = true
        errorMessage = nil
        do {
            let page = try await service.list(page: 0, size: 50)
            notifications = page.content
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }
}

// ── View ──────────────────────────────────────────────────────────────────────

struct NotificationsView: View {
    @EnvironmentObject var authStore: AuthStore
    @StateObject private var vm     = NotificationsViewModel()
    @State private var socket:       StompWebSocket?

    var body: some View {
        NavigationStack {
            Group {
                if vm.isLoading && vm.notifications.isEmpty {
                    ProgressView("Loading…")
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if vm.notifications.isEmpty {
                    emptyState
                } else {
                    notificationList
                }
            }
            .background(Color.aegisBg)
            .navigationTitle("Notifications")
            .navigationBarTitleDisplayMode(.large)
            .task {
                await vm.load()
                connectSocket()
            }
            .onDisappear { socket?.disconnect() }
            .refreshable { await vm.load() }
        }
    }

    // ── List ──────────────────────────────────────────────────────────────────

    private var notificationList: some View {
        ScrollView {
            AegisCard(padding: 0) {
                VStack(spacing: 0) {
                    ForEach(vm.notifications) { notif in
                        notificationRow(notif)
                        if notif.id != vm.notifications.last?.id {
                            Divider().padding(.leading, 60).foregroundStyle(Color.aegisBorder)
                        }
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
        }
    }

    private func notificationRow(_ notif: PushNotification) -> some View {
        HStack(alignment: .top, spacing: 14) {
            // Icon
            Image(systemName: iconName(for: notif.type))
                .font(.system(size: 20, weight: .medium))
                .foregroundStyle(iconColor(for: notif.type))
                .frame(width: 36, height: 36)
                .background(iconColor(for: notif.type).opacity(0.12))
                .clipShape(Circle())

            VStack(alignment: .leading, spacing: 4) {
                Text(notif.title)
                    .font(.aegisBodySmall)
                    .fontWeight(.semibold)
                    .foregroundStyle(Color.aegisText)
                Text(notif.body)
                    .font(.aegisCaption)
                    .foregroundStyle(Color.aegisTextMuted)
                    .lineLimit(2)
            }

            Spacer()

            Text(relativeDate(notif.createdAt))
                .font(.aegisCaption)
                .foregroundStyle(Color.aegisTextSubtle)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }

    private var emptyState: some View {
        VStack(spacing: 16) {
            Image(systemName: "bell.slash.fill")
                .font(.system(size: 52))
                .foregroundStyle(Color.aegisTextSubtle)
            Text("No notifications yet")
                .font(.aegisHeadline)
                .foregroundStyle(Color.aegisText)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    // ── WebSocket ─────────────────────────────────────────────────────────────

    private func connectSocket() {
        guard let userId = authStore.currentUser?.id else { return }
        Task {
            guard let token = try? await authStore.validAccessToken() else { return }
            socket = StompWebSocket(
                userId:    userId,
                wsBaseURL: AppConfig.wsBaseURL,
                onMessage: { [self] _ in
                    Task { await vm.load() }
                }
            )
            socket?.connect(accessToken: token)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private func iconName(for type: String) -> String {
        switch type {
        case "TRANSACTION_COMPLETED": return "checkmark.circle.fill"
        case "TRANSACTION_FAILED",
             "TRANSACTION_ROLLED_BACK": return "xmark.circle.fill"
        case "KYC_APPROVED":       return "person.badge.shield.checkmark.fill"
        case "KYC_REJECTED":       return "person.crop.circle.badge.xmark"
        case "KYC_STATUS_CHANGED": return "person.badge.shield.checkmark.fill"
        case "USER_REGISTERED":    return "person.badge.plus"
        default: return "bell.fill"
        }
    }

    private func iconColor(for type: String) -> Color {
        switch type {
        case "TRANSACTION_COMPLETED", "KYC_APPROVED", "KYC_STATUS_CHANGED", "USER_REGISTERED":
            return Color.aegisSuccess
        case "TRANSACTION_FAILED", "TRANSACTION_ROLLED_BACK", "KYC_REJECTED":
            return Color.aegisDanger
        default: return Color.aegisPrimary
        }
    }

    private func relativeDate(_ date: Date) -> String {
        let f = RelativeDateTimeFormatter()
        f.unitsStyle = .abbreviated
        return f.localizedString(for: date, relativeTo: Date())
    }
}
