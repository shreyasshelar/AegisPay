package com.aegispay.user.controller;

import com.aegispay.common.domain.dto.ApiResponse;
import com.aegispay.common.domain.exception.AegisPayException;
import com.aegispay.user.domain.dto.*;
import com.aegispay.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.validation.annotation.Validated;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // ── Registration ───────────────────────────────────────────────────────────

    /**
     * Register a new user. Idempotent on the JWT {@code sub} claim — returns the existing
     * record (HTTP 200) if the caller is already registered, or creates a new one (HTTP 201).
     * The externalId (Keycloak sub) is taken from the JWT; the client must already hold a
     * valid IdP token before calling this endpoint.
     *
     * <h3>Concurrent-registration safety</h3>
     * auth.ts calls this endpoint on every initial sign-in (idempotent call). In the
     * extremely rare scenario where two requests for the same Keycloak {@code sub} arrive
     * simultaneously (two browser tabs opening at the exact same moment), the DB unique
     * constraint on {@code external_id} will reject the second write.  We catch that
     * {@link DataIntegrityViolationException} here and fall back to a plain lookup so both
     * callers ultimately receive the correct existing user record rather than a 500.
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(
            @Valid @RequestBody UserRegistrationRequest request,
            @RequestHeader(value = "X-Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt) {

        try {
            RegistrationResult result = userService.register(request, idempotencyKey, jwt);
            // 201 for genuine new account creation; 200 for idempotent re-fetch of existing record.
            HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
            return ResponseEntity.status(status).body(ApiResponse.ok(result.user()));

        } catch (DataIntegrityViolationException ex) {
            // Two concurrent first-time logins for the same JWT sub raced past the
            // findByExternalId check simultaneously.  The winner's DB write already committed.
            // Retry the read-only lookup so this request also returns the correct user.
            log.warn("register: concurrent race for sub={} — retrying lookup after constraint violation",
                    jwt.getSubject());
            UserResponse existing = userService.getByExternalId(jwt.getSubject());
            return ResponseEntity.ok(ApiResponse.ok(existing));
        }
    }

    // ── Self-service ───────────────────────────────────────────────────────────

    /**
     * Returns the authenticated user's own profile.
     *
     * <h3>Lookup strategy (three paths)</h3>
     * <ol>
     *   <li><b>Normal path</b> — JWT carries {@code aegispay_user_id} (AegisPay domain UUID)
     *       and the row exists: look up by primary key via {@link UserService#getById}.
     *       This is the common case for all users once the async Keycloak attribute
     *       write-back has completed.</li>
     *   <li><b>Bootstrap path</b> — no {@code aegispay_user_id} claim yet (social login or
     *       Keycloak-native first session before write-back): look up by the
     *       {@code external_id} column (Keycloak {@code sub}) via
     *       {@link UserService#getByExternalId}.</li>
     *   <li><b>Stale-attribute path</b> — {@code aegispay_user_id} claim exists but points
     *       to a row that no longer exists (e.g. after a dev DB wipe without clearing the
     *       Keycloak user attribute, or after account deletion): {@link UserService#getById}
     *       throws {@code USER_NOT_FOUND}; the catch block falls back to
     *       {@link UserService#getByExternalId} so the user is never stuck with an
     *       un-dismissible 404.  {@code auth.ts} also always calls {@code /register} on
     *       initial sign-in (idempotent), which re-provisions the record before the first
     *       API call arrives.</li>
     * </ol>
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMe(@AuthenticationPrincipal Jwt jwt) {
        String aeId = jwt.getClaimAsString("aegispay_user_id");
        UserResponse response;
        if (aeId != null && !aeId.isBlank()) {
            // Normal path: JWT carries AegisPay domain UUID → primary-key lookup.
            // Guard against two failure modes:
            //   (a) Malformed UUID — e.g. written by a buggy admin script.
            //   (b) Stale UUID — Keycloak attribute was set before a DB wipe/reset, so the
            //       row no longer exists.  Both cases fall back to external_id lookup so the
            //       user is never stuck with an un-dismissible 404 on their own profile page.
            try {
                response = userService.getById(UUID.fromString(aeId));
            } catch (IllegalArgumentException e) {
                log.warn("getMe: malformed aegispay_user_id='{}' in JWT sub={}; "
                        + "falling back to external_id lookup", aeId, jwt.getSubject());
                response = userService.getByExternalId(jwt.getSubject());
            } catch (AegisPayException e) {
                if ("USER_NOT_FOUND".equals(e.getErrorCode())) {
                    // Stale Keycloak attribute: aegispay_user_id points to a DB row that no
                    // longer exists (e.g. after a dev DB wipe).  Fall back to external_id so
                    // getMe() still works; the next registration cycle will refresh the attribute.
                    log.warn("getMe: aegispay_user_id='{}' not found in DB (stale Keycloak attribute); "
                            + "falling back to external_id lookup for sub={}", aeId, jwt.getSubject());
                    response = userService.getByExternalId(jwt.getSubject());
                } else {
                    throw e;
                }
            }
        } else {
            // Bootstrap path: first session for social/newly-created user.
            response = userService.getByExternalId(jwt.getSubject());
        }
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // ── Back-office ────────────────────────────────────────────────────────────

    /**
     * Paginated user list, optionally filtered by KYC status.
     * Accessible to BACK_OFFICE, ADMIN, and MERCHANT_OPS roles only.
     *
     * <p>Example: {@code GET /api/v1/users?page=0&size=50&kycStatus=PENDING}
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('BACK_OFFICE', 'ADMIN', 'MERCHANT_OPS')")
    public ResponseEntity<ApiResponse<Page<UserResponse>>> listUsers(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false)    String kycStatus) {

        Page<UserResponse> result = userService.listUsers(page, size, kycStatus);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    // ── Per-user endpoints ─────────────────────────────────────────────────────

    /**
     * Lightweight existence check used by transaction-service before initiating a payment.
     * Returns 200 OK if the user exists (by AegisPay domain UUID / primary key), 404 otherwise.
     * No profile data is returned — safe to call without role restriction.
     */
    @GetMapping("/{userId}/exists")
    public ResponseEntity<Void> exists(@PathVariable UUID userId) {
        return userService.existsById(userId)
                ? ResponseEntity.ok().<Void>build()
                : ResponseEntity.notFound().build();
    }

    /**
     * Get a user's full profile by UUID.
     *
     * <h3>Path variable semantics</h3>
     * <ul>
     *   <li>Self (customer, established): pass the {@code aegispay_user_id} JWT claim value
     *       (AegisPay domain UUID) — caught by the second {@code @PreAuthorize} condition.</li>
     *   <li>Self (customer, first session, no {@code aegispay_user_id} yet): pass the
     *       Keycloak {@code sub} — caught by the third condition; {@code resolveUser()}
     *       then routes to {@code getByExternalId}.</li>
     *   <li>Back-office: always passes the internal DB UUID — caught by the role check.</li>
     * </ul>
     *
     * <h3>⚠ First-session social users: use {@code /me} instead</h3>
     * A first-session social user whose {@code session.user.id} was set to the AegisPay UUID
     * during registration (fast path in {@code auth.ts}) but whose JWT does not yet carry
     * {@code aegispay_user_id} (write-back pending) will receive HTTP 403 from this endpoint
     * because neither PreAuthorize condition matches.  All customer-facing frontend components
     * <b>must use {@code GET /api/v1/users/me}</b> (via {@code useMe()} hook) for self-service
     * reads; this endpoint is primarily for back-office and inter-service use.
     *
     * <h3>⚠ Critical: why the lookup direction is decisive</h3>
     * The {@code users.id} (primary key, AegisPay UUID) and {@code users.external_id}
     * (Keycloak sub) are two distinct, non-interchangeable identifiers stored in different
     * columns.  Calling {@code getByExternalId} with an AegisPay UUID — or {@code getById}
     * with a Keycloak sub — always returns 404.
     */
    @GetMapping("/{userId}")
    @PreAuthorize("hasAnyRole('BACK_OFFICE', 'ADMIN', 'MERCHANT_OPS') or " +
                  "#jwt.getClaimAsString('aegispay_user_id') == #userId.toString() or " +
                  "#jwt.subject == #userId.toString()")
    public ResponseEntity<ApiResponse<UserResponse>> getById(
            @PathVariable UUID userId,
            @AuthenticationPrincipal Jwt jwt) {

        UserResponse response = resolveUser(userId, jwt);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Returns just the KYC status for a given user.
     * Used by internal services (transaction-service, risk-engine) as a lightweight check.
     *
     * <p>Follows the same lookup strategy as {@link #getById} — see that method's Javadoc.
     */
    @GetMapping("/{userId}/kyc-status")
    @PreAuthorize("hasAnyRole('BACK_OFFICE', 'ADMIN', 'MERCHANT_OPS') or " +
                  "#jwt.getClaimAsString('aegispay_user_id') == #userId.toString() or " +
                  "#jwt.subject == #userId.toString()")
    public ResponseEntity<ApiResponse<java.util.Map<String, String>>> getKycStatus(
            @PathVariable UUID userId,
            @AuthenticationPrincipal Jwt jwt) {

        UserResponse user = resolveUser(userId, jwt);
        return ResponseEntity.ok(ApiResponse.ok(
                java.util.Map.of("kycStatus", user.kycStatus().name())));
    }

    // ── KYC lifecycle ──────────────────────────────────────────────────────────

    /** Submit a KYC document for analysis. The caller must be the document owner or an ADMIN. */
    @PostMapping("/{userId}/kyc/documents")
    public ResponseEntity<ApiResponse<KycStatusResponse>> submitKycDocument(
            @PathVariable UUID userId,
            @Valid @RequestBody KycDocumentUploadRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        String role    = jwt.getClaimAsString("aegispay_role");
        boolean isAdmin = "ADMIN".equalsIgnoreCase(role) || "BACK_OFFICE".equalsIgnoreCase(role);
        KycStatusResponse response = userService.submitKycDocument(userId, request, jwt.getSubject(), isAdmin);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(response));
    }

    /**
     * User confirms the AI-reviewed KYC data and requests document submission.
     * Transitions the KYC state PENDING → DOCUMENT_SUBMITTED.
     */
    @PatchMapping("/{userId}/kyc")
    public ResponseEntity<ApiResponse<KycStatusResponse>> confirmKycSubmission(
            @PathVariable UUID userId,
            @Valid @RequestBody KycConfirmRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        KycStatusResponse response = userService.confirmKycSubmission(userId, request, jwt.getSubject());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * AI platform callback — updates KYC status with OCR results.
     * In production this should be protected by an internal API key or mTLS.
     * Currently restricted to ADMIN role only.
     */
    @PatchMapping("/{userId}/kyc/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<KycStatusResponse>> processKycCallback(
            @PathVariable UUID userId,
            @Valid @RequestBody KycStatusUpdateRequest request) {

        KycStatusResponse response = userService.processAiCallback(userId, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // ── Device tokens ──────────────────────────────────────────────────────────

    /**
     * Register a device push token (FCM or APNs) for the user.
     * Called once per app session from iOS (APNs token) and Android (FCM token).
     */
    @PostMapping("/{userId}/push-token")
    public ResponseEntity<Void> registerPushToken(
            @PathVariable UUID userId,
            @Valid @RequestBody PushTokenRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        userService.registerPushToken(userId, request, jwt.getSubject());
        return ResponseEntity.noContent().build();
    }

    // ── Internal helpers ───────────────────────────────────────────────────────

    /**
     * Central lookup dispatcher for per-user endpoints.
     *
     * <p>The key invariant: {@code users.id} (AegisPay UUID, primary key) and
     * {@code users.external_id} (Keycloak sub) are stored in different columns and are
     * NOT interchangeable.  The correct lookup method depends on which type of ID the
     * caller passed:
     *
     * <ul>
     *   <li>If {@code userId == jwt.aegispay_user_id} → caller is passing their AegisPay domain
     *       UUID → {@link UserService#getById} (primary-key lookup).</li>
     *   <li>If {@code userId == jwt.subject} → caller is passing their Keycloak sub; this only
     *       happens for social users on first session whose JWT has no {@code aegispay_user_id}
     *       yet → {@link UserService#getByExternalId} (external_id column lookup).</li>
     *   <li>Otherwise (back-office) → caller passes the internal DB UUID → primary-key lookup.</li>
     * </ul>
     */
    private UserResponse resolveUser(UUID userId, Jwt jwt) {
        String aeId = jwt.getClaimAsString("aegispay_user_id");

        if (userId.toString().equals(jwt.getSubject()) && (aeId == null || aeId.isBlank())) {
            // Social login, first session: no aegispay_user_id in JWT yet.
            // userId path variable = Keycloak sub → external_id column lookup.
            return userService.getByExternalId(userId.toString());
        }

        // All other cases: userId is the AegisPay domain UUID (primary key).
        //   - Self call: userId == aeId → primary-key lookup ✓
        //   - Back-office call: userId is internal UUID → primary-key lookup ✓
        //   - Rare: user passes Keycloak sub even though aeId exists → we call getById
        //     which will return 404 (correct — send the right ID next time).
        return userService.getById(userId);
    }
}
