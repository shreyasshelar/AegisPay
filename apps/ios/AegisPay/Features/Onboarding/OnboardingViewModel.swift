import SwiftUI

@MainActor
final class OnboardingViewModel: ObservableObject {

    @Published var firstName = ""
    @Published var lastName  = ""
    @Published var email     = ""

    @Published private(set) var isSubmitting = false
    @Published private(set) var errorMessage: String?

    private let userService    = UserService()
    private let idempotencyKey = UUID().uuidString

    // ── Validation ────────────────────────────────────────────────────────────

    var firstNameError: String? {
        guard !firstName.isEmpty else { return nil }
        return firstName.count < 2 ? "At least 2 characters required" : nil
    }

    var lastNameError: String? {
        guard !lastName.isEmpty else { return nil }
        return lastName.count < 2 ? "At least 2 characters required" : nil
    }

    var emailError: String? {
        guard !email.isEmpty else { return nil }
        let pattern = /^[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}$/
        return email.wholeMatch(of: pattern) == nil ? "Enter a valid email address" : nil
    }

    var isValid: Bool {
        !firstName.isEmpty && !lastName.isEmpty && !email.isEmpty &&
        firstNameError == nil && lastNameError == nil && emailError == nil
    }

    // ── Registration ──────────────────────────────────────────────────────────

    /// Registers the user with AegisPay and returns the created userId on success.
    /// The caller (RootView / OnboardingView) should call `authStore.completeRegistration(userId:)`.
    func register() async -> String? {
        guard isValid else { return nil }
        isSubmitting = true
        errorMessage = nil
        defer { isSubmitting = false }

        do {
            let profile = try await userService.register(
                firstName:      firstName.trimmingCharacters(in: .whitespaces),
                lastName:       lastName.trimmingCharacters(in: .whitespaces),
                email:          email.trimmingCharacters(in: .whitespaces),
                idempotencyKey: idempotencyKey
            )
            return profile.id
        } catch {
            errorMessage = error.localizedDescription
            return nil
        }
    }
}
