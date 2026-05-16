package com.aegispay.user.service;

import com.aegispay.common.domain.enums.KycStatus;
import com.aegispay.common.domain.exception.AegisPayException;
import com.aegispay.user.domain.dto.*;
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
    public UserResponse register(UserRegistrationRequest request,
                                 String idempotencyKey,
                                 Jwt jwt) {
        String externalId = jwt.getSubject();

        // Idempotent registration — return existing user if already registered
        return userRepository.findByExternalId(externalId)
                .map(existing -> {
                    log.debug("User already registered: externalId={}", externalId);
                    return userMapper.toResponse(existing);
                })
                .orElseGet(() -> {
                    idempotencyService.claim(idempotencyKey);
                    return createUser(request, externalId, jwt);
                });
    }

    private UserResponse createUser(UserRegistrationRequest request,
                                    String externalId,
                                    Jwt jwt) {
        if (userRepository.existsByEmail(request.email())) {
            throw new AegisPayException("EMAIL_ALREADY_EXISTS",
                    "A user with this email address is already registered.",
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
        keycloakAdminService.writeUserAttributes(externalId, user.getId(), user.getTenantId());

        log.info("User registered: userId={} externalId={}", user.getId(), externalId);
        return userMapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getById(UUID userId) {
        return userRepository.findById(userId)
                .map(userMapper::toResponse)
                .orElseThrow(() -> new AegisPayException(
                        "USER_NOT_FOUND",
                        "User not found: " + userId,
                        HttpStatus.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public UserResponse getByExternalId(String externalId) {
        return userRepository.findByExternalId(externalId)
                .map(userMapper::toResponse)
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
        KycDocument doc = kycDocumentRepository.findById(request.documentId())
                .orElseThrow(() -> new AegisPayException(
                        "KYC_DOCUMENT_NOT_FOUND",
                        "KYC document not found: " + request.documentId(),
                        HttpStatus.NOT_FOUND));

        // Transition: DOCUMENT_SUBMITTED → AI_PROCESSING (when AI picks it up)
        if (user.getKycStatus() == KycStatus.DOCUMENT_SUBMITTED) {
            kycStateMachine.assertValidTransition(user.getKycStatus(), KycStatus.AI_PROCESSING);
            user.setKycStatus(KycStatus.AI_PROCESSING);
        }

        KycStatus previous = user.getKycStatus();
        kycStateMachine.assertValidTransition(previous, request.newStatus());

        user.setKycStatus(request.newStatus());

        doc.setOcrStatus("COMPLETED");
        doc.setExtractedData(request.extractedData());
        if (request.tamperedFlag() != null) doc.setTamperedFlag(request.tamperedFlag());
        if (request.qualityScore() != null) doc.setQualityScore(request.qualityScore());
        if (request.rejectionReason() != null) doc.setRejectionReason(request.rejectionReason());

        kycDocumentRepository.save(doc);
        userRepository.save(user);

        OutboxEntry outboxEntry = eventProducer.buildKycStatusChangedEntry(
                user, previous, doc.getDocumentType(), request.rejectionReason());
        outboxEntryRepository.save(outboxEntry);

        log.info("KYC status updated: userId={} status={}", userId, request.newStatus());

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
}
