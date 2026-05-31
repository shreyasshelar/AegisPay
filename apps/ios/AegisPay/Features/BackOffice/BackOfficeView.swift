import SwiftUI

private let adminRoles: Set<String> = ["ADMIN"]

// ── Back-office root view ─────────────────────────────────────────────────────

struct BackOfficeView: View {
    @StateObject private var vm = BackOfficeViewModel()
    @EnvironmentObject var authStore: AuthStore
    @State private var showTriageSheet = false

    private var isAdminUser: Bool {
        adminRoles.contains(authStore.currentUser?.role ?? "")
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // ── Segmented tab picker ──────────────────────────────────────
                Picker("Tab", selection: $vm.selectedTab) {
                    ForEach(BackOfficeTab.allCases) { tab in
                        Label(tab.label, systemImage: tab.systemImage).tag(tab)
                    }
                }
                .pickerStyle(.segmented)
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
                .background(Color.aegisBg)

                Divider()

                // ── Tab content ───────────────────────────────────────────────
                switch vm.selectedTab {
                case .riskCases:
                    RiskCasesTab(vm: vm)
                case .incidentTriage:
                    IncidentTriageTab(
                        vm: vm,
                        onOpenFullTriage: isAdminUser ? { showTriageSheet = true } : nil
                    )
                }
            }
            .background(Color.aegisBg)
            .navigationTitle("Back Office")
            .navigationBarTitleDisplayMode(.large)
            .toolbar {
                if vm.selectedTab == .riskCases {
                    ToolbarItem(placement: .navigationBarTrailing) {
                        Button { Task { await vm.loadRiskCases() } } label: {
                            Image(systemName: "arrow.clockwise")
                                .rotationEffect(vm.isLoadingCases ? .degrees(360) : .zero)
                                .animation(vm.isLoadingCases
                                    ? .linear(duration: 0.8).repeatForever(autoreverses: false)
                                    : .default,
                                    value: vm.isLoadingCases)
                        }
                    }
                }
            }
        }
        // Full-screen triage sheet (ADMIN only)
        .sheet(isPresented: $showTriageSheet) {
            TriageView()
                .environmentObject(authStore)
        }
    }
}

// ── Risk cases tab ────────────────────────────────────────────────────────────

private struct RiskCasesTab: View {
    @ObservedObject var vm: BackOfficeViewModel

    var body: some View {
        if vm.isLoadingCases {
            ProgressView().padding(.top, 60)
        } else if let err = vm.casesError {
            ContentUnavailableView(
                "Could not load cases",
                systemImage: "exclamationmark.triangle",
                description: Text(err)
            )
        } else if vm.cases.isEmpty {
            ContentUnavailableView(
                "No risk cases",
                systemImage: "checkmark.shield",
                description: Text("The queue is empty — all clear.")
            )
        } else {
            List(vm.cases) { rc in
                RiskCaseRow(riskCase: rc, isSelected: vm.selectedCase?.id == rc.id)
                    .onTapGesture { vm.selectCase(rc) }
                    .sheet(isPresented: Binding(
                        get: { vm.selectedCase?.id == rc.id },
                        set: { if !$0 { vm.selectedCase = nil } }
                    )) {
                        RiskCaseDetailSheet(vm: vm, riskCase: rc)
                            .presentationDetents([.medium, .large])
                    }
            }
            .listStyle(.plain)
        }
    }
}

// ── Risk case list row ────────────────────────────────────────────────────────

private struct RiskCaseRow: View {
    let riskCase:   RiskCase
    let isSelected: Bool

    var body: some View {
        HStack(spacing: 12) {
            // Score ring
            ZStack {
                Circle()
                    .stroke(decisionColor.opacity(0.2), lineWidth: 3)
                Circle()
                    .trim(from: 0, to: CGFloat(riskCase.riskScore) / 100)
                    .stroke(decisionColor, style: StrokeStyle(lineWidth: 3, lineCap: .round))
                    .rotationEffect(.degrees(-90))
                Text("\(riskCase.riskScore)")
                    .font(.system(size: 11, weight: .bold, design: .rounded))
                    .foregroundStyle(decisionColor)
            }
            .frame(width: 44, height: 44)

            VStack(alignment: .leading, spacing: 3) {
                Text("Tx \(riskCase.transactionId.prefix(8))…")
                    .font(.subheadline).fontWeight(.semibold)
                    .foregroundStyle(Color.aegisText)
                if !riskCase.ruleFlagKeys.isEmpty {
                    Text(riskCase.ruleFlagKeys.joined(separator: " · "))
                        .font(.caption2)
                        .foregroundStyle(Color.aegisTextMuted)
                        .lineLimit(1)
                }
            }

            Spacer()

            DecisionBadge(decision: riskCase.decision)
        }
        .padding(.vertical, 4)
        .listRowBackground(isSelected ? Color.aegisPrimaryLight : Color.clear)
    }

