import SwiftUI
import PhotosUI
import AVFoundation

// MARK: — Camera picker wrapper

struct CameraPickerView: UIViewControllerRepresentable {
    @Binding var image: UIImage?
    @Environment(\.dismiss) private var dismiss

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker            = UIImagePickerController()
        picker.sourceType     = .camera
        picker.delegate       = context.coordinator
        picker.allowsEditing  = false
        return picker
    }

    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}

    final class Coordinator: NSObject, UINavigationControllerDelegate, UIImagePickerControllerDelegate {
        let parent: CameraPickerView
        init(_ parent: CameraPickerView) { self.parent = parent }

        func imagePickerController(
            _ picker: UIImagePickerController,
            didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]
        ) {
            parent.image = info[.originalImage] as? UIImage
            parent.dismiss()
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            parent.dismiss()
        }
    }
}

// MARK: — ProfileView

struct ProfileView: View {
    @EnvironmentObject var authStore: AuthStore
    @EnvironmentObject var biometricService: BiometricAuthService
    @StateObject private var vm = ProfileViewModel()

    @State private var showSourceSheet  = false
    @State private var showCamera       = false
    @State private var showCameraPermissionDenied = false
    @State private var cameraImage:    UIImage? = nil

    // When camera returns an image, process it
    private func handleCameraImage(_ img: UIImage?) {
        guard let img else { return }
        Task { await vm.processImage(img) }
    }

