package com.aegispay.android.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Date

// ── Transaction ───────────────────────────────────────────────────────────────

enum class TransactionStatus {
    INITIATED, RESERVED, RISK_CLEARED, PROCESSING, COMPLETED, FAILED, ROLLED_BACK;

    val isTerminal get() = this == COMPLETED || this == FAILED || this == ROLLED_BACK

    val displayLabel get() = when (this) {
        INITIATED   -> "Initiated"
        RESERVED    -> "Funds Reserved"
        RISK_CLEARED -> "Risk Cleared"
        PROCESSING  -> "Processing"
        COMPLETED   -> "Completed"
        FAILED      -> "Failed"
        ROLLED_BACK -> "Rolled Back"
    }
}

@JsonClass(generateAdapter = true)
data class Transaction(
    @Json(name = "transactionId") val transactionId: String,
    @Json(name = "payerId")       val payerId:       String,
    @Json(name = "payeeId")       val payeeId:       String,
    @Json(name = "amount")        val amount:        Double,
    @Json(name = "currency")      val currency:      String,
    @Json(name = "status")        val status:        TransactionStatus,
    @Json(name = "initiatedAt")   val initiatedAt:   Date,
    @Json(name = "completedAt")   val completedAt:   Date?,
    @Json(name = "failureReason") val failureReason: String?,
    @Json(name = "note")          val note:          String?,
)

@JsonClass(generateAdapter = true)
data class PagedTransactions(
    @Json(name = "content")       val content:       List<Transaction>,
    @Json(name = "totalElements") val totalElements: Int,
    @Json(name = "totalPages")    val totalPages:    Int,
    @Json(name = "number")        val number:        Int,
    @Json(name = "size")          val size:          Int,
    @Json(name = "last")          val last:          Boolean,
)

@JsonClass(generateAdapter = true)
data class CreateTransactionRequest(
    @Json(name = "payeeId")  val payeeId:  String,
    @Json(name = "amount")   val amount:   Double,
    @Json(name = "currency") val currency: String,
    @Json(name = "note")     val note:     String?,
)

// ── Account / Ledger ──────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class Account(
    @Json(name = "id")               val id:               String,
    @Json(name = "userId")           val userId:           String,
    @Json(name = "currency")         val currency:         String,
    @Json(name = "availableBalance") val availableBalance: Double,
    @Json(name = "reservedBalance")  val reservedBalance:  Double,
)

// ── Wallet top-up ─────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class TopUpIntentRequest(
    @Json(name = "amount")   val amount:   Double,
    @Json(name = "currency") val currency: String,
)

@JsonClass(generateAdapter = true)
data class TopUpIntentResponse(
    @Json(name = "paymentIntentId") val paymentIntentId: String,
    @Json(name = "clientSecret")    val clientSecret:    String,
    @Json(name = "amount")          val amount:          Double,
    @Json(name = "currency")        val currency:        String,
)

@JsonClass(generateAdapter = true)
data class TopUpConfirmRequest(
    @Json(name = "paymentIntentId") val paymentIntentId: String,
)

// ── User / KYC ────────────────────────────────────────────────────────────────

enum class KycStatus {
    PENDING, DOCUMENT_SUBMITTED, AI_PROCESSING, APPROVED, REJECTED, MANUAL_REVIEW
}

@JsonClass(generateAdapter = true)
data class UserRegistrationRequest(
    @Json(name = "firstName") val firstName: String,
    @Json(name = "lastName")  val lastName:  String,
    @Json(name = "email")     val email:     String,
)

@JsonClass(generateAdapter = true)
data class UserProfile(
    @Json(name = "id")        val id:        String,
    @Json(name = "email")     val email:     String,
    @Json(name = "name")      val name:      String?,
    @Json(name = "kycStatus") val kycStatus: KycStatus,
    @Json(name = "role")      val role:      String,
)

enum class KycDocumentType(val value: String, val label: String) {
    NATIONAL_ID("NATIONAL_ID",     "National ID / Aadhaar"),
    PASSPORT("PASSPORT",           "Passport"),
    DRIVING_LICENSE("DRIVING_LICENSE", "Driver's License"),
    PAN_CARD("PAN_CARD",           "PAN Card"),
}

@JsonClass(generateAdapter = true)
data class KycDocumentRequest(
    @Json(name = "documentType")    val documentType:    String,
    @Json(name = "base64ImageData") val base64ImageData: String,
    @Json(name = "mimeType")        val mimeType:        String,
)

@JsonClass(generateAdapter = true)
data class KycQuality(
    @Json(name = "overallScore")    val overallScore:    Double,
    @Json(name = "sharpness")       val sharpness:       Double,
    @Json(name = "brightness")      val brightness:      Double,
    @Json(name = "crop")            val crop:            Double,
    @Json(name = "glare")           val glare:           Double,
    @Json(name = "acceptable")      val acceptable:      Boolean,
    @Json(name = "rejectionReason") val rejectionReason: String?,
)