    private var decisionColor: Color {
        switch riskCase.decision {
        case .approved: return .aegisSuccess
        case .review:   return .aegisWarning
        case .rejected: return .aegisDanger
        }
    }
}

// ── Risk case detail sheet ────────────────────────────────────────────────────

private struct RiskCaseDetailSheet: View {
    @ObservedObject var vm: BackOfficeViewModel
    let riskCase: RiskCase

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {

                    // Header
                    HStack {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Transaction")
                                .font(.caption).foregroundStyle(Color.aegisTextMuted)
                            Text(riskCase.transactionId)
                                .font(.system(.caption, design: .monospaced))
                                .foregroundStyle(Color.aegisText)
                        }
                        Spacer()
                        DecisionBadge(decision: riskCase.decision)
                    }
                    .padding()
                    .background(Color.aegisSurface)
                    .clipShape(RoundedRectangle(cornerRadius: 12))

                    // Score + flags
                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            Label("Risk Score", systemImage: "gauge.with.needle")
                                .font(.subheadline).fontWeight(.semibold)
                            Spacer()
                            Text("\(riskCase.riskScore) / 100")
                                .font(.subheadline).fontWeight(.bold)
                                .foregroundStyle(scoreColor)
                        }

                        // Score bar
                        GeometryReader { geo in
                            ZStack(alignment: .leading) {
                                RoundedRectangle(cornerRadius: 4)
                                    .fill(Color.aegisBorder)
                                    .frame(height: 8)
                                RoundedRectangle(cornerRadius: 4)
                                    .fill(scoreColor)
                                    .frame(width: geo.size.width * CGFloat(riskCase.riskScore) / 100, height: 8)
                            }
                        }
                        .frame(height: 8)

                        if !riskCase.ruleFlagKeys.isEmpty {
                            Text("Triggered Rules")
                                .font(.caption).foregroundStyle(Color.aegisTextMuted)
                            FlowLayout(spacing: 6) {
                                ForEach(riskCase.ruleFlagKeys, id: \.self) { rule in
                                    Text(rule)
                                        .font(.system(size: 11, design: .monospaced))
                                        .padding(.horizontal, 8).padding(.vertical, 3)
                                        .background(Color.aegisDangerLight)
                                        .foregroundStyle(Color.aegisDanger)
                                        .clipShape(Capsule())
                                }
                            }
                        }
                    }
                    .padding()
                    .background(Color.aegisSurface)
                    .clipShape(RoundedRectangle(cornerRadius: 12))

                    // AI explain button
                    if !riskCase.ruleFlagKeys.isEmpty {
                        Button {
                            Task { await vm.explainFraud() }
                        } label: {
                            HStack {
                                if vm.isExplaining {
                                    ProgressView().tint(.white)
                                } else {
                                    Image(systemName: "sparkles")
                                }
                                Text(vm.isExplaining ? "Asking AI…" : "AI Fraud Explanation")
                                    .fontWeight(.semibold)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .background(Color.aegisPrimary)
                            .foregroundStyle(.white)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                        }
                        .disabled(vm.isExplaining)
                    }

                    // AI explanation result
                    if let explanation = vm.explanation {
                        VStack(alignment: .leading, spacing: 8) {
                            Label("AI Explanation", systemImage: "sparkles")
                                .font(.caption)
                                .fontWeight(.semibold)
                                .foregroundStyle(Color.aegisTextMuted)
                            Text(explanation.explanation)
                                .font(.subheadline)
                                .foregroundStyle(Color.aegisText)
                                .lineSpacing(4)
                        }
                        .padding()
                        .background(Color.aegisPrimaryLight)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                    }

                    if let err = vm.explainError {
                        Label(err, systemImage: "exclamationmark.triangle")
                            .font(.caption).foregroundStyle(Color.aegisDanger)
                    }
                }
                .padding()
            }
            .background(Color.aegisBg)
            .navigationTitle("Case Detail")
            .navigationBarTitleDisplayMode(.inline)
        }
    }

    private var scoreColor: Color {
        riskCase.riskScore >= 70 ? .aegisDanger
            : riskCase.riskScore >= 30 ? .aegisWarning
            : .aegisSuccess
    }
}

// ── Incident triage tab ───────────────────────────────────────────────────────

