import SwiftUI

@MainActor
final class TransactionListViewModel: ObservableObject {

    @Published private(set) var transactions:   [Transaction] = []
    @Published private(set) var isLoading       = false
    @Published private(set) var isFetchingMore  = false
    @Published private(set) var hasMore         = true
    @Published private(set) var errorMessage:   String?

    // ── Filters (changing any filter resets to page 0) ────────────────────────
    @Published var statusFilter: TransactionStatus? = nil {
        didSet { Task { await loadInitial() } }
    }
    @Published var fromDate: Date? = nil {
        didSet { Task { await loadInitial() } }
    }
    @Published var toDate: Date? = nil {
        didSet { Task { await loadInitial() } }
    }

    private let service     = TransactionService()
    private var currentPage = 0
    private let pageSize    = 20

    // ── Load ──────────────────────────────────────────────────────────────────

    func loadInitial() async {
        guard !isLoading else { return }
        isLoading    = true
        errorMessage = nil
        currentPage  = 0
        do {
            let page = try await service.list(
                page:     0,
                size:     pageSize,
                status:   statusFilter?.rawValue,
                fromDate: fromDate.map(iso8601Start),
                toDate:   toDate.map(iso8601End)
            )
            transactions = page.content
            hasMore      = !page.last
            currentPage  = 1
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }

    func loadNextPage() async {
        guard hasMore, !isFetchingMore, !isLoading else { return }
        isFetchingMore = true
        do {
            let page = try await service.list(
                page:     currentPage,
                size:     pageSize,
                status:   statusFilter?.rawValue,
                fromDate: fromDate.map(iso8601Start),
                toDate:   toDate.map(iso8601End)
            )
            transactions.append(contentsOf: page.content)
            hasMore     = !page.last
            currentPage += 1
        } catch {
            errorMessage = error.localizedDescription
        }
        isFetchingMore = false
    }

    func clearFilters() {
        statusFilter = nil
        fromDate     = nil
        toDate       = nil
    }

    var hasActiveFilters: Bool {
        statusFilter != nil || fromDate != nil || toDate != nil
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private let isoFormatter: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime]
        return f
    }()

    private func iso8601Start(_ date: Date) -> String {
        var cal = Calendar.current
        let startOfDay = cal.startOfDay(for: date)
        return isoFormatter.string(from: startOfDay)
    }

    private func iso8601End(_ date: Date) -> String {
        var cal = Calendar.current
        var comps = DateComponents()
        comps.day    = 1
        comps.second = -1
        let end = cal.date(byAdding: comps, to: cal.startOfDay(for: date)) ?? date
        return isoFormatter.string(from: end)
    }
}