    private func requestCameraAndShow() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            showCamera = true
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { granted in
                DispatchQueue.main.async {
                    if granted { showCamera = true }
                    else { showCameraPermissionDenied = true }
                }
            }
        default:
            showCameraPermissionDenied = true
        }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    profileCard
                    kycStatusBannerView
                    if vm.profile?.kycStatus == .approved {
                        verifiedBadge
                    }
                    kycUploadSection
                    securitySection
                    signOutButton
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            }
            .background(Color.aegisBg)
            .navigationTitle("Profile")
            .navigationBarTitleDisplayMode(.large)
            .task { await vm.load(userId: authStore.currentUser?.id ?? "") }
            .animation(.easeInOut, value: vm.profile?.kycStatus)
            .animation(.easeInOut, value: vm.isProcessing)
            // Camera sheet
            .sheet(isPresented: $showCamera, onDismiss: { handleCameraImage(cameraImage) }) {
                CameraPickerView(image: $cameraImage)
                    .ignoresSafeArea()
            }
            // Source selection action sheet
            .confirmationDialog("Select Document Source", isPresented: $showSourceSheet, titleVisibility: .visible) {
                Button("Camera") { requestCameraAndShow() }
                PhotosPicker(selection: $vm.selectedPhoto, matching: .images) {
                    Text("Photo Library")
                }
                Button("Cancel", role: .cancel) {}
            }
            .alert("Camera Access Required", isPresented: $showCameraPermissionDenied) {
                Button("Cancel", role: .cancel) {}
                Button("Open Settings") {
                    if let url = URL(string: UIApplication.openSettingsURLString) {
                        UIApplication.shared.open(url)
                    }
                }
            } message: {
                Text("Please allow camera access in Settings to scan your document.")
            }
        }
    }

    // MARK: — Profile header card

    private var profileCard: some View {
        AegisCard {
            HStack(spacing: 16) {
                ZStack {
                    Circle()
                        .fill(Color.aegisPrimaryLight)
                        .frame(width: 56, height: 56)
                    Text(String(vm.profile?.name?.prefix(1) ?? "?").uppercased())
                        .font(.aegisHeadline)
                        .foregroundStyle(Color.aegisPrimary)
                }
                VStack(alignment: .leading, spacing: 4) {
                    if vm.isLoading {
                        SkeletonRect(width: 130, height: 16)
                        SkeletonRect(width: 180, height: 12)
                    } else {
                        Text(vm.profile?.name ?? "—")
                            .font(.aegisSubhead)
                            .foregroundStyle(Color.aegisText)
                        Text(vm.profile?.email ?? "—")
                            .font(.aegisBodySmall)
                            .foregroundStyle(Color.aegisTextMuted)
                        Text(authStore.currentUser?.role ?? "CUSTOMER")
                            .font(.aegisCaption)
                            .fontWeight(.semibold)
                            .foregroundStyle(Color.aegisPrimary)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(Color.aegisPrimaryLight)
                            .clipShape(Capsule())
                    }
                }
                Spacer()
            }
        }
    }

    // MARK: — KYC status banner

    @ViewBuilder
    private var kycStatusBannerView: some View {
        let status = vm.profile?.kycStatus ?? .pending
        let (icon, label, bg, tint): (String, String, Color, Color) = {
            switch status {
            case .pending:           return ("clock.fill",                    "KYC Pending — upload a document to get started",          Color.aegisWarningLight, Color.aegisWarning)
            case .documentSubmitted: return ("doc.badge.clock",               "Document received — awaiting AI processing",               Color.aegisPrimaryLight, Color.aegisPrimary)
            case .aiProcessing:      return ("cpu",                           "AI is processing your document…",                         Color.aegisPrimaryLight, Color.aegisPrimary)
            case .approved:          return ("checkmark.shield.fill",         "Identity Verified — full account access",                  Color.aegisSuccessLight, Color.aegisSuccess)
            case .rejected:          return ("xmark.shield.fill",             "KYC Rejected — please re-upload a valid document",         Color.aegisDangerLight,  Color.aegisDanger)
            case .manualReview:      return ("person.badge.shield.checkmark", "Under manual review — our team will contact you",          Color(hex: "#f1f5f9"),   Color.aegisTextMuted)
            }
        }()

        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 20, weight: .medium))
                .foregroundStyle(tint)
            Text(label)
                .font(.aegisBodySmall)
                .foregroundStyle(tint)
                .fontWeight(.medium)
            Spacer()
        }
        .padding(14)
        .background(bg)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(tint.opacity(0.3), lineWidth: 1))
    }

    private var verifiedBadge: some View {
        HStack(spacing: 12) {
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 32))
                .foregroundStyle(Color.aegisSuccess)
            VStack(alignment: .leading, spacing: 2) {
                Text("Identity Verified")
                    .font(.aegisSubhead)
                    .foregroundStyle(Color.aegisSuccess)
                Text("Your account has full transaction limits")
                    .font(.aegisBodySmall)
                    .foregroundStyle(Color.aegisSuccess.opacity(0.8))
            }
            Spacer()
        }
        .padding(16)
        .background(Color.aegisSuccessLight)
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.aegisSuccess.opacity(0.3), lineWidth: 1))
    }

    // MARK: — Upload section

    @ViewBuilder
    private var kycUploadSection: some View {
        let status = vm.profile?.kycStatus ?? .pending
        let canUpload = status == .pending || status == .rejected

        if canUpload {
            AegisCard {
                VStack(spacing: 20) {
                    // Header
                    HStack(spacing: 12) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 10)
                                .fill(Color.aegisPrimaryLight)
                                .frame(width: 40, height: 40)
                            Image(systemName: "doc.viewfinder")
                                .font(.system(size: 18))
                                .foregroundStyle(Color.aegisPrimary)
                        }
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Upload Identity Document")
                                .font(.aegisSubhead)
                                .foregroundStyle(Color.aegisText)
                            Text("JPG · PNG · Max 5 MB")
                                .font(.aegisCaption)
                                .foregroundStyle(Color.aegisTextSubtle)
                        }
                        Spacer()
                    }

                    // Document type picker
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Document Type")
                            .font(.aegisBodySmall)
                            .fontWeight(.medium)
                            .foregroundStyle(Color.aegisText)
                        Picker("Document Type", selection: $vm.selectedDocType) {
                            ForEach(KycDocumentType.allCases, id: \.self) { dt in
                                Text(dt.displayLabel).tag(dt)
                            }
                        }
                        .pickerStyle(.menu)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 12)
                        .frame(height: 44)
                        .background(Color.aegisSurface)
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.aegisBorder, lineWidth: 1.5))
                    }

                    // Drop zone
                    ZStack {
                        RoundedRectangle(cornerRadius: 14)
                            .fill(Color.aegisSurface)
                        RoundedRectangle(cornerRadius: 14)
                            .strokeBorder(style: StrokeStyle(lineWidth: 2, dash: [6]))
                            .foregroundStyle(Color.aegisBorder)

                        if vm.isProcessing {
                            VStack(spacing: 10) {
                                ProgressView().scaleEffect(1.2)
                                Text("Uploading…")
                                    .font(.aegisBodySmall)
                                    .foregroundStyle(Color.aegisTextMuted)
                            }
                        } else {
                            VStack(spacing: 8) {
                                Image(systemName: "arrow.up.doc")
                                    .font(.system(size: 32))
                                    .foregroundStyle(Color.aegisBorder)
                                Text("Tap a button below to add your document")
                                    .font(.aegisBodySmall)
                                    .foregroundStyle(Color.aegisTextSubtle)
                                    .multilineTextAlignment(.center)
                            }
                        }
                    }
                    .frame(height: 120)

                    // Buttons row
                    HStack(spacing: 12) {
                        Button {
                            showSourceSheet = true
                        } label: {
                            Label("Camera", systemImage: "camera.fill")
                                .frame(maxWidth: .infinity)
                        }
                        .aegisButtonStyle(.secondary, fullWidth: false)
                        .disabled(vm.isProcessing)

                        PhotosPicker(selection: $vm.selectedPhoto, matching: .images) {
                            Label("Gallery", systemImage: "photo.on.rectangle")
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 14)
                                .background(Color.aegisSurface)
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.aegisBorder, lineWidth: 1.5))
                                .foregroundStyle(Color.aegisText)
                                .font(.aegisBodySmall.weight(.semibold))
                        }
                        .disabled(vm.isProcessing)
                    }

                    // Error
                    if let err = vm.errorMessage {
                        HStack(spacing: 8) {
                            Image(systemName: "exclamationmark.circle.fill")
                                .foregroundStyle(Color.aegisDanger)
                            Text(err)
                                .font(.aegisBodySmall)
                                .foregroundStyle(Color.aegisDanger)
                                .lineLimit(3)
                        }
                        .transition(.opacity.combined(with: .move(edge: .top)))
                    }
                }
            }
        }
    }

    // MARK: — Security section

    @ViewBuilder
    private var securitySection: some View {
        if biometricService.isAvailable {
            AegisCard {
                VStack(alignment: .leading, spacing: 12) {
                    HStack(spacing: 10) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 8)
                                .fill(Color.aegisPrimaryLight)
                                .frame(width: 34, height: 34)
                            Image(systemName: biometricIconName)
                                .font(.system(size: 16, weight: .medium))
                                .foregroundStyle(Color.aegisPrimary)
                        }
                        Text("Security")
                            .font(.aegisSubhead)
                            .foregroundStyle(Color.aegisText)
                        Spacer()
                    }

                    Divider()

                    Toggle(isOn: Binding(
                        get: { biometricService.isEnabled },
                        set: { biometricService.isEnabled = $0 }
                    )) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(biometricToggleLabel)
                                .font(.aegisBodySmall)
                                .fontWeight(.medium)
                                .foregroundStyle(Color.aegisText)
                        }
                    }
                    .tint(Color.aegisPrimary)

                    Text("When enabled, you'll be asked to authenticate when returning to the app.")
                        .font(.aegisCaption)
                        .foregroundStyle(Color.aegisTextSubtle)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }

    private var biometricIconName: String {
        switch biometricService.biometricType {
        case .faceID, .opticID: return "faceid"
        case .touchID:          return "touchid"
        case .none:             return "lock.fill"
        }
    }

    private var biometricToggleLabel: String {
        switch biometricService.biometricType {
        case .faceID:   return "Face ID"
        case .opticID:  return "Optic ID"
        case .touchID:  return "Touch ID"
        case .none:     return "Biometric Unlock"
        }
    }

    // MARK: — Sign out

    private var signOutButton: some View {
        Button(role: .destructive) {
            Task { await authStore.signOut() }
        } label: {
            Label("Sign Out", systemImage: "rectangle.portrait.and.arrow.right")
                .fontWeight(.semibold)
                .frame(maxWidth: .infinity)
        }
        .aegisButtonStyle(.destructive, fullWidth: true)
    }

}
