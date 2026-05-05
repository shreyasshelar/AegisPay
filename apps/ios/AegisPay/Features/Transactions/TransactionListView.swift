import SwiftUI

struct TransactionListView: View {
    @EnvironmentObject var authStore: AuthStore
    @StateObject private var vm = TransactionListViewModel()

    @State private var showFilters = false

    var body: some View {
        NavigationStack {
            Group {
                if vm.isLoading && vm.transactions.isEmpty {
                    listSkeleton
                } else if vm.transactions.isEmpty {
                    emptyState
                } else {
                    transactionList
                }
            }
            .background(Color.aegisBg)
            .navigationTitle("Transactions")
            .navigationBarTitleDisplayMode(.large)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        showFilters.toggle()
                    } label: {
                        Image(systemName: vm.hasActiveFilters ? "line.3.horizontal.decrease.circle.fill" : "line.3.horizontal.decrease.circle")
                            .foregroundStyle(vm.hasActiveFilters ? Color.aegisPrimary : Color.aegisTextMuted)
                    }
                }
            }
            .sheet(isPresented: $showFilters) {
                FilterSheet(vm: vm)
                    .presentationDetents([.medium])
            }
            .overlay(alignment: .top) {
                if let error = vm.errorMessage {
                    Text(error)
                        .font(.aegisCaption)
                        .foregroundStyle(.white)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 8)
                        .background(Color.aegisDanger)
                        .clipShape(Capsule())
                        .padding(.top, 4)
                        .transition(.opacity)
                }
            }
            .animation(.easeInOut, value: vm.errorMessage)
            .task { await vm.loadInitial() }
            .refreshable { await vm.loadInitial() }
        }
    }

    // ── Transaction list ──────────────────────────────────────────────────────

    private var transactionList: some View {
        ScrollView {
            LazyVStack(spacing: 0, pinnedViews: []) {
                // Active filter chips
                if vm.hasActiveFilters {
                    activeFilterChips
                        .padding(.horizontal, 16)
                        .padding(.top, 8)
                }

                AegisCard(padding: 0) {
                    VStack(spacing: 0) {
                        ForEach(vm.transactions) { tx in
                            NavigationLink(
                                destination: TransactionDetailView(transactionId: tx.id)
                            ) {
                                AegisTransactionRow(
                                    transaction:   tx,
                                    currentUserId: authStore.currentUser?.id ?? ""
                                )
                            }
                            .buttonStyle(.plain)

                            if tx.id != vm.transactions.last?.id {
                                Divider()
                                    .padding(.leading, 70)
                                    .foregroundStyle(Color.aegisBorder)
                            }
                        }

                        // Infinite scroll trigger
                        if vm.hasMore {
                            ProgressView()
                                .padding()
                                .task { await vm.loadNextPage() }
                        } else {
                            Text("All transactions loaded")
                                .font(.aegisCaption)
                                .foregroundStyle(Color.aegisTextSubtle)
                                .padding()
                        }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            }
        }
    }

    // ── Active filter chips ───────────────────────────────────────────────────

    private var activeFilterChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                if let status = vm.statusFilter {
                    FilterChip(label: status.displayLabel) { vm.statusFilter = nil }
                }
                if let from = vm.fromDate {
                    FilterChip(label: "From \(shortDate(from))") { vm.fromDate = nil }
                }
                if let to = vm.toDate {
                    FilterChip(label: "To \(shortDate(to))") { vm.toDate = nil }
                }
                Button {
                    vm.clearFilters()
                } label: {
                    Text("Clear all")
                        .font(.aegisCaption)
                        .foregroundStyle(Color.aegisTextMuted)
                }
            }
        }
    }

    // ── Skeleton / empty state ────────────────────────────────────────────────

    private var listSkeleton: some View {
        ScrollView {
            AegisCard(padding: 0) {
                VStack(spacing: 0) {
                    ForEach(0..<10, id: \.self) { _ in
                        TransactionRowSkeleton()
                        Divider().foregroundStyle(Color.aegisBorder)
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 12)
        }
    }

    private var emptyState: some View {
        VStack(spacing: 16) {
            Image(systemName: vm.hasActiveFilters ? "magnifyingglass" : "tray.fill")
                .font(.system(size: 52))
                .foregroundStyle(Color.aegisTextSubtle)
            Text(vm.hasActiveFilters ? "No matching transactions" : "No transactions yet")
                .font(.aegisHeadline)
                .foregroundStyle(Color.aegisText)
            if vm.hasActiveFilters {
                Button("Clear filters") { vm.clearFilters() }
                    .aegisButtonStyle(.secondary)
            } else {
                Text("Send money to get started")
                    .font(.aegisBody)
                    .foregroundStyle(Color.aegisTextMuted)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func shortDate(_ date: Date) -> String {
        date.formatted(.dateTime.day().month(.abbreviated))
    }
}

// ── Filter chip ───────────────────────────────────────────────────────────────

private struct FilterChip: View {
    let label:   String
    let onRemove: () -> Void

    var body: some View {
        HStack(spacing: 4) {
            Text(label)
                .font(.aegisCaption)
                .foregroundStyle(Color.aegisPrimary)
            Button(action: onRemove) {
                Image(systemName: "xmark")
                    .font(.system(size: 9, weight: .bold))
                    .foregroundStyle(Color.aegisPrimary)
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 5)
        .background(Color.aegisPrimaryLight)
        .clipShape(Capsule())
    }
}

// ── Filter bottom sheet ───────────────────────────────────────────────────────

private struct FilterSheet: View {
    @ObservedObject var vm: TransactionListViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section("Status") {
                    Picker("Status", selection: $vm.statusFilter) {
                        Text("Any").tag(TransactionStatus?.none)
                        ForEach(TransactionStatus.allCases, id: \.self) { status in
                            Text(status.displayLabel).tag(TransactionStatus?.some(status))
                        }
                    }
                    .pickerStyle(.menu)
                }

                Section("Date range") {
                    DatePicker(
                        "From",
                        selection: Binding(
                            get:  { vm.fromDate ?? Date() },
                            set:  { vm.fromDate = $0 }
                        ),
                        in: ...( vm.toDate ?? Date()),
                        displayedComponents: .date
                    )
                    .onChange(of: vm.fromDate) { _, _ in }

                    DatePicker(
                        "To",
                        selection: Binding(
                            get:  { vm.toDate ?? Date() },
                            set:  { vm.toDate = $0 }
                        ),
                        in: (vm.fromDate ?? .distantPast)...,
                        displayedComponents: .date
                    )
                }

                if vm.hasActiveFilters {
                    Section {
                        Button("Clear all filters", role: .destructive) {
                            vm.clearFilters()
                        }
                    }
                }
            }
            .navigationTitle("Filters")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }
}
