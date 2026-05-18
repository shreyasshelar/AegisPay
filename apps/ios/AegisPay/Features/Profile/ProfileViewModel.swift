import SwiftUI
import PhotosUI

@MainActor
final class ProfileViewModel: ObservableObject {

    @Published private(set) var profile:        UserProfile?
    @Published private(set) var isLoading       = false
    @Published private(set) var isProcessing    = false   // OCR in flight
    @Published private(set) var isConfirming    = false   // PATCH in flight
    @Published private(set) var kycResult:      KycProcessingResult?
    @Published private(set) var errorMessage:   String?
    @Published private(set) var confirmSuccess  = false

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
        kycResult     = nil
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
        isProcessing = true
        kycResult    = nil
        errorMessage = nil

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
            let result = try await userService.processKycDocument(request)
            kycResult  = result

            if result.tampering?.tampered == true {
                HapticFeedback.warning()
            } else if result.quality.acceptable {
                HapticFeedback.success()
            } else {
                HapticFeedback.warning()
            }
        } catch {
            errorMessage = error.localizedDescription
            HapticFeedback.error()
        }
    }

    // ── Confirm extracted data ────────────────────────────────────────────────

    func confirmKyc(userId: String) async {
        isConfirming  = true
        errorMessage  = nil
        do {
            try await userService.confirmKyc(userId: userId, documentType: selectedDocType.rawValue)
            confirmSuccess = true
            kycResult      = nil
            HapticFeedback.success()
            // Refresh profile to get updated kycStatus
            await load(userId: userId)
        } catch {
            errorMessage = error.localizedDescription
            HapticFeedback.error()
        }
        isConfirming = false
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    func resetResult() { kycResult = nil; errorMessage = nil }

    var canConfirm: Bool {
        guard let r = kycResult else { return false }
        if r.tampering?.tampered == true { return false }
        if let v = r.validation, !v.overallValid { return false }
        return r.quality.acceptable
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
