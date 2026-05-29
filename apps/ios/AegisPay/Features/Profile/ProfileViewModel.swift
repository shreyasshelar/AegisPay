import SwiftUI
import PhotosUI
import FirebaseAuth

/**
 * Steps in the phone-number OTP verification flow.
 * Firebase handles SMS delivery; AegisPay backend is updated only after the credential
 * is confirmed, then the Firebase session is immediately signed out so it never
 * interferes with the Keycloak / AppAuth primary session.
 */
enum PhoneStep {
    case idle         // collapsed — no active flow
    case enterPhone   // user typing phone number
    case sending      // waiting for Firebase SMS
    case enterOtp     // SMS received — user typing 6-digit code
    case saving       // credential verified; persisting to backend
    case saved        // success
}

@MainActor
final class ProfileViewModel: ObservableObject {

    @Published private(set) var profile:        UserProfile?
    @Published private(set) var isLoading       = false
    @Published private(set) var isProcessing    = false   // upload in flight
    @Published private(set) var submitSuccess   = false   // 202 received; processing in background
    @Published private(set) var errorMessage:   String?

    // Document type selection
    @Published var selectedDocType: KycDocumentType = .nationalId

    // PhotosPicker binding
    @Published var selectedPhoto: PhotosPickerItem? = nil {
        didSet { Task { await processSelectedPhoto() } }
    }

    // ── Phone OTP flow ────────────────────────────────────────────────────────
    @Published private(set) var phoneStep:  PhoneStep = .idle
    @Published var phoneInput: String = ""
    @Published var otpInput:   String = ""
    @Published private(set) var phoneError: String?

    private var verificationID: String?

    private let userService = UserService()

    /// Polls /users/{id} every 10 s while KYC is in an intermediate state.
    /// Non-nil while the poll is active; set to nil when the task is cancelled or
    /// when the status transitions to a terminal state.
    private var kycPollingTask: Task<Void, Never>?

    private static let intermediateStatuses: Set<KycStatus> = [.documentSubmitted, .aiProcessing]

    // ── Load ──────────────────────────────────────────────────────────────────

