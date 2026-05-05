import Foundation

// ── Back-office tab ───────────────────────────────────────────────────────────

enum BackOfficeTab: Int, CaseIterable, Identifiable {
    case riskCases      = 0
    case incidentTriage = 1

    var id: Int { rawValue }

    var label: String {
        switch self {
        case .riskCases:      return "Risk Cases"
        case .incidentTriage: return "Incident Triage"
        }
    }

    var systemImage: String {
        switch self {
        case .riskCases:      return "shield.lefthalf.filled"
        case .incidentTriage: return "stethoscope"
        }
    }
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

@MainActor
final class BackOfficeViewModel: ObservableObject {

    // ── Navigation ────────────────────────────────────────────────────────────

    @Published var selectedTab: BackOfficeTab = .riskCases

    // ── Risk cases ────────────────────────────────────────────────────────────

    @Published private(set) var isLoadingCases = false
    @Published private(set) var cases:          [RiskCase]            = []
    @Published private(set) var casesError:     String?               = nil
    @Published var             selectedCase:    RiskCase?             = nil
    @Published private(set) var explanation:    FraudExplainResponse? = nil
    @Published private(set) var isExplaining    = false
    @Published private(set) var explainError:   String?               = nil

    // ── Incident triage ───────────────────────────────────────────────────────

    @Published var serviceName   = ""
    @Published var incidentDesc  = ""
    @Published private(set) var triageReport:  String? = nil
    @Published private(set) var isTriaging     = false
    @Published private(set) var triageError:   String? = nil

    // ── Services ──────────────────────────────────────────────────────────────

    private let riskService = RiskService()
    private let aiService   = AiService()

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    init() { Task { await loadRiskCases() } }

    // ── Risk cases ────────────────────────────────────────────────────────────

    func loadRiskCases() async {
        isLoadingCases = true
        casesError     = nil
        do {
            cases = try await riskService.listRiskCases()
        } catch {
            casesError = error.localizedDescription
        }
        isLoadingCases = false
    }

    func selectCase(_ rc: RiskCase) {
        selectedCase = rc
        explanation  = nil
        explainError = nil
    }

    func explainFraud() async {
        guard let rc = selectedCase, !rc.ruleFlagKeys.isEmpty else { return }
        isExplaining = true
        explainError = nil
        do {
            explanation = try await aiService.explainFraud(
                FraudExplainRequest(
                    transactionId: rc.transactionId,
                    riskScore:     rc.riskScore,
                    flaggedRules:  rc.ruleFlagKeys
                )
            )
        } catch {
            explainError = error.localizedDescription
        }
        isExplaining = false
    }

    // ── Incident triage ───────────────────────────────────────────────────────

    var canTriage: Bool { !serviceName.trimmingCharacters(in: .whitespaces).isEmpty &&
                          !incidentDesc.trimmingCharacters(in: .whitespaces).isEmpty }

    func runTriage() async {
        guard canTriage else { return }
        isTriaging  = true
        triageReport = nil
        triageError  = nil
        do {
            triageReport = try await aiService.triageIncident(
                serviceName:         serviceName.trimmingCharacters(in: .whitespaces),
                incidentDescription: incidentDesc.trimmingCharacters(in: .whitespaces)
            )
        } catch {
            triageError = error.localizedDescription
        }
        isTriaging = false
    }

    func resetTriage() {
        serviceName  = ""
        incidentDesc = ""
        triageReport = nil
        triageError  = nil
    }
}
