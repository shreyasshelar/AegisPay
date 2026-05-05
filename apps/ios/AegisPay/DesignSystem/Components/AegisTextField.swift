import SwiftUI

/// Labeled text field with error + hint support — mirrors the web Input component.
struct AegisTextField: View {
    let label:       String
    let placeholder: String
    @Binding var text: String
    var error:       String? = nil
    var hint:        String? = nil
    var isSecure:    Bool    = false
    var keyboardType: UIKeyboardType = .default
    var autocapitalization: TextInputAutocapitalization = .sentences

    @FocusState private var focused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label)
                .font(.aegisBodySmall)
                .fontWeight(.medium)
                .foregroundStyle(Color.aegisText)

            Group {
                if isSecure {
                    SecureField(placeholder, text: $text)
                } else {
                    TextField(placeholder, text: $text)
                        .keyboardType(keyboardType)
                        .textInputAutocapitalization(autocapitalization)
                }
            }
            .font(.aegisBody)
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .background(Color.aegisSurface)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(borderColor, lineWidth: 1.5)
            )
            .focused($focused)

            if let error {
                Label(error, systemImage: "exclamationmark.circle.fill")
                    .font(.aegisCaption)
                    .foregroundStyle(Color.aegisDanger)
            } else if let hint {
                Text(hint)
                    .font(.aegisCaption)
                    .foregroundStyle(Color.aegisTextMuted)
            }
        }
    }

    private var borderColor: Color {
        if error != nil { return Color.aegisDanger }
        return focused ? Color.aegisPrimary : Color.aegisBorder
    }
}

// ── Amount field ──────────────────────────────────────────────────────────────

/// Large numeric input for entering payment amounts.
struct AegisAmountField: View {
    let currency: String
    @Binding var text: String
    var error: String? = nil

    @FocusState private var focused: Bool

    private var symbol: String {
        switch currency {
        case "INR": return "₹"
        case "USD": return "$"
        case "EUR": return "€"
        case "GBP": return "£"
        default:    return currency
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .center, spacing: 4) {
                Text(symbol)
                    .font(.aegisAmount)
                    .foregroundStyle(Color.aegisTextMuted)

                TextField("0.00", text: $text)
                    .font(.aegisAmount)
                    .keyboardType(.decimalPad)
                    .foregroundStyle(Color.aegisText)
                    .focused($focused)
                    .onChange(of: text) { _, new in
                        text = sanitiseAmount(new)
                    }

                Text(currency)
                    .font(.aegisBodySmall)
                    .fontWeight(.medium)
                    .foregroundStyle(Color.aegisTextSubtle)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 16)
            .background(Color.aegisSurface)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .stroke(
                        error != nil ? Color.aegisDanger
                            : focused ? Color.aegisPrimary
                            : Color.aegisBorder,
                        lineWidth: 1.5
                    )
            )

            if let error {
                Label(error, systemImage: "exclamationmark.circle.fill")
                    .font(.aegisCaption)
                    .foregroundStyle(Color.aegisDanger)
            }
        }
    }

    private func sanitiseAmount(_ raw: String) -> String {
        let digits = raw.filter { $0.isNumber || $0 == "." }
        let parts  = digits.split(separator: ".", omittingEmptySubsequences: false)
        guard parts.count <= 2 else {
            return parts[0] + "." + parts[1...].joined()
        }
        if parts.count == 2 {
            let decimals = String(parts[1].prefix(2))
            return parts[0] + "." + decimals
        }
        return digits
    }
}
