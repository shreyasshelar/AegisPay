import SwiftUI

/// First-time registration screen shown when a Keycloak-authenticated user
/// has no AegisPay account yet. Pre-fills the email from the ID-token.
struct OnboardingView: View {

    @EnvironmentObject var authStore: AuthStore

    let prefillEmail: String?

    @StateObject private var vm = OnboardingViewModel()

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 24) {

                    // ── Hero ──────────────────────────────────────────────────
                    VStack(spacing: 12) {
                        Image(systemName: "person.badge.plus")
                            .font(.system(size: 52))
                            .foregroundStyle(Color.aegisPrimary)

                        Text("Create your profile")
                            .font(.aegisTitle)
                            .foregroundStyle(Color.aegisText)

                        Text("Just a few details to finish setting up your AegisPay account.")
                            .font(.aegisBody)
                            .foregroundStyle(Color.aegisTextMuted)
                            .multilineTextAlignment(.center)
                    }
                    .padding(.top, 24)
                    .padding(.horizontal, 16)

                    // ── Form ──────────────────────────────────────────────────
                    AegisCard(padding: 20) {
                        VStack(spacing: 16) {

                            AegisTextField(
                                label:              "First name",
                                placeholder:        "e.g. Aarav",
                                text:               $vm.firstName,
                                error:              vm.firstNameError,
                                autocapitalization: .words
                            )

                            AegisTextField(
                                label:              "Last name",
                                placeholder:        "e.g. Sharma",
                                text:               $vm.lastName,
                                error:              vm.lastNameError,
                                autocapitalization: .words
                            )

                            AegisTextField(
                                label:              "Email",
                                placeholder:        "your@email.com",
                                text:               $vm.email,
                                error:              vm.emailError,
                                keyboardType:       .emailAddress,
                                autocapitalization: .never
                            )
                        }
                    }
                    .padding(.horizontal, 16)

                    // ── Error ─────────────────────────────────────────────────
                    if let err = vm.errorMessage {
                        HStack(spacing: 10) {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .foregroundStyle(Color.aegisWarning)
                            Text(err)
                                .font(.aegisBodySmall)
                                .foregroundStyle(Color.aegisText)
                        }
                        .padding(14)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.aegisWarningLight)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                        .padding(.horizontal, 16)
                    }

                    // ── CTA ───────────────────────────────────────────────────
                    Button {
                        Task {
                            if let userId = await vm.register() {
                                await authStore.completeRegistration(userId: userId)
                            }
                        }
                    } label: {
                        HStack(spacing: 8) {
                            if vm.isSubmitting {
                                ProgressView()
                                    .tint(.white)
                                    .scaleEffect(0.85)
                            }
                            Text(vm.isSubmitting ? "Creating account…" : "Get started")
                                .font(.aegisBodySmall)
                                .fontWeight(.semibold)
                        }
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                        .background(vm.isValid ? Color.aegisPrimary : Color.aegisBorder)
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                    }
                    .disabled(!vm.isValid || vm.isSubmitting)
                    .padding(.horizontal, 16)

                    // ── Sign out ──────────────────────────────────────────────
                    Button {
                        Task { await authStore.signOut() }
                    } label: {
                        Text("Sign in with a different account")
                            .font(.aegisCaption)
                            .foregroundStyle(Color.aegisTextMuted)
                    }
                    .padding(.bottom, 32)
                }
            }
            .background(Color.aegisBg)
            .navigationTitle("Welcome")
            .navigationBarTitleDisplayMode(.inline)
            .onAppear {
                if let email = prefillEmail, vm.email.isEmpty {
                    vm.email = email
                }
            }
        }
    }
}