@JsonClass(generateAdapter = true)
data class KycTampering(
    @Json(name = "tampered")   val tampered:   Boolean,
    @Json(name = "confidence") val confidence: Double,
    @Json(name = "indicators") val indicators: List<String>,
)

@JsonClass(generateAdapter = true)
data class KycExtractedData(
    @Json(name = "fullName")       val fullName:       String?,
    @Json(name = "dateOfBirth")    val dateOfBirth:    String?,
    @Json(name = "documentNumber") val documentNumber: String?,
    @Json(name = "documentType")   val documentType:   String?,
    @Json(name = "expiryDate")     val expiryDate:     String?,
    @Json(name = "address")        val address:        String?,
)

@JsonClass(generateAdapter = true)
data class KycProcessingResult(
    @Json(name = "status")          val status:          String,
    @Json(name = "rejectionCode")   val rejectionCode:   String?,
    @Json(name = "rejectionReason") val rejectionReason: String?,
    @Json(name = "quality")         val quality:         KycQuality,
    @Json(name = "tampering")       val tampering:       KycTampering?,
    @Json(name = "extractedData")   val extractedData:   KycExtractedData?,
)

// ── Notifications ─────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class PushNotification(
    @Json(name = "id")            val id:            String,
    @Json(name = "type")          val type:          String,
    @Json(name = "title")         val title:         String,
    @Json(name = "body")          val body:          String,
    @Json(name = "createdAt")     val createdAt:     Date,
    @Json(name = "transactionId") val transactionId: String?,
)

@JsonClass(generateAdapter = true)
data class PagedNotifications(
    @Json(name = "content")       val content:       List<PushNotification>,
    @Json(name = "totalElements") val totalElements: Int,
    @Json(name = "last")          val last:          Boolean,
)

// ── Risk / Back-office ────────────────────────────────────────────────────────

enum class RiskDecision {
    APPROVED, REVIEW, REJECTED;

    val displayLabel get() = when (this) {
        APPROVED -> "Approved"
        REVIEW   -> "Review"
        REJECTED -> "Rejected"
    }
}

/**
 * ruleFlags arrives as a JSON object with arbitrary values, e.g. { "VELOCITY": true }.
 * We use KotlinJsonAdapterFactory (reflection) for this class so that
 * Map<String, Any> is supported without code-gen.
 */
data class RiskCase(
    val id:             String,
    val transactionId:  String,
    val userId:         String,
    val riskScore:      Int,
    val decision:       RiskDecision,
    val ruleFlags:      Map<String, @JvmSuppressWildcards Any> = emptyMap(),
    val ragExplanation: String?,
    val createdAt:      String,
) {
    /** Keys of the ruleFlags object — i.e. the names of rules that fired. */
    val ruleFlagKeys: List<String> get() = ruleFlags.keys.toList()
}

@JsonClass(generateAdapter = true)
data class PagedRiskCases(
    @Json(name = "content")       val content:       List<RiskCase>,
    @Json(name = "totalElements") val totalElements: Int,
    @Json(name = "last")          val last:          Boolean,
)

@JsonClass(generateAdapter = true)
data class PagedUsers(
    @Json(name = "content")       val content:       List<UserProfile>,
    @Json(name = "totalElements") val totalElements: Int,
    @Json(name = "last")          val last:          Boolean,
)

@JsonClass(generateAdapter = true)
data class TriageIncidentRequest(
    @Json(name = "serviceName")          val serviceName:         String,
    @Json(name = "incidentDescription")  val incidentDescription: String,
)

@JsonClass(generateAdapter = true)
data class TriageIncidentResponse(
    @Json(name = "analysis") val analysis: String,
)

// ── AI ────────────────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class FraudExplainRequest(
    @Json(name = "transactionId") val transactionId: String,
    @Json(name = "riskScore")     val riskScore:     Int,
    @Json(name = "flaggedRules")  val flaggedRules:  List<String>,
)

@JsonClass(generateAdapter = true)
data class FraudExplainResponse(
    @Json(name = "transactionId") val transactionId: String,
    @Json(name = "explanation")   val explanation:   String,
)

@JsonClass(generateAdapter = true)
data class ErrorResolutionRequest(
    @Json(name = "errorCode")    val errorCode:    String,
    @Json(name = "errorMessage") val errorMessage: String?,
)

@JsonClass(generateAdapter = true)
data class ErrorResolutionResponse(
    @Json(name = "errorCode")  val errorCode:  String,
    @Json(name = "resolution") val resolution: String,
)
