import SwiftUI

struct DashboardView: View {
    @EnvironmentObject var authStore: AuthStore
    @StateObject private var vm = DashboardViewModel()

    @State private var navigateToDetail: String? = nil

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: 16) {

                    // ── Balance card ──────────────────────────────────────────
                    balanceCard

                    // ── Stat row ──────────────────────────────────────────────
                    HStack(spacing: 12) {
                        AegisStatCard(
                            label:    "Completed",
                            value:    "\(vm.completedCount)",
                            icon:     "checkmark.circle.fill",
                            iconTint: Color.aegisSuccess
                        )
                        AegisStatCard(
                            label:    "Failed",
                            value:    "\(vm.failedCount)",
                            icon:     "xmark.circle.fill",
                            iconTint: Color.aegisDanger
                        )
                    }

                    // ── Quick actions ─────────────────────────────────────────
                    quickActions

                    // ── Recent transactions ───────────────────────────────────
                    recentTransactions
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)
                .padding(.bottom, 24)
            }
            .background(Color.aegisBg)
            .navigationTitle("Dashboard")
            .navigationBarTitleDisplayMode(.large)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    refreshButton
                }
            }
            .refreshable { await vm.load(userId: userId) }
            .task {
                await vm.load(userId: userId)
                if let token = try? await authStore.validAccessToken() {
                    vm.connectSocket(userId: userId, accessToken: token)
                }
            }
            .onDisappear { vm.disconnectSocket() }
            // Error banner
            .overlay(alignment: .top) {
                if let error = vm.errorMessage {
                    errorBanner(message: error)
                        .padding(.top, 4)
                        .padding(.horizontal, 16)
                        .transition(.move(edge: .top).combined(with: .opacity))
                }
            }
            .animation(.easeInOut, value: vm.errorMessage)
        }
    }

    // ── Subviews ──────────────────────────────────────────────────────────────

    @ViewBuilder
    private var balanceCard: some View {
        AegisCard(padding: 24) {
            VStack(spacing: 6) {
                Text("Available Balance")
                    .font(.aegisCaption)
                    .foregroundStyle(Color.aegisTextMuted)

                if vm.isLoading && vm.account == nil {
                    SkeletonRect(width: 180, height: 40, radius: 8)
                } else {
                    Text(vm.account?.availableBalance.formatted(currency: vm.account?.currency ?? "INR")
                         ?? "—")
                        .font(.aegisAmount)
                        .foregroundStyle(Color.aegisText)
                }

                if let reserved = vm.account?.reservedBalance,
                   reserved > 0 {
                    Text("\(reserved.formatted(currency: vm.account!.currency)) reserved")
                        .font(.aegisCaption)
                        .foregroundStyle(Color.aegisWarning)
                }
            }
            .frame(maxWidth: .infinity)
        }
        .frame(maxWidth: .infinity)
    }

    @ViewBuilder
    private var quickActions: some View {
        HStack(spacing: 12) {
            NavigationLink(destination: SendMoneyView()) {
                Label("Send Money", systemImage: "arrow.up.circle.fill")
                    .font(.aegisBodySmall)
                    .fontWeight(.semibold)
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(Color.aegisPrimary)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
            }

            NavigationLink(destination: TransactionListView()) {
                Label("History", systemImage: "list.bullet.rectangle")
                    .font(.aegisBodySmall)
                    .fontWeight(.semibold)
                    .foregroundStyle(Color.aegisPrimary)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(Color.aegisSurface)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
                    .overlay(
                        RoundedRectangle(cornerRadius: 14)
                            .stroke(Color.aegisBorder, lineWidth: 1.5)
                    )
            }
        }
    }

    @ViewBuilder
    private var recentTransactions: some View {
        AegisCard(padding: 0) {
            VStack(spacing: 0) {
                HStack {
                    Text("Recent Transactions")
                        .font(.aegisSubhead)
                        .foregroundStyle(Color.aegisText)
                    Spacer()
                    NavigationLink(destination: TransactionListView()) {
                        HStack(spacing: 3) {
                            Text("View all")
                            Image(systemName: "arrow.up.right")
                        }
                        .font(.aegisCaption)
                        .foregroundStyle(Color.aegisPrimary)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 14)

                Divider()
                    .foregroundStyle(Color.aegisBorder)

                if vm.isLoading && vm.recentTx.isEmpty {
                    VStack(spacing: 0) {
                        ForEach(0..<5, id: \.self) { _ in
                            TransactionRowSkeleton()
                            Divider().foregroundStyle(Color.aegisBorder)
                        }
                    }
                } else if vm.recentTx.isEmpty {
                    VStack(spacing: 8) {
                        Image(systemName: "tray")
                            .font(.system(size: 36))
                            .foregroundStyle(Color.aegisTextSubtle)
                        Text("No transactions yet")
                            .font(.aegisBody)
                            .foregroundStyle(Color.aegisTextMuted)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(40)
                } else {
                    ForEach(vm.recentTx) { tx in
                        NavigationLink(destination: TransactionDetailView(transactionId: tx.id)) {
                            AegisTransactionRow(
                                transaction:   tx,
                                currentUserId: userId
                            )
                        }
                        .buttonStyle(.plain)

                        if tx.id != vm.recentTx.last?.id {
                            Divider()
                                .padding(.leading, 70)
                                .foregroundStyle(Color.aegisBorder)
                        }
                    }
                }
            }
        }
    }

    private var refreshButton: some View {
        Button {
            HapticFeedback.selection()
            Task { await vm.load(userId: userId) }
        } label: {
            Image(systemName: "arrow.clockwise")
                .font(.system(size: 15, weight: .medium))
        }
        .disabled(vm.isLoading)
    }

    private func errorBanner(message: String) -> some View {
        HStack(spacing: 10) {
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundStyle(Color.aegisWarning)
            Text(message)
                .font(.aegisBodySmall)
                .foregroundStyle(Color.aegisText)
                .lineLimit(2)
            Spacer()
        }
        .padding(14)
        .background(Color.aegisWarningLight)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .shadow(color: .black.opacity(0.06), radius: 6, y: 2)
    }

    private var userId: String {
        authStore.currentUser?.id ?? ""
    }
}

// ── Skeleton helpers ──────────────────────────────────────────────────────────

struct SkeletonRect: View {
    let width:  CGFloat
    let height: CGFloat
    var radius: CGFloat = 6

    var body: some View {
        RoundedRectangle(cornerRadius: radius)
            .fill(Color.aegisBorder)
            .frame(width: width, height: height)
            .shimmer()
    }
}

struct TransactionRowSkeleton: View {
    var body: some View {
        HStack(spacing: 14) {
            Circle().fill(Color.aegisBorder).frame(width: 40, height: 40).shimmer()
            VStack(alignment: .leading, spacing: 6) {
                SkeletonRect(width: 130, height: 12)
                SkeletonRect(width: 80, height: 10)
            }
            Spacer()
            SkeletonRect(width: 70, height: 14)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }
}

// ── Shimmer modifier ─────────────────────────────────────────────────────────

struct ShimmerModifier: ViewModifier {
    @State private var phase: CGFloat = -1

    func body(content: Content) -> some View {
        content
            .overlay(
                LinearGradient(
                    colors: [.clear, .white.opacity(0.45), .clear],
                    startPoint: UnitPoint(x: phase, y: 0.5),
                    endPoint:   UnitPoint(x: phase + 0.5, y: 0.5)
                )
                .allowsHitTesting(false)
            )
            .onAppear {
                withAnimation(.linear(duration: 1.4).repeatForever(autoreverses: false)) {
                    phase = 1.5
                }
            }
    }
}

extension View {
    func shimmer() -> some View { modifier(ShimmerModifier()) }
}
