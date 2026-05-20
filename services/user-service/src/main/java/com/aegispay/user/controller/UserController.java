package com.aegispay.user.controller;

import com.aegispay.common.domain.dto.ApiResponse;
import com.aegispay.user.domain.dto.*;
import com.aegispay.user.service.UserService;
import jakarta.validation.Valid;
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

    /**
     * Register a new user. Idempotent — returns the existing record if already registered.
     * The caller must already have a valid IdP JWT; the externalId comes from the JWT subject.
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(
            @Valid @RequestBody UserRegistrationRequest request,
            @RequestHeader(value = "X-Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt) {

        UserResponse response = userService.register(request, idempotencyKey, jwt);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    /** Get the authenticated user's own profile. */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMe(@AuthenticationPrincipal Jwt jwt) {
        String externalId = jwt.getClaimAsString("aegispay_user_id") != null
                ? jwt.getClaimAsString("aegispay_user_id")
                : jwt.getSubject();
        UserResponse response = userService.getByExternalId(externalId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Back-office: paginated user list, optionally filtered by KYC status.
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

    /** Get a user by UUID. Back-office can fetch any user; customers can only fetch themselves. */
    @GetMapping("/{userId}")
    @PreAuthorize("hasAnyRole('BACK_OFFICE', 'ADMIN', 'MERCHANT_OPS') or " +
                  "#jwt.getClaimAsString('aegispay_user_id') == #userId.toString() or " +
                  "#jwt.subject == #userId.toString()")
    public ResponseEntity<ApiResponse<UserResponse>> getById(
            @PathVariable UUID userId,
            @AuthenticationPrincipal Jwt jwt) {
        // Customers pass their aegispay_user_id (external UUID) — look up by external ID.
        // Back-office passes the internal DB UUID — look up by internal ID.
        String aeId = jwt.getClaimAsString("aegispay_user_id");
        boolean isSelf = userId.toString().equals(aeId) || userId.toString().equals(jwt.getSubject());
        UserResponse response = isSelf
                ? userService.getByExternalId(userId.toString())
                : userService.getById(userId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /** Submit a KYC document for analysis. The caller must be the document owner or an ADMIN. */
    @PostMapping("/{userId}/kyc/documents")
    public ResponseEntity<ApiResponse<KycStatusResponse>> submitKycDocument(
            @PathVariable UUID userId,
            @Valid @RequestBody KycDocumentUploadRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        String role = jwt.getClaimAsString("aegispay_role");
        boolean isAdmin = "ADMIN".equalsIgnoreCase(role) || "BACK_OFFICE".equalsIgnoreCase(role);
        KycStatusResponse response = userService.submitKycDocument(userId, request, jwt.getSubject(), isAdmin);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(response));
    }

    /**
     * User confirms the AI-reviewed KYC data and requests document submission.
     * Transitions the KYC state from PENDING → DOCUMENT_SUBMITTED.
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
     * In production this endpoint should be protected by an internal API key or mTLS.
     * For now it is accessible to ADMIN role only.
     */
    @PatchMapping("/{userId}/kyc/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<KycStatusResponse>> processKycCallback(
            @PathVariable UUID userId,
            @Valid @RequestBody KycStatusUpdateRequest request) {

        KycStatusResponse response = userService.processAiCallback(userId, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

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
}