private struct IncidentTriageTab: View {
    @ObservedObject var vm: BackOfficeViewModel
    /// Non-nil when the signed-in user is ADMIN — tapping opens the full TriageView.
    var onOpenFullTriage: (() -> Void)? = nil

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {

                // ── Input form ────────────────────────────────────────────────
                VStack(alignment: .leading, spacing: 14) {
                    HStack {
                        Label("New Incident", systemImage: "terminal")
                            .font(.subheadline).fontWeight(.semibold)
                            .foregroundStyle(Color.aegisText)
                        Spacer()
                        // ADMIN-only shortcut to the dedicated full-screen triage view
                        if let openFullTriage = onOpenFullTriage {
                            Button(action: openFullTriage) {
                                Label("Full Agent", systemImage: "arrow.up.right.square")
                                    .font(.caption).fontWeight(.medium)
                                    .foregroundStyle(Color.aegisPrimary)
                            }
                        }
                    }

                    VStack(alignment: .leading, spacing: 6) {
                        Text("Affected Service")
                            .font(.caption).foregroundStyle(Color.aegisTextMuted)
                        TextField("e.g. transaction-service", text: $vm.serviceName)
                            .textFieldStyle(.plain)
                            .padding(10)
                            .background(Color.aegisBg)
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                            .overlay(
                                RoundedRectangle(cornerRadius: 8)
                                    .stroke(Color.aegisBorder)
                            )
                    }

                    VStack(alignment: .leading, spacing: 6) {
                        Text("Incident Description")
                            .font(.caption).foregroundStyle(Color.aegisTextMuted)
                        TextEditor(text: $vm.incidentDesc)
                            .frame(minHeight: 100)
                            .padding(8)
                            .background(Color.aegisBg)
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                            .overlay(
                                RoundedRectangle(cornerRadius: 8)
                                    .stroke(Color.aegisBorder)
                            )
                    }

                    Button {
                        Task { await vm.runTriage() }
                    } label: {
                        HStack {
                            if vm.isTriaging {
                                ProgressView().tint(.white)
                            } else {
                                Image(systemName: "sparkles")
                            }
                            Text(vm.isTriaging ? "Triaging…" : "Run AI Triage")
                                .fontWeight(.semibold)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(vm.canTriage ? Color.aegisPrimary : Color.aegisBorder)
                        .foregroundStyle(.white)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                    }
                    .disabled(!vm.canTriage || vm.isTriaging)
                }
                .padding()
                .background(Color.aegisSurface)
                .clipShape(RoundedRectangle(cornerRadius: 16))
                .shadow(color: .black.opacity(0.04), radius: 6, y: 2)

                // ── Triage report — rendered as Markdown ──────────────────────
                if let report = vm.triageReport {
                    VStack(alignment: .leading, spacing: 8) {
                        Label("Triage Report", systemImage: "checkmark.shield")
                            .font(.subheadline).fontWeight(.semibold)
                            .foregroundStyle(Color.aegisText)
                        AegisMarkdownView(markdown: report)
                            .padding()
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color(hex: "#F8FAFC"))           // slate-50
                            .clipShape(RoundedRectangle(cornerRadius: 12))

                        Button("Clear", role: .destructive) { vm.resetTriage() }
                            .font(.caption)
                    }
                    .padding()
                    .background(Color.aegisSurface)
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                    .shadow(color: .black.opacity(0.04), radius: 6, y: 2)
                }

                if let err = vm.triageError {
                    Label(err, systemImage: "exclamationmark.triangle")
                        .font(.caption).foregroundStyle(Color.aegisDanger)
                        .padding(.horizontal)
                }
            }
            .padding()
        }
        .background(Color.aegisBg)
    }
}

// ── Shared sub-components ─────────────────────────────────────────────────────

private struct DecisionBadge: View {
    let decision: RiskDecision

    var body: some View {
        Text(decision.displayLabel.uppercased())
            .font(.system(size: 10, weight: .bold))
            .padding(.horizontal, 8).padding(.vertical, 3)
            .background(bgColor)
            .foregroundStyle(fgColor)
            .clipShape(Capsule())
    }

    private var bgColor: Color {
        switch decision {
        case .approved: return .aegisSuccessLight
        case .review:   return .aegisWarningLight
        case .rejected: return .aegisDangerLight
        }
    }
    private var fgColor: Color {
        switch decision {
        case .approved: return .aegisSuccess
        case .review:   return .aegisWarning
        case .rejected: return .aegisDanger
        }
    }
}

/// Simple horizontal-wrapping flow layout for rule-flag chips.
private struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        var x: CGFloat = 0, y: CGFloat = 0, rowH: CGFloat = 0

        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if x + size.width > maxWidth, x > 0 {
                y += rowH + spacing
                x  = 0
                rowH = 0
            }
            x    += size.width + spacing
            rowH  = max(rowH, size.height)
        }
        return CGSize(width: maxWidth, height: y + rowH)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let maxWidth = bounds.width
        var x = bounds.minX, y = bounds.minY, rowH: CGFloat = 0

        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if x + size.width > bounds.maxX, x > bounds.minX {
                y += rowH + spacing
                x  = bounds.minX
                rowH = 0
            }
            view.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
            x    += size.width + spacing
            rowH  = max(rowH, size.height)
        }
    }
}
