import SwiftUI
import SafariServices

// ── SFSafariViewController SwiftUI bridge ────────────────────────────────────

struct SafariView: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context: Context) -> SFSafariViewController {
        let config = SFSafariViewController.Configuration()
        config.entersReaderIfAvailable = false
        let vc = SFSafariViewController(url: url, configuration: config)
        vc.preferredControlTintColor = UIColor(Color.aegisPrimary)
        vc.dismissButtonStyle = .done
        return vc
    }

    func updateUIViewController(_ uiViewController: SFSafariViewController, context: Context) {}
}

// ── Docs landing view (shown in-app as a tab) ─────────────────────────────────

struct DocsView: View {
    @State private var showSafari = false
    @State private var safariURL  = AppConfig.docsURL

    private let sections: [(icon: String, title: String, subtitle: String, path: String)] = [
        ("🏗️", "Architecture",      "Service topology, tech stack, ports",      "architecture"),
        ("🔄", "Transaction Flow",  "End-to-end saga with Stripe",              "flows"),
        ("📐", "Patterns",          "Outbox, CQRS, double-entry ledger",        "patterns"),
        ("🤖", "AI Platform",       "RAG pipeline, triage agent, KYC",          "ai"),
        ("🔐", "Security",          "OAuth2/PKCE, RBAC, Vault, rate limiting",  "security"),
        ("📊", "Observability",     "Prometheus rules, Grafana, ClickHouse",    "observability"),
        ("🏗️", "Infrastructure",   "Kubernetes, Helm, Argo CD, CI/CD",         "infrastructure"),
        ("⚙️", "Services",         "Per-service deep dives and Kafka topics",  "services"),
    ]

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 0) {
                    // Hero
                    VStack(spacing: 12) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 18)
                                .fill(Color.aegisPrimary.opacity(0.1))
                                .frame(width: 68, height: 68)
                            Image(systemName: "doc.text.fill")
                                .font(.system(size: 30, weight: .semibold))
                                .foregroundStyle(Color.aegisPrimary)
                        }
                        Text("Developer Docs")
                            .font(.aegisTitle)
                            .foregroundStyle(Color.aegisText)
                        Text("Interactive architecture docs, flow diagrams, and AI platform guides")
                            .font(.aegisBody)
                            .foregroundStyle(Color.aegisTextMuted)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 32)
                    }
                    .padding(.vertical, 28)

                    // Open full docs button
                    Button {
                        safariURL  = AppConfig.docsURL
                        showSafari = true
                    } label: {
                        HStack(spacing: 8) {
                            Image(systemName: "arrow.up.right.square")
                            Text("Open Full Docs")
                                .fontWeight(.semibold)
                        }
                    }
                    .aegisButtonStyle(.primary, fullWidth: true)
                    .padding(.horizontal, 20)

                    Divider()
                        .padding(.vertical, 20)
                        .padding(.horizontal, 20)

                    // Section grid
                    Text("Browse by section")
                        .font(.aegisCaption)
                        .foregroundStyle(Color.aegisTextSubtle)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 20)
                        .padding(.bottom, 12)

                    LazyVGrid(
                        columns: [GridItem(.flexible()), GridItem(.flexible())],
                        spacing: 12,
                    ) {
                        ForEach(sections, id: \.path) { section in
                            DocsSectionCard(
                                icon:     section.icon,
                                title:    section.title,
                                subtitle: section.subtitle,
                                onTap:    {
                                    safariURL  = AppConfig.webBaseURL
                                        .appendingPathComponent("docs")
                                        .appendingPathComponent(section.path)
                                    showSafari = true
                                },
                            )
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.bottom, 32)
                }
            }
            .toolbar(.hidden, for: .navigationBar)
        }
        .sheet(isPresented: $showSafari) {
            SafariView(url: safariURL)
                .ignoresSafeArea()
        }
    }
}

// ── Section card ─────────────────────────────────────────────────────────────

private struct DocsSectionCard: View {
    let icon:    String
    let title:   String
    let subtitle: String
    let onTap:   () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: 8) {
                Text(icon)
                    .font(.system(size: 28))
                Text(title)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Color.aegisText)
                    .lineLimit(1)
                Text(subtitle)
                    .font(.system(size: 11))
                    .foregroundStyle(Color.aegisTextMuted)
                    .lineLimit(2)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(14)
            .background(Color(.systemBackground))
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .stroke(Color(.separator).opacity(0.5), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }
}
