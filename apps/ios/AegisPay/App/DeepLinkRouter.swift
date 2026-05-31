import Foundation
import Combine

// MARK: — Routable destinations

enum DeepLinkDestination: Equatable {
    case transactionDetail(id: String)
    case send
    case wallet
    case profile
    case unknown
}

// MARK: — Router

/// Centralised deep-link and universal-link handler.
///
/// Usage (in a SwiftUI view):
/// ```swift
/// @StateObject private var router = DeepLinkRouter.shared
///
/// .onChange(of: router.pendingDestination) { destination in
///     guard let dest = destination else { return }
///     router.pendingDestination = nil   // consume
///     // navigate to dest
/// }
/// ```
///
/// Supported URL patterns
/// ─────────────────────
/// Custom scheme:     `aegispay://app/transactions/{id}`
/// Universal Links:   `https://api.aegispay.shreyasshelar.uk/transactions/{id}`
///                    `https://api.aegispay.shreyasshelar.uk/send`
///                    `https://api.aegispay.shreyasshelar.uk/wallet`
@MainActor
final class DeepLinkRouter: ObservableObject {

    static let shared = DeepLinkRouter()
    private init() {}

    /// Set by `handle(url:)`, consumed by the root view.
    @Published var pendingDestination: DeepLinkDestination?

    /// Parse a URL (universal link or custom scheme) into a navigable destination.
    @MainActor
    func handle(url: URL) {
        let destination = route(url: url)
        guard destination != .unknown else {
            print("[DeepLinkRouter] Unrecognised URL: \(url)")
            return
        }
        pendingDestination = destination
    }

    // MARK: Private

    private func route(url: URL) -> DeepLinkDestination {
        let pathComponents = url.pathComponents.filter { $0 != "/" }

        switch pathComponents.first {
        case "transactions" where pathComponents.count >= 2:
            return .transactionDetail(id: pathComponents[1])

        case "send":
            return .send

        case "wallet":
            return .wallet

        case "profile":
            return .profile

        default:
            // Try query param: ?transactionId=xxx
            if let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
               let txId = components.queryItems?.first(where: { $0.name == "transactionId" })?.value {
                return .transactionDetail(id: txId)
            }
            return .unknown
        }
    }
}
