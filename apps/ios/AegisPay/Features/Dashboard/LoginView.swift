import SwiftUI

struct LoginView: View {
    @EnvironmentObject var authStore: AuthStore
    @State private var isSigningIn = false
    @State private var errorMessage: String?

    var body: some View {
        ZStack {
            // Background gradient
            LinearGradient(
                colors: [Color.aegisPrimaryLight, Color.white, Color(hex: "#f1f5f9")],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()

            VStack(spacing: 0) {
                Spacer()

                // ── Hero ──────────────────────────────────────────────────────
                VStack(spacing: 20) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 24)
                            .fill(Color.aegisPrimary)
                            .frame(width: 88, height: 88)
                            .shadow(color: Color.aegisPrimary.opacity(0.4), radius: 16, y: 6)

                        Image(systemName: "shield.checkered")
                            .font(.system(size: 44, weight: .semibold))
                            .foregroundStyle(.white)
                    }

                    VStack(spacing: 8) {
                        Text("AegisPay")
                            .font(.aegisTitle)
                            .foregroundStyle(Color.aegisText)

                        Text("Secure, event-driven payments")
                            .font(.aegisBody)
                            .foregroundStyle(Color.aegisTextMuted)
                    }
                }

                Spacer()

                // ── Error banner ──────────────────────────────────────────────
                if let error = errorMessage {
                    HStack(spacing: 10) {
                        Image(systemName: "exclamationmark.circle.fill")
                            .foregroundStyle(Color.aegisDanger)
                        Text(error)
                            .font(.aegisBodySmall)
                            .foregroundStyle(Color.aegisDanger)
                        Spacer()
                    }
                    .padding(14)
                    .background(Color.aegisDangerLight)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(Color.aegisDanger.opacity(0.3), lineWidth: 1)
                    )
                    .padding(.bottom, 16)
                    .transition(.move(edge: .top).combined(with: .opacity))
                }

                // ── Sign-in button ────────────────────────────────────────────
                Button {
                    Task { await startSignIn() }
                } label: {
                    HStack(spacing: 10) {
                        if isSigningIn {
                            ProgressView()
                                .progressViewStyle(.circular)
                                .tint(.white)
                        } else {
                            Image(systemName: "shield.checkered")
                        }
                        Text(isSigningIn ? "Redirecting…" : "Continue with Keycloak")
                            .fontWeight(.semibold)
                    }
                }
                .aegisButtonStyle(.primary, loading: isSigningIn, fullWidth: true)

                // ── Fine print ────────────────────────────────────────────────
                Text("Protected by OAuth 2.0 + PKCE · Zero-trust")
                    .font(.aegisCaption)
                    .foregroundStyle(Color.aegisTextSubtle)
                    .padding(.top, 20)
                    .padding(.bottom, 40)
            }
            .padding(.horizontal, 28)
            .animation(.easeInOut, value: errorMessage)
        }
    }

    private func startSignIn() async {
        isSigningIn  = true
        errorMessage = nil

        // AppAuth requires a UIViewController; grab the root one
        guard let vc = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene })
            .first?.windows.first?.rootViewController
        else {
            errorMessage = "Cannot present sign-in screen."
            isSigningIn  = false
            return
        }

        await authStore.signIn(presentingViewController: vc)

        if let err = authStore.lastError {
            errorMessage = err.errorDescription
        }
        isSigningIn = false
    }
}
