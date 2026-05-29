package com.aegispay.android.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.*

/**
 * Retrofit interface — mirrors all endpoints used by the frontend.
 * All calls are suspend functions; errors surface as [ApiException].
 */
interface AegisApiService {

    // ── Transactions ──────────────────────────────────────────────────────────

    @GET("api/v1/transactions")
    suspend fun listTransactions(
        @Query("page")   page:   Int     = 0,
        @Query("size")   size:   Int     = 20,
        @Query("status") status: String? = null,
    ): PagedTransactions

    @GET("api/v1/transactions/{id}")
    suspend fun getTransaction(@Path("id") id: String): Transaction

    @POST("api/v1/transactions")
    suspend fun createTransaction(
        @Header("X-Idempotency-Key") idempotencyKey: String,
        @Body request: CreateTransactionRequest,
    ): Transaction

    // ── Ledger ────────────────────────────────────────────────────────────────

    @GET("api/v1/ledger/accounts/{userId}")
    suspend fun getAccount(@Path("userId") userId: String): Account

    @GET("api/v1/ledger/accounts/me")
    suspend fun getMyAccount(): List<Account>

    // ── Wallet top-up ─────────────────────────────────────────────────────────

    /** Step 1: create a Stripe PaymentIntent — returns clientSecret for SDK confirmation. */
    @POST("api/v1/ledger/topup/intent")
    suspend fun createTopUpIntent(@Body request: TopUpIntentRequest): TopUpIntentResponse

    /** Step 2: notify backend that payment succeeded — credits the ledger balance. */
    @POST("api/v1/ledger/topup/confirm")
    suspend fun confirmTopUp(@Body request: TopUpConfirmRequest): Unit

    // ── Users ─────────────────────────────────────────────────────────────────

    @GET("api/v1/users/{id}")
    suspend fun getUser(@Path("id") id: String): UserProfile

    /** Resolves the caller's AegisPay profile using the JWT subject (Keycloak sub). */
    @GET("api/v1/users/me")
    suspend fun getMe(): UserProfile

    /**
     * Registers a new AegisPay user for a first-time Keycloak login.
     * Idempotent — returns the existing record if already registered.
     */
    @POST("api/v1/users/register")
    suspend fun registerUser(
        @Header("X-Idempotency-Key") idempotencyKey: String,
        @Body request: UserRegistrationRequest,
    ): UserProfile

    /** Back-office: paginated user list, optionally filtered by KYC status. */
    @GET("api/v1/users")
    suspend fun listUsers(
        @Query("page")      page:      Int     = 0,
        @Query("size")      size:      Int     = 50,
        @Query("kycStatus") kycStatus: String? = null,
    ): PagedUsers

    /**
     * Submits a KYC document for async AI processing.
     * Returns 202 Accepted immediately; the result is delivered via push notification
     * / WebSocket when the background pipeline finishes (up to ~6 min).
     */
    @Multipart
    @POST("api/v1/ai/kyc/process")
    suspend fun processKycDocument(
        @Part                   file:          MultipartBody.Part,
        @Part("documentType")   documentType:  RequestBody,
        @Part("registeredName") registeredName: RequestBody?,
    ): Unit

    /**
     * Add or replace the authenticated user's phone number (E.164 format).
     * Called after Firebase Phone Auth OTP verification to persist the verified number.
     * Pass null value to remove an existing number.
     */
    @PATCH("api/v1/users/{userId}/phone")
    suspend fun updatePhone(
        @Path("userId") userId: String,
        @Body body: Map<String, String?>,
    ): UserProfile

    @POST("api/v1/users/{id}/push-token")
    suspend fun registerPushToken(
        @Path("id") userId: String,
        @Body body: Map<String, String>,
    ): Unit

    // ── Notifications ─────────────────────────────────────────────────────────

    @GET("api/v1/notifications")
    suspend fun listNotifications(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 30,
    ): PagedNotifications

    // ── Risk / Back-office ────────────────────────────────────────────────────

    @GET("api/v1/risk/cases")
    suspend fun listRiskCases(
        @Query("page")     page:     Int     = 0,
        @Query("size")     size:     Int     = 50,
        @Query("decision") decision: String? = null,
    ): PagedRiskCases

    // ── AI ────────────────────────────────────────────────────────────────────

    @POST("api/v1/ai/fraud/explain")
    suspend fun explainFraud(@Body request: FraudExplainRequest): FraudExplainResponse

    @POST("api/v1/ai/errors/resolve")
    suspend fun resolveError(@Body request: ErrorResolutionRequest): ErrorResolutionResponse

    @POST("api/v1/ai/incidents/triage")
    suspend fun triageIncident(@Body request: TriageIncidentRequest): TriageIncidentResponse
}
