import SwiftUI
import PhotosUI

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
    @StateObject private var vm = ProfileViewModel()

    @State private var showSourceSheet  = false
    @State private var showCamera       = false
    @State private var cameraImage:    UIImage? = nil

    // When camera returns an image, process it
    private func handleCameraImage(_ img: UIImage?) {
        guard let img else { return }
        Task { await vm.processImage(img) }
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
                    if let result = vm.kycResult {
                        kycResultCard(result)
                    }
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
            .animation(.easeInOut, value: vm.kycResult != nil)
            // Camera sheet
            .sheet(isPresented: $showCamera, onDismiss: { handleCameraImage(cameraImage) }) {
                CameraPickerView(image: $cameraImage)
                    .ignoresSafeArea()
            }
            // Source selection action sheet
            .confirmationDialog("Select Document Source", isPresented: $showSourceSheet, titleVisibility: .visible) {
                Button("Camera") { showCamera = true }
                PhotosPicker(selection: $vm.selectedPhoto, matching: .images) {
                    Text("Photo Library")
                }
                Button("Cancel", role: .cancel) {}
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
                        Text(vm.profile?.name ?? authStore.currentUser?.name ?? "—")
                            .font(.aegisSubhead)
                            .foregroundStyle(Color.aegisText)
                        Text(vm.profile?.email ?? authStore.currentUser?.email ?? "—")
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

        if canUpload && vm.kycResult == nil {
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
                                Text("Analysing document…")
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

    // MARK: — KYC result card

    @ViewBuilder
    private func kycResultCard(_ result: KycProcessingResult) -> some View {
        AegisCard {
            VStack(alignment: .leading, spacing: 20) {

                // ── Quality section ──────────────────────────────────────────
                VStack(alignment: .leading, spacing: 10) {
                    HStack {
                        Image(systemName: "star.fill")
                            .foregroundStyle(Color.aegisWarning)
                        Text("Image Quality")
                            .font(.aegisBodySmall)
                            .fontWeight(.semibold)
                            .foregroundStyle(Color.aegisText)
                        Spacer()
                        Text(result.quality.acceptable ? "Acceptable" : "Low Quality")
                            .font(.aegisCaption)
                            .fontWeight(.semibold)
                            .foregroundStyle(result.quality.acceptable ? Color.aegisSuccess : Color.aegisDanger)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(result.quality.acceptable ? Color.aegisSuccessLight : Color.aegisDangerLight)
                            .clipShape(Capsule())
                    }
                    QualityBarRow(label: "Sharpness",  score: result.quality.sharpness)
                    QualityBarRow(label: "Brightness", score: result.quality.brightness)
                    QualityBarRow(label: "Overall",    score: result.quality.overallScore)
                }

                // ── Tampering alert ──────────────────────────────────────────
                if result.tampering?.tampered == true {
                    HStack(alignment: .top, spacing: 10) {
                        Image(systemName: "exclamationmark.triangle.fill")
                            .foregroundStyle(Color.aegisDanger)
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Tampering Detected")
                                .font(.aegisBodySmall)
                                .fontWeight(.semibold)
                                .foregroundStyle(Color.aegisDanger)
                            if let indicators = result.tampering?.indicators, !indicators.isEmpty {
                                Text(indicators.joined(separator: " · "))
                                    .font(.aegisCaption)
                                    .foregroundStyle(Color.aegisDanger.opacity(0.8))
                            }
                        }
                    }
                    .padding(12)
                    .background(Color.aegisDangerLight)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.aegisDanger.opacity(0.3), lineWidth: 1))
                }

                // ── Extracted data ───────────────────────────────────────────
                if let data = result.extractedData {
                    Divider()
                    VStack(alignment: .leading, spacing: 10) {
                        HStack {
                            Image(systemName: "doc.text.magnifyingglass")
                                .foregroundStyle(Color.aegisPrimary)
                            Text("Extracted Information")
                                .font(.aegisBodySmall)
                                .fontWeight(.semibold)
                                .foregroundStyle(Color.aegisText)
                        }

                        VStack(spacing: 0) {
                            extractedRow("Full Name",       data.fullName)
                            extractedRow("Date of Birth",   data.dateOfBirth)
                            extractedRow("Document Number", data.documentNumber)
                            extractedRow("Document Type",   data.documentType)
                            extractedRow("Expiry Date",     data.expiryDate)
                            extractedRow("Address",         data.address)
                        }
                        .background(Color.aegisSurface)
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.aegisBorder, lineWidth: 1))

                        Text("Please verify this information before confirming.")
                            .font(.aegisCaption)
                            .foregroundStyle(Color.aegisTextSubtle)
                    }
                }

                // ── Error from confirm ───────────────────────────────────────
                if let err = vm.errorMessage {
                    Text(err)
                        .font(.aegisBodySmall)
                        .foregroundStyle(Color.aegisDanger)
                }

                // ── Actions ──────────────────────────────────────────────────
                HStack(spacing: 12) {
                    Button { vm.resetResult() } label: {
                        Label("Retake", systemImage: "arrow.counterclockwise")
                    }
                    .aegisButtonStyle(.secondary, fullWidth: true)
                    .disabled(vm.isConfirming)

                    Button {
                        Task { await vm.confirmKyc(userId: authStore.currentUser?.id ?? "") }
                    } label: {
                        Label(
                            vm.isConfirming ? "Submitting…" : "Confirm & Submit",
                            systemImage: "checkmark.circle.fill"
                        )
                    }
                    .aegisButtonStyle(.primary, loading: vm.isConfirming, fullWidth: true)
                    .disabled(!vm.canConfirm || vm.isConfirming)
                }
            }
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

    // MARK: — Helpers

    @ViewBuilder
    private func extractedRow(_ label: String, _ value: String?) -> some View {
        if let value, !value.isEmpty {
            HStack(alignment: .top) {
                Text(label)
                    .font(.aegisCaption)
                    .foregroundStyle(Color.aegisTextMuted)
                    .frame(width: 110, alignment: .leading)
                Text(value)
                    .font(.aegisCaption)
                    .fontWeight(.medium)
                    .foregroundStyle(Color.aegisText)
                    .lineLimit(2)
                Spacer()
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            Divider().padding(.leading, 12)
        }
    }
}

// MARK: — Quality bar

private struct QualityBarRow: View {
    let label: String
    let score: Double

    private var color: Color {
        score >= 70 ? Color.aegisSuccess : score >= 40 ? Color.aegisWarning : Color.aegisDanger
    }

    var body: some View {
        VStack(spacing: 3) {
            HStack {
                Text(label)
                    .font(.aegisCaption)
                    .foregroundStyle(Color.aegisTextMuted)
                Spacer()
                Text("\(Int(score))%")
                    .font(.aegisCaption)
                    .fontWeight(.semibold)
                    .foregroundStyle(color)
            }
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 4)
                        .fill(Color.aegisBorder)
                        .frame(height: 6)
                    RoundedRectangle(cornerRadius: 4)
                        .fill(color)
                        .frame(width: geo.size.width * score / 100, height: 6)
                }
            }
            .frame(height: 6)
        }
    }
}
