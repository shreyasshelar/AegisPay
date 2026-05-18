package com.aegispay.android.ui.profile

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aegispay.android.auth.AuthRepository
import com.aegispay.android.network.AegisApiService
import com.aegispay.android.network.KycDocumentRequest
import com.aegispay.android.network.KycDocumentType
import com.aegispay.android.network.KycProcessingResult
import com.aegispay.android.network.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import javax.inject.Inject

private const val MAX_BYTES = 5 * 1024 * 1024 // 5 MB

data class ProfileUiState(
    val isLoading:      Boolean              = true,
    val profile:        UserProfile?         = null,
    val error:          String?              = null,

    // Document type the user has selected before upload
    val selectedDocType: KycDocumentType    = KycDocumentType.NATIONAL_ID,

    // Upload / processing state
    val isProcessing:   Boolean              = false,
    val kycResult:      KycProcessingResult? = null,
    val processError:   String?              = null,

    // Confirm step
    val isConfirming:   Boolean              = false,
    val confirmSuccess: Boolean              = false,
    val confirmError:   String?              = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val api:            AegisApiService,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    val currentUserId:    String? get() = authRepository.currentUserId
    val currentUserEmail: String? get() = authRepository.currentUserEmail
    val currentUserName:  String? get() = authRepository.currentUserName

    init { loadProfile() }

    // ── Load ──────────────────────────────────────────────────────────────────

    fun loadProfile() {
        val userId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { api.getUser(userId) }
                .onSuccess { p -> _uiState.update { it.copy(isLoading = false, profile = p) } }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    // ── Document type ─────────────────────────────────────────────────────────

    fun onDocTypeChange(t: KycDocumentType) = _uiState.update { it.copy(selectedDocType = t) }

    // ── Process document from gallery URI ─────────────────────────────────────

    fun processDocumentUri(uri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processError = null, kycResult = null) }

            val bytes = runCatching {
                contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()

            if (bytes == null) {
                _uiState.update { it.copy(isProcessing = false, processError = "Failed to read file") }
                return@launch
            }
            if (bytes.size > MAX_BYTES) {
                _uiState.update { it.copy(isProcessing = false, processError = "File must be under 5 MB") }
                return@launch
            }

            val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
            uploadBytes(bytes, mimeType)
        }
    }

    // ── Process document from camera bitmap ───────────────────────────────────

    fun processDocumentBitmap(bitmap: Bitmap) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processError = null, kycResult = null) }

            val bos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, bos)
            val bytes = bos.toByteArray()

            if (bytes.size > MAX_BYTES) {
                _uiState.update { it.copy(isProcessing = false, processError = "Image too large after capture") }
                return@launch
            }
            uploadBytes(bytes, "image/jpeg")
        }
    }

    private suspend fun uploadBytes(bytes: ByteArray, mimeType: String) {
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val docType = _uiState.value.selectedDocType.value

        runCatching {
            api.processKycDocument(
                KycDocumentRequest(
                    documentType    = docType,
                    base64ImageData = base64,
                    mimeType        = mimeType,
                    registeredName  = _uiState.value.profile?.name,
                )
            )
        }
            .onSuccess { result ->
                _uiState.update { it.copy(isProcessing = false, kycResult = result) }
            }
            .onFailure { e ->
                _uiState.update { it.copy(isProcessing = false, processError = e.message) }
            }
    }

    // ── Confirm extracted data ────────────────────────────────────────────────

    fun confirmKyc() {
        val userId  = authRepository.currentUserId ?: return
        val docType = _uiState.value.selectedDocType.value
        viewModelScope.launch {
            _uiState.update { it.copy(isConfirming = true, confirmError = null) }
            runCatching {
                api.confirmKyc(userId, mapOf("documentType" to docType))
            }
                .onSuccess {
                    _uiState.update { it.copy(isConfirming = false, confirmSuccess = true, kycResult = null) }
                    loadProfile() // refresh KYC status
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isConfirming = false, confirmError = e.message) }
                }
        }
    }

    fun resetResult() = _uiState.update { it.copy(kycResult = null, processError = null, confirmError = null) }

    val canConfirm: Boolean
        get() {
            val r = _uiState.value.kycResult ?: return false
            if (r.tampering?.tampered == true) return false
            if (r.validation?.overallValid == false) return false
            return r.quality.acceptable
        }
}
