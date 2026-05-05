package com.aegispay.user.controller;

import com.aegispay.common.domain.dto.ApiResponse;
import com.aegispay.user.domain.dto.*;
import com.aegispay.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
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
        UserResponse response = userService.getByExternalId(jwt.getSubject());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /** Get any user by internal UUID. Requires BACK_OFFICE, ADMIN, or MERCHANT_OPS role. */
    @GetMapping("/{userId}")
    @PreAuthorize("hasAnyRole('BACK_OFFICE', 'ADMIN', 'MERCHANT_OPS')")
    public ResponseEntity<ApiResponse<UserResponse>> getById(@PathVariable UUID userId) {
        UserResponse response = userService.getById(userId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /** Submit a KYC document for analysis. The caller must be the document owner. */
    @PostMapping("/{userId}/kyc/documents")
    public ResponseEntity<ApiResponse<KycStatusResponse>> submitKycDocument(
            @PathVariable UUID userId,
            @Valid @RequestBody KycDocumentUploadRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        KycStatusResponse response = userService.submitKycDocument(userId, request, jwt.getSubject());
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