    func load(userId: String) async {
        isLoading    = true
        errorMessage = nil
        do {
            let p = try await userService.getProfile(userId: userId)
            profile = p
            maybeStartKycPolling(status: p.kycStatus, userId: userId)
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }

    // ── Process photo (PhotosPicker) ──────────────────────────────────────────

    private func processSelectedPhoto() async {
        guard let item = selectedPhoto else { return }
        defer { selectedPhoto = nil }

        isProcessing  = true
        submitSuccess = false
        errorMessage  = nil

        do {
            guard let data = try await item.loadTransferable(type: Data.self) else {
                throw AppError.imageLoadFailed
            }
            await processImageData(data, mimeType: "image/jpeg")
        } catch {
            errorMessage = error.localizedDescription
            HapticFeedback.error()
        }

        isProcessing = false
    }

    // ── Process image from camera or picker ───────────────────────────────────

    func processImage(_ uiImage: UIImage) async {
        isProcessing  = true
        submitSuccess = false
        errorMessage  = nil

        guard let data = uiImage.jpegData(compressionQuality: 0.85) else {
            errorMessage = AppError.imageLoadFailed.localizedDescription
            isProcessing = false
            return
        }
        await processImageData(data, mimeType: "image/jpeg")
        isProcessing = false
    }

    private func processImageData(_ data: Data, mimeType: String) async {
        guard data.count <= 5 * 1024 * 1024 else {
            errorMessage = AppError.imageTooLarge.localizedDescription
            HapticFeedback.error()
            return
        }

        do {
            // Send as multipart/form-data — AI Platform requires this format.
            // (JSON base64 causes 415 Unsupported Media Type.)
            // Server returns 202 immediately — AI pipeline runs in the background.
            // Result arrives via push notification / WebSocket when processing completes.
            try await userService.processKycDocument(
                data:           data,
                mimeType:       mimeType,
                documentType:   selectedDocType.rawValue,
                registeredName: profile?.name
            )
            submitSuccess = true
            HapticFeedback.success()

            // Refresh profile so the status banner updates to DOCUMENT_SUBMITTED / AI_PROCESSING
            if let userId = profile?.id {
                await load(userId: userId)
                // If load() returned before the server committed the new status,
                // start polling now so we catch the transition when it arrives.
                maybeStartKycPolling(status: .documentSubmitted, userId: userId)
            }
        } catch {
            errorMessage = error.localizedDescription
            HapticFeedback.error()
        }
    }

    // ── KYC status polling ────────────────────────────────────────────────────

    /// Starts a 10-second poll when [status] is intermediate (DOCUMENT_SUBMITTED or
    /// AI_PROCESSING). Cancels any active poll when the status is already terminal.
    /// Idempotent — safe to call while a poll is already active.
    private func maybeStartKycPolling(status: KycStatus, userId: String) {
        guard Self.intermediateStatuses.contains(status) else {
            kycPollingTask?.cancel()
            kycPollingTask = nil
            return
        }
        guard kycPollingTask == nil else { return } // already polling

        kycPollingTask = Task { @MainActor [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(10))
                guard !Task.isCancelled, let self else { return }
                do {
                    let p = try await self.userService.getProfile(userId: userId)
                    self.profile = p
                    if !Self.intermediateStatuses.contains(p.kycStatus) {
                        // Reached APPROVED / REJECTED / MANUAL_REVIEW — stop polling.
                        self.kycPollingTask?.cancel()
                        self.kycPollingTask = nil
                        return
                    }
                } catch {
                    // Network error — keep polling; next tick will retry.
                }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    func resetResult() { submitSuccess = false; errorMessage = nil }

    deinit { kycPollingTask?.cancel() }

    // ── Phone OTP flow ────────────────────────────────────────────────────────

    func openPhoneSheet() {
        phoneInput = "" // don't pre-fill the masked value; user enters the real number
        otpInput   = ""
        phoneError = nil
        phoneStep  = .enterPhone
    }

    func cancelPhone() {
        phoneStep  = .idle
        phoneInput = ""
        otpInput   = ""
        phoneError = nil
    }

    /**
     * Request an SMS OTP from Firebase Phone Auth.
     * `uiDelegate: nil` works for most devices; iOS falls back to reCAPTCHA web flow
     * automatically when silent push isn't available.
     */
    func sendOtp() async {
        let phone = phoneInput.trimmingCharacters(in: .whitespaces)
        guard !phone.isEmpty else { phoneError = "Enter a phone number (e.g. +919876543210)"; return }
        phoneStep  = .sending
        phoneError = nil
        do {
            let id = try await PhoneAuthProvider.provider().verifyPhoneNumber(phone, uiDelegate: nil)
            verificationID = id
            phoneStep = .enterOtp
        } catch {
            phoneStep  = .enterPhone
            phoneError = error.localizedDescription
        }
    }

    /**
     * Confirm the OTP the user typed, then persist the verified number.
     * Uses `profile.id` (AegisPay domain UUID loaded from the backend) as the userId
     * — this is unambiguous and cannot be confused with the Keycloak sub.
     * Firebase session is signed out immediately after — Keycloak handles primary auth.
     */
    func verifyOtp() async {
        guard let id     = verificationID else { return }
        guard let userId = profile?.id    else { phoneError = "Profile not loaded"; return }
        let code = otpInput.trimmingCharacters(in: .whitespaces)
        guard code.count == 6 else { phoneError = "Enter the 6-digit code"; return }

        let credential = PhoneAuthProvider.provider().credential(
            withVerificationID: id,
            verificationCode:   code
        )
        phoneStep = .saving
        do {
            _ = try await Auth.auth().signIn(with: credential)
            try? Auth.auth().signOut() // immediately — Keycloak is primary auth

            let phone   = phoneInput.trimmingCharacters(in: .whitespaces)
            let updated = try await userService.updatePhone(userId: userId, phone: phone)
            profile    = updated
            phoneStep  = .saved
            HapticFeedback.success()
        } catch {
            phoneStep  = .enterOtp
            phoneError = error.localizedDescription
            HapticFeedback.error()
        }
    }
}

enum AppError: LocalizedError {
    case imageLoadFailed
    case imageTooLarge

    var errorDescription: String? {
        switch self {
        case .imageLoadFailed: return "Could not load the selected image."
        case .imageTooLarge:   return "Image must be under 5 MB."
        }
    }
}
