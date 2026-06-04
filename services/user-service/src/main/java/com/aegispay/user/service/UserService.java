package com.aegispay.user.service;

import com.aegispay.common.domain.enums.KycStatus;
import com.aegispay.common.domain.exception.AegisPayException;
import com.aegispay.user.domain.dto.KycConfirmRequest;
import com.aegispay.user.domain.dto.KycDocumentUploadRequest;
import com.aegispay.user.domain.dto.KycStatusResponse;
import com.aegispay.user.domain.dto.KycStatusUpdateRequest;
import com.aegispay.user.domain.dto.PushTokenRequest;
import com.aegispay.user.domain.dto.RegistrationResult;
import com.aegispay.user.domain.dto.UserRegistrationRequest;
import com.aegispay.user.domain.dto.UserResponse;
import com.aegispay.user.domain.entity.KycDocument;
import com.aegispay.user.domain.entity.User;
import com.aegispay.user.domain.mapper.UserMapper;
import com.aegispay.user.idempotency.IdempotencyService;
import com.aegispay.user.kafka.UserEventProducer;
import com.aegispay.user.kyc.KycStateMachine;
import com.aegispay.user.outbox.OutboxEntry;
import com.aegispay.user.outbox.OutboxEntryRepository;
import com.aegispay.user.repository.KycDocumentRepository;
import com.aegispay.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final KycDocumentRepository kycDocumentRepository;
    private final OutboxEntryRepository outboxEntryRepository;
    private final UserMapper userMapper;
    private final KycStateMachine kycStateMachine;
    private final IdempotencyService idempotencyService;
    private final UserEventProducer eventProducer;
    private final AiPlatformClient aiPlatformClient;
    private final KeycloakAdminService keycloakAdminService;

    @Transactional
    public RegistrationResult register(UserRegistrationRequest request,
                                       String idempotencyKey,
                                       Jwt jwt) {
        String externalId = jwt.getSubject();

        // Idempotent registration — return existing user if already registered.
        // Returns RegistrationResult so the controller can emit 200 vs 201 correctly.
        return userRepository.findByExternalId(externalId)
                .map(existing -> {
                    log.debug("User already registered (idempotent): externalId={}", externalId);
                    return new RegistrationResult(userMapper.toResponse(existing), false);
                })
                .orElseGet(() -> {
                    idempotencyService.claim(idempotencyKey);
                    return new RegistrationResult(createUser(request, externalId, jwt), true);
                });
    }

    private UserResponse createUser(UserRegistrationRequest request,
                                    String externalId,
                                    Jwt jwt) {
        // Check for email collision BEFORE writing. A collision means a different Keycloak
        // subject already registered with this email (e.g. the same person used email+password
        // first, then tries Google login with the same address). We do NOT silently merge
        // accounts — that could grant access to the wrong user's funds. Instead, return 409
        // so the caller (auth.ts) can detect EMAIL_ALREADY_EXISTS and fall back gracefully.
        if (userRepository.existsByEmail(request.email())) {
            log.warn("Registration blocked: email={} already exists for a different externalId. "
                    + "Requesting externalId={}", request.email(), externalId);
            throw new AegisPayException("EMAIL_ALREADY_EXISTS",
                    "An account with this email address already exists. "
                    + "Please log in with the original sign-in method or contact support.",
                    HttpStatus.CONFLICT);
        }

        String role = jwt.getClaimAsString("aegispay_role");
        String tenantId = jwt.getClaimAsString("aegispay_tenant_id");

        User user = User.builder()
                .externalId(externalId)
                .email(request.email())
                .phone(request.phone())
                .firstName(request.firstName())
                .lastName(request.lastName())
                .role(role != null ? role : "CUSTOMER")
                .tenantId(tenantId != null ? tenantId : request.tenantId())
                .kycStatus(KycStatus.PENDING)
                .build();

        userRepository.save(user);

        OutboxEntry outboxEntry = eventProducer.buildUserRegisteredEntry(user);
        outboxEntryRepository.save(outboxEntry);

        // Write aegispay_user_id + aegispay_tenant_id back to Keycloak asynchronously
        // so subsequent JWTs carry the domain UUID and tenant claim.
        keycloakAdminService.writeUserAttributes(externalId, user.getId(), user.getTenantId(), user.getRole() != null ? user.getRole().name() : "CUSTOMER");

        log.info("User registered: userId={} externalId={}", user.getId(), externalId);
        return userMapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getById(UUID userId) {
        return userRepository.findById(userId)
                .map(userMapper::toResponse)
                .map(r -> enrichWithRejectionReason(r, userId))
                .orElseThrow(() -> new AegisPayException(
                        "USER_NOT_FOUND",
                        "User not found: " + userId,
                        HttpStatus.NOT_FOUND));
    }

    /**
     * Lightweight existence check used by other services (e.g. transaction-service)
     * to validate a payeeId before initiating a payment. Returns true if a user with
     * the given internal UUID exists; false otherwise. No data is exposed.
     */
    @Transactional(readOnly = true)
    public boolean existsById(UUID userId) {
        return userRepository.existsById(userId);
    }

    @Transactional(readOnly = true)
    public UserResponse getByExternalId(String externalId) {
        return userRepository.findByExternalId(externalId)
                .map(u -> enrichWithRejectionReason(userMapper.toResponse(u), u.getId()))
                .orElseThrow(() -> new AegisPayException(
                        "USER_NOT_FOUND",
                        "User not found for subject: " + externalId,
                        HttpStatus.NOT_FOUND));
    }

    @Transactional
    public KycStatusResponse submitKycDocument(UUID userId,
                                               KycDocumentUploadRequest request,
                                               String callerExternalId,
                                               boolean isAdmin) {
        User user = requireUser(userId);
        assertCallerIsOwnerOrAdmin(user, callerExternalId, isAdmin);

        if (kycStateMachine.isTerminal(user.getKycStatus())) {
            throw new AegisPayException("KYC_ALREADY_COMPLETED",
                    "KYC process is already completed for this user.",
                    HttpStatus.CONFLICT);
        }

        KycStatus previous = user.getKycStatus();

        KycDocument doc = KycDocument.builder()
                .userId(userId)
                .documentType(request.documentType())
                .documentRef(request.documentRef())
                .ocrStatus("PENDING")
                .build();
        kycDocumentRepository.save(doc);

        kycStateMachine.assertValidTransition(previous, KycStatus.DOCUMENT_SUBMITTED);
        user.setKycStatus(KycStatus.DOCUMENT_SUBMITTED);
        userRepository.save(user);

        OutboxEntry outboxEntry = eventProducer.buildKycStatusChangedEntry(
                user, previous, request.documentType(), null);
        outboxEntryRepository.save(outboxEntry);

        // Submit to AI platform asynchronously — transitions to AI_PROCESSING on callback
        aiPlatformClient.submitKycDocument(doc, userId.toString());

        return KycStatusResponse.builder()
                .userId(userId)
                .kycStatus(user.getKycStatus())
                .documentType(request.documentType())
                .ocrStatus(doc.getOcrStatus())
                .tamperedFlag(false)
                .build();
    }

    @Transactional
    public KycStatusResponse processAiCallback(UUID userId, KycStatusUpdateRequest request) {
        User user = requireUser(userId);

        // Don't allow re-processing a user whose KYC already reached a terminal state.
        if (kycStateMachine.isTerminal(user.getKycStatus())) {
            throw new AegisPayException("KYC_ALREADY_COMPLETED",
                    "KYC process is already completed for this user.",
                    HttpStatus.CONFLICT);
        }

        // ── Preliminary status updates (DOCUMENT_SUBMITTED / AI_PROCESSING) ─────
        // These are sent by the AI Platform at the START of the async pipeline to lock
        // the user out of re-uploading while processing is in progress.  No KycDocument
        // record is created yet — the final callback (APPROVED / REJECTED / MANUAL_REVIEW)
        // will create it with the full OCR and validation data.
        if (request.newStatus() == KycStatus.DOCUMENT_SUBMITTED
                || request.newStatus() == KycStatus.AI_PROCESSING) {
            KycStatus previous = user.getKycStatus();
            user.setKycStatus(request.newStatus());
            userRepository.save(user);

            OutboxEntry outboxEntry = eventProducer.buildKycStatusChangedEntry(
                    user, previous, request.documentType(), null);
            outboxEntryRepository.save(outboxEntry);

            log.info("KYC preliminary status update: userId={} {} → {}",
                    userId, previous, request.newStatus());

            return KycStatusResponse.builder()
                    .userId(userId)
                    .kycStatus(user.getKycStatus())
                    .documentType(request.documentType())
                    .ocrStatus("PENDING")
                    .tamperedFlag(false)
                    .rejectionReason(null)
                    .build();
        }

        // ── Resolve or create the KycDocument record ───────────────────────────
        // documentId is null in the async direct-upload flow (browser → AI Platform →
        // callback). In that case we create the document record here rather than
        // requiring User Service to pre-create it before the AI pipeline runs.
        KycDocument doc;
        if (request.documentId() != null) {
            doc = kycDocumentRepository.findById(request.documentId())
                    .orElseThrow(() -> new AegisPayException(
                            "KYC_DOCUMENT_NOT_FOUND",
                            "KYC document not found: " + request.documentId(),
                            HttpStatus.NOT_FOUND));
        } else {
            // Async direct-upload: create a document record now so the DB has a full audit trail.
            doc = KycDocument.builder()
                    .userId(userId)
                    .documentType(request.documentType() != null ? request.documentType() : "UNKNOWN")
                    .documentRef("ai-async-upload")
                    .ocrStatus("PENDING")
                    .tamperedFlag(false)
                    .build();
            kycDocumentRepository.save(doc);
        }

        // ── Apply final status directly ────────────────────────────────────────
        // This endpoint is protected by ADMIN role (JWT) or InternalApiKeyFilter
        // (service-to-service), so it is trusted.  We skip intermediate state machine
        // transitions (PENDING → DOCUMENT_SUBMITTED → AI_PROCESSING) to allow the async
        // flow to jump directly to the final decision status regardless of where the user
        // currently sits in the state machine.  The only guard we keep is the terminal check
        // above — APPROVED and REJECTED are immutable.
        KycStatus previous = user.getKycStatus();
        user.setKycStatus(request.newStatus());

        doc.setOcrStatus("COMPLETED");
        if (request.extractedData() != null) doc.setExtractedData(request.extractedData());
        if (request.tamperedFlag() != null) doc.setTamperedFlag(request.tamperedFlag());
        if (request.qualityScore() != null) doc.setQualityScore(request.qualityScore());
        if (request.rejectionReason() != null) doc.setRejectionReason(request.rejectionReason());

        kycDocumentRepository.save(doc);
        userRepository.save(user);

        // Publish KycStatusChangedEvent via Outbox → Kafka → Notification Service →
        // WebSocket push so the frontend and mobile apps receive real-time updates.
        OutboxEntry outboxEntry = eventProducer.buildKycStatusChangedEntry(
                user, previous, doc.getDocumentType(), request.rejectionReason());
        outboxEntryRepository.save(outboxEntry);

        log.info("KYC status updated via AI callback: userId={} previous={} new={}",
                userId, previous, request.newStatus());

        return KycStatusResponse.builder()
                .userId(userId)
                .kycStatus(user.getKycStatus())
                .documentType(doc.getDocumentType())
                .ocrStatus(doc.getOcrStatus())
                .tamperedFlag(doc.isTamperedFlag())
                .rejectionReason(doc.getRejectionReason())
                .build();
    }

    /**
     * User-facing KYC confirmation: transitions PENDING → DOCUMENT_SUBMITTED
     * and creates a placeholder KYC document record.
     * The actual AI processing was done client-side via ai-platform; this call
     * records the user's intent to submit and triggers the outbox event.
     */
    @Transactional
    public KycStatusResponse confirmKycSubmission(UUID userId,
                                                  KycConfirmRequest request,
                                                  String callerExternalId) {
        User user = requireUser(userId);

        // Enforce ownership — users can only submit their own KYC
        if (!user.getExternalId().equals(callerExternalId)) {
            throw new AegisPayException("FORBIDDEN",
                    "You can only submit KYC for your own account.", HttpStatus.FORBIDDEN);
        }

        kycStateMachine.assertValidTransition(user.getKycStatus(), KycStatus.DOCUMENT_SUBMITTED);
        user.setKycStatus(KycStatus.DOCUMENT_SUBMITTED);
        userRepository.save(user);

        // Create a minimal document record (documentRef will be updated by the AI pipeline)
        KycDocument doc = KycDocument.builder()
                .userId(user.getId())
                .documentType(request.documentType())
                .documentRef("pending")
                .ocrStatus("PENDING")
                .tamperedFlag(false)
                .build();
        kycDocumentRepository.save(doc);

        OutboxEntry outboxEntry = eventProducer.buildKycStatusChangedEntry(
                user, KycStatus.PENDING, request.documentType(), null);
        outboxEntryRepository.save(outboxEntry);

        log.info("KYC submission confirmed: userId={} docType={}", userId, request.documentType());

        return KycStatusResponse.builder()
                .userId(userId)
                .kycStatus(user.getKycStatus())
                .documentType(request.documentType())
                .ocrStatus("PENDING")
                .tamperedFlag(false)
                .build();
    }

    /**
     * Adds or replaces the phone number on file for the given user.
     *
     * <p>This is the primary path for SSO users (Google, GitHub, Apple, Microsoft) who
     * registered without a phone number because OAuth providers don't supply one.
     * Without a phone on file, SMS notifications are silently skipped.
     *
     * <p>Publishes a {@code UserContactUpdatedEvent} via the transactional outbox so the
     * notification-service can update its {@code UserContactDocument} read-model and start
     * delivering SMS for future transaction events.
     *
     * @param userId            the AegisPay domain UUID of the user to update
     * @param phone             new phone in international format (e.g. {@code +919876543210});
     *                          null / blank clears the number
     * @param callerExternalId  Keycloak {@code sub} of the authenticated caller — must match
     *                          the user's own {@code externalId} (self-service only)
     */
    @Transactional
    public UserResponse updatePhone(UUID userId, String phone, String callerExternalId) {
        User user = requireUser(userId);
        if (!user.getExternalId().equals(callerExternalId)) {
            throw new AegisPayException("FORBIDDEN",
                    "You can only update your own phone number.", HttpStatus.FORBIDDEN);
        }

        boolean newPhonePresent = phone != null && !phone.isBlank();
        user.setPhone(newPhonePresent ? phone : null);

        // Auto-enable SMS when a verified phone is first saved.
        // Auto-disable when the number is removed (can't receive SMS without a number).
        if (newPhonePresent) {
            user.setSmsNotificationsEnabled(true);
        } else {
            user.setSmsNotificationsEnabled(false);
        }

        userRepository.save(user);

        OutboxEntry outboxEntry = eventProducer.buildUserContactUpdatedEntry(user);
        outboxEntryRepository.save(outboxEntry);

        log.info("Phone updated: userId={} hasPhone={} smsEnabled={}",
                userId, newPhonePresent, user.isSmsNotificationsEnabled());
        return userMapper.toResponse(user);
    }

    /**
     * Toggles SMS notification preference for the user.
     *
     * <p>Enabling SMS requires a verified phone number to already be on file.
     * If the user has no phone, enabling SMS returns 422 — the client should prevent
     * this state, but the server is the authoritative guard.
     *
     * <p>Disabling is always allowed regardless of phone status.
     *
     * @param userId            the AegisPay domain UUID of the user to update
     * @param enabled           new SMS preference value
     * @param callerExternalId  Keycloak {@code sub} — self-service only
     */
    @Transactional
    public UserResponse updateSmsPreference(UUID userId, boolean enabled, String callerExternalId) {
        User user = requireUser(userId);
        if (!user.getExternalId().equals(callerExternalId)) {
            throw new AegisPayException("FORBIDDEN",
                    "You can only update your own notification preferences.", HttpStatus.FORBIDDEN);
        }

        if (enabled && (user.getPhone() == null || user.getPhone().isBlank())) {
            throw new AegisPayException("PHONE_REQUIRED",
                    "Add and verify a phone number before enabling SMS notifications.",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        user.setSmsNotificationsEnabled(enabled);
        userRepository.save(user);

        // Propagate preference change to notification-service read-model via Outbox → Kafka.
        OutboxEntry outboxEntry = eventProducer.buildUserContactUpdatedEntry(user);
        outboxEntryRepository.save(outboxEntry);

        log.info("SMS preference updated: userId={} smsEnabled={}", userId, enabled);
        return userMapper.toResponse(user);
    }

    /**
     * Stores (or replaces) the user's push notification device token.
     * Idempotent — safe to call on every app launch.
     */
    @Transactional
    public void registerPushToken(UUID userId, PushTokenRequest request, String callerExternalId) {
        User user = requireUser(userId);
        if (!user.getExternalId().equals(callerExternalId)) {
            throw new AegisPayException("FORBIDDEN",
                    "You can only register push tokens for your own account.", HttpStatus.FORBIDDEN);
        }
        user.setPushToken(request.token());
        user.setPushTokenPlatform(request.platform());
        userRepository.save(user);
        log.debug("Push token registered: userId={} platform={}", userId, request.platform());
    }

    // ── Back-office: list users ────────────────────────────────────────────────

    /**
     * Returns a paginated list of users, optionally filtered by KYC status.
     * Accessible to BACK_OFFICE, ADMIN, and MERCHANT_OPS roles only.
     *
     * @param page      0-based page index
     * @param size      page size (capped at 100)
     * @param kycStatus optional filter — any value from {@link KycStatus}
     */
    @Transactional(readOnly = true)
    public Page<UserResponse> listUsers(int page, int size, String kycStatus) {
        var pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("createdAt").descending());

        Page<User> users = (kycStatus != null && !kycStatus.isBlank())
                ? userRepository.findAllByKycStatusOrderByCreatedAtDesc(
                        KycStatus.valueOf(kycStatus.toUpperCase()), pageable)
                : userRepository.findAllByOrderByCreatedAtDesc(pageable);

        return users.map(userMapper::toResponse);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Asserts that the authenticated caller is the owner of {@code userId} or has ADMIN role.
     * Used by the Phone OTP endpoints which must be self-service.
     */
    public void assertSelfOrAdmin(java.util.UUID userId, org.springframework.security.oauth2.jwt.Jwt jwt) {
        String claimId = jwt.getClaimAsString("aegispay_user_id");
        String sub     = jwt.getSubject();
        boolean isAdmin = jwt.getClaimAsStringList("realm_access") != null
                || (jwt.getClaim("realm_access") instanceof java.util.Map<?,?> ra
                        && ra.get("roles") instanceof java.util.List<?> roles
                        && roles.contains("ADMIN"));
        // Self-check: the caller's AegisPay UUID or Keycloak sub must match
        boolean isSelf = userId.toString().equals(claimId) || userId.toString().equals(sub);
        if (!isSelf && !isAdmin) {
            // Fall back to DB lookup by sub (social users whose JWT lacks aegispay_user_id)
            isSelf = userRepository.findById(userId)
                    .map(u -> u.getExternalId().equals(sub))
                    .orElse(false);
        }
        if (!isSelf && !isAdmin) {
            throw new AegisPayException("FORBIDDEN",
                    "You can only manage your own phone number.", HttpStatus.FORBIDDEN);
        }
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AegisPayException(
                        "USER_NOT_FOUND", "User not found: " + userId, HttpStatus.NOT_FOUND));
    }

    private void assertCallerIsOwnerOrAdmin(User user, String callerExternalId, boolean isAdmin) {
        boolean isOwner = user.getExternalId().equals(callerExternalId);
        if (!isOwner && !isAdmin) {
            throw new AegisPayException("FORBIDDEN",
                    "You do not have permission to modify this user's KYC.",
                    HttpStatus.FORBIDDEN);
        }
    }

    /**
     * Enriches a {@link UserResponse} with the rejection reason from the user's
     * most recent {@link com.aegispay.user.domain.entity.KycDocument}.
     *
     * <p>Only performs the extra DB lookup when {@code kycStatus == REJECTED}; for
     * all other statuses the response is returned unchanged (no extra query fired).
     */
    private UserResponse enrichWithRejectionReason(UserResponse response, UUID userId) {
        if (response.kycStatus() != com.aegispay.common.domain.enums.KycStatus.REJECTED) {
            return response;
        }
        return kycDocumentRepository
                .findFirstByUserIdOrderByCreatedAtDesc(userId)
                .map(doc -> response.withRejectionReason(doc.getRejectionReason()))
                .orElse(response);
    }
}