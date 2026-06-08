import Foundation

extension Decimal {
    /// Formats as a currency string using a locale that matches the currency code.
    ///
    /// Locale selection ensures correct digit-grouping:
    ///   INR → en_IN   (lakh system: ₹1,00,000)
    ///   GBP → en_GB   (£1,000.00)
    ///   EUR → en_IE   (€1,000.00 — English EU locale)
    ///   USD → en_US   ($1,000.00)
    ///   other → en_US fallback
    func formatted(currency: String) -> String {
        let formatter              = NumberFormatter()
        formatter.numberStyle      = .currency
        formatter.currencyCode     = currency
        formatter.locale           = Decimal.locale(for: currency)
        formatter.minimumFractionDigits = 2
        formatter.maximumFractionDigits = 2
        return formatter.string(from: self as NSDecimalNumber) ?? "\(self)"
    }

    static func locale(for currency: String) -> Locale {
        switch currency.uppercased() {
        case "INR": return Locale(identifier: "en_IN")
        case "GBP": return Locale(identifier: "en_GB")
        case "EUR": return Locale(identifier: "en_IE")
        case "USD": return Locale(identifier: "en_US")
        default:    return Locale(identifier: "en_US")
        }
    }
}
