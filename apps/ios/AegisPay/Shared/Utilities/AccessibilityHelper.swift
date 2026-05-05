import SwiftUI

/// Convenience View modifiers for consistent, app-wide accessibility labelling.
/// Apply these to any view instead of reaching for raw `.accessibilityLabel`
/// calls so that labelling conventions stay uniform across the codebase.
extension View {

    // MARK: — Card

    /// Groups the view's children into a single accessibility element and
    /// labels it as a card.
    ///
    /// - Parameter label: The VoiceOver label for the combined element.
    func aegisCard(label: String) -> some View {
        self
            .accessibilityElement(children: .combine)
            .accessibilityLabel(label)
    }

    // MARK: — Button

    /// Annotates the view as an interactive button with an optional hint.
    ///
    /// - Parameters:
    ///   - label: The VoiceOver label.
    ///   - hint:  An optional hint describing the action (default: empty).
    func aegisButton(label: String, hint: String = "") -> some View {
        self
            .accessibilityLabel(label)
            .accessibilityHint(hint)
            .accessibilityAddTraits(.isButton)
    }

    // MARK: — Amount

    /// Labels a monetary amount view so VoiceOver announces it naturally,
    /// e.g. "1,234.56 INR" rather than announcing raw formatting characters.
    ///
    /// - Parameters:
    ///   - amount:   The formatted amount string, e.g. "1,234.56".
    ///   - currency: The ISO 4217 currency code or symbol, e.g. "INR".
    func aegisAmount(_ amount: String, currency: String) -> some View {
        self
            .accessibilityLabel("\(amount) \(currency)")
    }

    // MARK: — Image

    /// Labels a decorative or informational image for VoiceOver.
    ///
    /// - Parameter description: A short description of the image content.
    func aegisImage(description: String) -> some View {
        self
            .accessibilityLabel(description)
            .accessibilityAddTraits(.isImage)
    }
}
