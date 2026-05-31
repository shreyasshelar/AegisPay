import Foundation

extension Decimal {
    /// Formats as a currency string using the user's locale.
    func formatted(currency: String) -> String {
        let formatter              = NumberFormatter()
        formatter.numberStyle      = .currency
        formatter.currencyCode     = currency
        formatter.minimumFractionDigits = 2
        formatter.maximumFractionDigits = 2
        return formatter.string(from: self as NSDecimalNumber) ?? "\(self)"
    }
}
