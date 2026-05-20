import Foundation

// ── Session model ─────────────────────────────────────────────────────────────

struct TriageSession: Identifiable {
    let id          = UUID()
    let serviceName: String
    let description: String
    let analysis:    String
    var degraded:    Bool    { analysis.hasPrefix("⚠") }
    let timestamp:   Date    = .now
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

@MainActor
final class TriageViewModel: ObservableObject {

    // ── Form state ────────────────────────────────────────────────────────────

    @Published var serviceName  = ""
    @Published var description  = ""

    // ── Loading & error ───────────────────────────────────────────────────────

    @Published private(set) var isTriaging  = false
    @Published private(set) var triageError: String? = nil

    // ── Session history ───────────────────────────────────────────────────────

    @Published private(set) var sessions:    [TriageSession] = []
    @Published var             expandedId:   UUID?           = nil

    private let aiService = AiService()

    // ── Computed ──────────────────────────────────────────────────────────────

    var canTriage: Bool {
        !serviceName.trimmingCharacters(in: .whitespaces).isEmpty &&
        !description.trimmingCharacters(in: .whitespaces).isEmpty
    }

    // ── Prefill from transaction context ─────────────────────────────────────

    func prefill(transactionId: String?, service: String?) {
        if let svc = service, !svc.isEmpty { serviceName = svc }
        if let txId = transactionId, description.isEmpty {
            description = "Transaction \(txId) failed. Investigate root cause and recommend mitigation."
        }
    }

    // ── Run triage ────────────────────────────────────────────────────────────

    func runTriage() async {
        guard canTriage else { return }
        isTriaging  = true
        triageError = nil

        do {
            let analysis = try await aiService.triageIncident(
                serviceName:         serviceName.trimmingCharacters(in: .whitespaces),
                incidentDescription: description.trimmingCharacters(in: .whitespaces)
            )
            let session = TriageSession(
                serviceName: serviceName.trimmingCharacters(in: .whitespaces),
                description: description.trimmingCharacters(in: .whitespaces),
                analysis:    analysis
            )
            sessions.insert(session, at: 0)
            expandedId = session.id
        } catch {
            triageError = error.localizedDescription
        }
        isTriaging = false
    }

    // ── Session management ────────────────────────────────────────────────────

    func toggleExpanded(_ id: UUID) {
        expandedId = (expandedId == id) ? nil : id
    }

    func clearSessions() {
        sessions   = []
        expandedId = nil
    }
}
