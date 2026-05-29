package com.aegispay.android.ui.profile

import android.app.Activity
import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aegispay.android.auth.AuthRepository
import com.aegispay.android.network.AegisApiService
import com.aegispay.android.network.KycDocumentType
import com.aegispay.android.network.KycStatus
import com.aegispay.android.network.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.auth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val MAX_BYTES = 5 * 1024 * 1024 // 5 MB

/** KYC states where the AI pipeline is still running — poll until one of these clears. */
private val INTERMEDIATE_KYC = setOf(KycStatus.DOCUMENT_SUBMITTED, KycStatus.AI_PROCESSING)

/**
 * Steps in the phone-number OTP verification flow.
 * Firebase handles the SMS delivery; AegisPay backend is updated only after Firebase
 * confirms the OTP, then the Firebase session is immediately signed out.
 */
enum class PhoneStep {
    IDLE,         // phone section collapsed / no action in progress
    ENTER_PHONE,  // user typing phone number
    SENDING,      // Firebase is sending the SMS OTP
    ENTER_OTP,    // OTP sent — user entering 6-digit code
    SAVING,       // Firebase credential verified; persisting to AegisPay backend
    SAVED,        // backend saved — show success banner
}

data class ProfileUiState(
    val isLoading:      Boolean           = true,
    val profile:        UserProfile?      = null,
    val error:          String?           = null,

    // Document type the user has selected before upload
    val selectedDocType: KycDocumentType = KycDocumentType.NATIONAL_ID,

    // Upload / processing state
    val isProcessing:   Boolean           = false,
    /** True once the server has accepted the document (202). AI pipeline runs in background;
     *  result arrives via push notification / WebSocket. */
    val uploadSuccess:  Boolean           = false,
    val processError:   String?           = null,

    // Phone OTP flow
    val phoneStep:  PhoneStep = PhoneStep.IDLE,
    val phoneInput: String    = "",
    val otpInput:   String    = "",
    val phoneError: String?   = null,
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

    /** Polls /users/{id} every 10 s while KYC is in an intermediate state. */
    private var kycPollingJob: Job? = null

    /** Firebase verification ID stored between sendOtp() and verifyOtp(). */
    private var _verificationId: String? = null

    init { loadProfile() }

    // ── Load ──────────────────────────────────────────────────────────────────

    fun loadProfile() {
        val userId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { api.getUser(userId) }
                .onSuccess { p ->
                    _uiState.update { it.copy(isLoading = false, profile = p) }
                    maybeStartKycPolling(p.kycStatus)
                }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    // ── Document type ─────────────────────────────────────────────────────────

    fun onDocTypeChange(t: KycDocumentType) = _uiState.update { it.copy(selectedDocType = t) }

    // ── Process document from gallery URI ─────────────────────────────────────

    fun processDocumentUri(uri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processError = null, uploadSuccess = false) }

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
            _uiState.update { it.copy(isProcessing = true, processError = null, uploadSuccess = false) }

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
        val docType   = _uiState.value.selectedDocType.value
        val extension = if (mimeType.contains("png", ignoreCase = true)) "png" else "jpg"

        // Build multipart parts — AI Platform endpoint requires multipart/form-data.
        // (JSON base64 causes 415 Unsupported Media Type.)
        val filePart = MultipartBody.Part.createFormData(
            "file",
            "kyc_document.$extension",
            bytes.toRequestBody(mimeType.toMediaType()),
        )
        val docTypePart      = docType.toRequestBody("text/plain".toMediaType())
        val registeredNamePart = _uiState.value.profile?.name
            ?.toRequestBody("text/plain".toMediaType())

        // Server returns 202 immediately; AI pipeline runs in the background.
        // Result arrives via push notification / WebSocket when processing completes.
        runCatching {
            api.processKycDocument(
                file           = filePart,
                documentType   = docTypePart,
                registeredName = registeredNamePart,
            )
        }
            .onSuccess {
                _uiState.update { it.copy(isProcessing = false, uploadSuccess = true) }
                loadProfile() // refresh banner to DOCUMENT_SUBMITTED / AI_PROCESSING
                // Start polling immediately — we know the pipeline is now running
                maybeStartKycPolling(KycStatus.DOCUMENT_SUBMITTED)
            }
            .onFailure { e ->
                _uiState.update { it.copy(isProcessing = false, processError = e.message) }
            }
    }

    // ── KYC status polling ────────────────────────────────────────────────────

    /**
     * Starts a 10-second polling loop when [status] is an intermediate KYC state
     * (DOCUMENT_SUBMITTED or AI_PROCESSING). Cancels any existing loop when the
     * status is already in a terminal state (APPROVED, REJECTED, PENDING, etc.).
     *
     * Idempotent: calling while a poll is already active is a no-op.
     */
    private fun maybeStartKycPolling(status: KycStatus) {
        if (INTERMEDIATE_KYC.contains(status)) {
            if (kycPollingJob?.isActive == true) return // already polling
            kycPollingJob = viewModelScope.launch {
                while (true) {
                    delay(10_000)
                    val userId = authRepository.currentUserId ?: break
                    runCatching { api.getUser(userId) }
                        .onSuccess { p ->
                            _uiState.update { it.copy(profile = p) }
                            if (!INTERMEDIATE_KYC.contains(p.kycStatus)) {
                                return@launch // reached APPROVED / REJECTED / MANUAL_REVIEW
                            }
                        }
                }
            }
        } else {
            kycPollingJob?.cancel()
            kycPollingJob = null
        }
    }

    fun resetResult() = _uiState.update { it.copy(processError = null, uploadSuccess = false) }

    // ── Phone OTP flow ────────────────────────────────────────────────────────

    /** Opens the phone entry sheet, pre-filling any existing (masked) number as placeholder. */
    fun openPhoneSheet() {
        _uiState.update { it.copy(
            phoneStep  = PhoneStep.ENTER_PHONE,
            phoneInput = "",          // don't pre-fill masked value — user types fresh number
            otpInput   = "",
            phoneError = null,
        ) }
    }

    fun onPhoneChange(v: String) = _uiState.update { it.copy(phoneInput = v, phoneError = null) }

    fun onOtpChange(v: String) = _uiState.update { it.copy(otpInput = v, phoneError = null) }

    fun cancelPhone() = _uiState.update { it.copy(
        phoneStep  = PhoneStep.IDLE,
        phoneInput = "",
        otpInput   = "",
        phoneError = null,
    ) }

    /**
     * Kick off Firebase Phone Auth OTP delivery.
     * Must be called from a Composable scope where [activity] is the host Activity
     * — required by [PhoneAuthProvider.verifyPhoneNumber] for reCAPTCHA fallback.
     */
    fun sendOtp(activity: Activity) {
        val phone = _uiState.value.phoneInput.trim()
        if (phone.isBlank()) {
            _uiState.update { it.copy(phoneError = "Enter a phone number (e.g. +919876543210)") }
            return
        }
        _uiState.update { it.copy(phoneStep = PhoneStep.SENDING, phoneError = null) }

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            /** Google Play SMS Retriever auto-verified — skip manual OTP entry. */
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                viewModelScope.launch { signInAndSave(credential) }
            }
            override fun onVerificationFailed(e: FirebaseException) {
                _uiState.update { it.copy(phoneStep = PhoneStep.ENTER_PHONE, phoneError = e.message ?: "Failed to send OTP") }
            }
            override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                _verificationId = id
                _uiState.update { it.copy(phoneStep = PhoneStep.ENTER_OTP) }
            }
        }

        val opts = PhoneAuthOptions.newBuilder(Firebase.auth)
            .setPhoneNumber(phone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(opts)
    }

    /** Validates the 6-digit OTP the user typed and triggers sign-in + backend save. */
    fun verifyOtp() {
        val id  = _verificationId ?: return
        val otp = _uiState.value.otpInput.trim()
        if (otp.length != 6) {
            _uiState.update { it.copy(phoneError = "Enter the 6-digit code") }
            return
        }
        val credential = PhoneAuthProvider.getCredential(id, otp)
        viewModelScope.launch { signInAndSave(credential) }
    }

    /**
     * 1. Signs into Firebase with the phone credential (proves ownership).
     * 2. Immediately signs out of Firebase — we use Keycloak for primary auth.
     * 3. Persists the verified number to AegisPay backend via PATCH /users/{id}/phone.
     */
    private suspend fun signInAndSave(credential: PhoneAuthCredential) {
        _uiState.update { it.copy(phoneStep = PhoneStep.SAVING, phoneError = null) }
        runCatching {
            Firebase.auth.signInWithCredential(credential).awaitFirebase()
            Firebase.auth.signOut() // immediately — Keycloak handles primary session
            val userId = authRepository.currentUserId ?: throw IllegalStateException("Not logged in")
            val phone  = _uiState.value.phoneInput.trim()
            api.updatePhone(userId, mapOf("phone" to phone))
        }
        .onSuccess {
            _uiState.update { it.copy(phoneStep = PhoneStep.SAVED, phoneError = null) }
            loadProfile()
        }
        .onFailure { e ->
            _uiState.update { it.copy(phoneStep = PhoneStep.ENTER_OTP, phoneError = e.message ?: "Verification failed") }
        }
    }

    override fun onCleared() {
        super.onCleared()
        kycPollingJob?.cancel()
    }
}

// ── Firebase Task → coroutine bridge ─────────────────────────────────────────
// Avoids adding kotlinx-coroutines-play-services just for one call.

private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitFirebase(): T =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result -> cont.resume(result) }
        addOnFailureListener { e -> cont.resumeWithException(e) }
    }
