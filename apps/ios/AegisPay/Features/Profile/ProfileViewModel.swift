import SwiftUI
import PhotosUI

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

    private let userService = UserService()

    // ── Load ──────────────────────────────────────────────────────────────────

    func load(userId: String) async {
        isLoading    = true
        errorMessage = nil
        do {
            profile = try await userService.getProfile(userId: userId)
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

        let base64 = data.base64EncodedString()
        let request = KycDocumentRequest(
            documentType:    selectedDocType.rawValue,
            base64ImageData: base64,
            mimeType:        mimeType,
            registeredName:  profile?.name
        )

        do {
            // Server returns 202 immediately — AI pipeline runs in the background.
            // Result arrives via push notification / WebSocket when processing completes.
            try await userService.processKycDocument(request)
            submitSuccess = true
            HapticFeedback.success()
            // Refresh profile so the status banner updates to DOCUMENT_SUBMITTED / AI_PROCESSING
            if let userId = profile?.id {
                await load(userId: userId)
            }
        } catch {
            errorMessage = error.localizedDescription
            HapticFeedback.error()
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    func resetResult() { submitSuccess = false; errorMessage = nil }
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
