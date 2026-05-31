import SwiftUI

/// Renders Markdown text using iOS 15+ `AttributedString(markdown:)`.
///
/// Handles the subset produced by the AegisPay AI platform:
///   # H1 / ## H2 / ### H3   — headings
///   **text**                 — bold inline
///   `code`                   — inline monospace
///   - item / * item          — bullet list items
///   Regular paragraphs and blank lines
///
/// Mirrors the `MarkdownText` composable on Android and the
/// `<ReactMarkdown>` prose blocks added to the web triage screens.
struct AegisMarkdownView: View {

    let markdown: String

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            ForEach(Array(lines.enumerated()), id: \.offset) { _, line in
                lineView(for: line)
            }
        }
    }

    private var lines: [String] { markdown.components(separatedBy: "\n") }

    @ViewBuilder
    private func lineView(for raw: String) -> some View {
        let line = raw.trimmingCharacters(in: .newlines)

        if line.hasPrefix("# ") {
            Text(stripPrefix("# ", from: line))
                .font(.title3).fontWeight(.bold)
                .foregroundStyle(Color.aegisText)
                .padding(.top, 8)

        } else if line.hasPrefix("## ") {
            Text(stripPrefix("## ", from: line))
                .font(.headline).fontWeight(.semibold)
                .foregroundStyle(Color.aegisText)
                .padding(.top, 6)

        } else if line.hasPrefix("### ") {
            Text(stripPrefix("### ", from: line))
                .font(.subheadline).fontWeight(.semibold)
                .foregroundStyle(Color.aegisText)
                .padding(.top, 4)

        } else if line.hasPrefix("- ") || line.hasPrefix("* ") {
            HStack(alignment: .top, spacing: 6) {
                Text("•")
                    .font(.caption)
                    .foregroundStyle(Color.aegisText)
                    .padding(.top, 2)
                inlineText(stripPrefix(line.hasPrefix("- ") ? "- " : "* ", from: line))
                    .font(.callout)
                    .foregroundStyle(Color.aegisText)
            }

        } else if line == "---" || line.isEmpty {
            Spacer().frame(height: 4)

        } else {
            inlineText(line)
                .font(.callout)
                .foregroundStyle(Color.aegisText)
        }
    }

    /// Renders **bold** and `inline code` using AttributedString(markdown:).
    @ViewBuilder
    private func inlineText(_ text: String) -> some View {
        if #available(iOS 15, *),
           let attributed = try? AttributedString(
                markdown: text,
                options: AttributedString.MarkdownParsingOptions(
                    interpretedSyntax: .inlineOnlyPreservingWhitespace
                )
           ) {
            Text(attributed)
        } else {
            Text(text)
        }
    }

    private func stripPrefix(_ prefix: String, from string: String) -> String {
        guard string.hasPrefix(prefix) else { return string }
        return String(string.dropFirst(prefix.count))
    }
}
