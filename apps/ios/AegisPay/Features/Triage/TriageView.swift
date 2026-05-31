import SwiftUI

// ── Screen ────────────────────────────────────────────────────────────────────

struct TriageView: View {
    /// Optional pre-fill from a failed-transaction context.
    var prefillTransactionId: String? = nil
    var prefillService:       String? = nil

    @StateObject private var vm = TriageViewModel()

    private let timeFmt: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "HH:mm:ss"
        return f
    }()

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    inputCard
                    if !vm.sessions.isEmpty { sessionHistory }
                }
                .padding()
            }
            .background(Color.aegisBg)
            .navigationTitle("AI Triage Agent")
            .navigationBarTitleDisplayMode(.large)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    VStack(spacing: 1) {
                        Text("AI Triage Agent")
                            .font(.headline).fontWeight(.bold)
                        Text("ADMIN only")
                            .font(.caption2)
                            .foregroundStyle(Color.aegisTextMuted)
                    }
                }
                if !vm.sessions.isEmpty {
                    ToolbarItem(placement: .navigationBarTrailing) {
                        Button(role: .destructive) {
                            vm.clearSessions()
                        } label: {
                            Image(systemName: "trash.slash")
                                .foregroundStyle(Color.aegisDanger)
                        }
                        .accessibilityLabel("Clear session history")
                    }
                }
            }
        }
        .task {
            vm.prefill(transactionId: prefillTransactionId, service: prefillService)
        }
    }

    // ── Input card ────────────────────────────────────────────────────────────

    private var inputCard: some View {
        VStack(alignment: .leading, spacing: 14) {

            Label("New Triage Request", systemImage: "terminal")
                .font(.subheadline).fontWeight(.semibold)
                .foregroundStyle(Color.aegisText)

            // Amber pre-fill banner
            if let txId = prefillTransactionId {
                HStack(alignment: .top, spacing: 8) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .font(.caption).foregroundStyle(Color(hex: "#B45309"))
                    Text("Pre-filled from transaction \(txId)")
                        .font(.caption)
                        .foregroundStyle(Color(hex: "#92400E"))
                }
                .padding(10)
                .background(Color(hex: "#FFFBEB"))
                .clipShape(RoundedRectangle(cornerRadius: 8))
            }

            // Service name
            VStack(alignment: .leading, spacing: 6) {
                Text("Affected Service")
                    .font(.caption).foregroundStyle(Color.aegisTextMuted)
                TextField("e.g. transaction-service", text: $vm.serviceName)
                    .textFieldStyle(.plain)
                    .padding(10)
                    .background(Color.aegisBg)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.aegisBorder))
            }

            // Description
            VStack(alignment: .leading, spacing: 6) {
                Text("Incident Description")
                    .font(.caption).foregroundStyle(Color.aegisTextMuted)
                TextEditor(text: $vm.description)
                    .frame(minHeight: 90)
                    .padding(8)
                    .background(Color.aegisBg)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.aegisBorder))
            }

            // Error
            if let err = vm.triageError {
                Label(err, systemImage: "exclamationmark.circle")
                    .font(.caption).foregroundStyle(Color.aegisDanger)
            }

            // Run button
            Button {
                Task { await vm.runTriage() }
            } label: {
                HStack(spacing: 8) {
                    if vm.isTriaging {
                        ProgressView().tint(.white).scaleEffect(0.85)
                        Text("Agent is investigating…")
                            .fontWeight(.semibold)
                    } else {
                        Image(systemName: "sparkles")
                        Text("Run AI Triage")
                            .fontWeight(.semibold)
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 13)
                .background(vm.canTriage && !vm.isTriaging ? Color.aegisPrimary : Color.aegisBorder)
                .foregroundStyle(.white)
                .clipShape(RoundedRectangle(cornerRadius: 12))
            }
            .disabled(!vm.canTriage || vm.isTriaging)
        }
        .padding()
        .background(Color.aegisSurface)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .shadow(color: .black.opacity(0.04), radius: 6, y: 2)
    }

    // ── Session history ───────────────────────────────────────────────────────

    private var sessionHistory: some View {
        VStack(alignment: .leading, spacing: 10) {

            HStack(spacing: 6) {
                Image(systemName: "clock.arrow.circlepath")
                    .font(.caption).foregroundStyle(Color.aegisTextMuted)
                Text("Session History (\(vm.sessions.count))")
                    .font(.subheadline).fontWeight(.semibold)
                    .foregroundStyle(Color.aegisText)
            }

            ForEach(vm.sessions) { session in
                TriageSessionCard(
                    session:  session,
                    expanded: vm.expandedId == session.id,
                    timeFmt:  timeFmt,
                    onToggle: { vm.toggleExpanded(session.id) }
                )
            }
        }
    }
}

// ── Session card ──────────────────────────────────────────────────────────────

private struct TriageSessionCard: View {
    let session:  TriageSession
    let expanded: Bool
    let timeFmt:  DateFormatter
    let onToggle: () -> Void

    var body: some View {
        VStack(spacing: 0) {

            // ── Header row ────────────────────────────────────────────────────
            Button(action: onToggle) {
                HStack(spacing: 10) {
                    Image(systemName: "stethoscope")
                        .font(.subheadline)
                        .foregroundStyle(session.degraded ? Color(hex: "#F59E0B") : Color.aegisPrimary)

                    VStack(alignment: .leading, spacing: 2) {
                        Text(session.serviceName)
                            .font(.subheadline).fontWeight(.semibold)
                            .foregroundStyle(Color.aegisText)
                        Text(session.description.prefix(80) + (session.description.count > 80 ? "…" : ""))
                            .font(.caption)
                            .foregroundStyle(Color.aegisTextMuted)
                            .lineLimit(1)
                    }

                    Spacer()

                    VStack(alignment: .trailing, spacing: 3) {
                        if session.degraded {
                            Text("DEGRADED")
                                .font(.system(size: 9, weight: .bold))
                                .foregroundStyle(Color(hex: "#B45309"))
                        }
                        Text(timeFmt.string(from: session.timestamp))
                            .font(.caption2)
                            .foregroundStyle(Color.aegisTextMuted)
                        Image(systemName: expanded ? "chevron.up" : "chevron.down")
                            .font(.caption2)
                            .foregroundStyle(Color.aegisTextMuted)
                    }
                }
                .padding(14)
            }
            .buttonStyle(.plain)

            // ── Analysis — rendered as Markdown ──────────────────────────────
            // Uses iOS 15+ AttributedString(markdown:) — mirrors web ReactMarkdown.
            if expanded {
                AegisMarkdownView(markdown: session.analysis)
                    .padding(14)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color(hex: "#F8FAFC"))             // slate-50
                    .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
        .background(Color.aegisSurface)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .shadow(color: .black.opacity(0.04), radius: 6, y: 2)
        .animation(.easeInOut(duration: 0.2), value: expanded)
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

#Preview {
    TriageView()
        .environmentObject(AuthStore())
}
