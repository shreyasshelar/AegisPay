package com.aegispay.android.network

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

    // ── Users ─────────────────────────────────────────────────────────────────

    @GET("api/v1/users/{id}")
    suspend fun getUser(@Path("id") id: String): UserProfile

    @POST("api/v1/ai/kyc/process")
    suspend fun processKycDocument(@Body request: KycDocumentRequest): KycProcessingResult

    @PATCH("api/v1/users/{id}/kyc")
    suspend fun confirmKyc(
        @Path("id") userId: String,
        @Body body: Map<String, String>,
    ): Unit

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
