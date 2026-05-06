package com.aegispay.user.service;

import com.aegispay.common.domain.enums.KycStatus;
import com.aegispay.common.domain.exception.AegisPayException;
import com.aegispay.user.domain.dto.KycDocumentUploadRequest;
import com.aegispay.user.domain.dto.KycStatusResponse;
import com.aegispay.user.domain.dto.UserRegistrationRequest;
import com.aegispay.user.domain.dto.UserResponse;
import com.aegispay.user.domain.entity.User;
import com.aegispay.user.domain.mapper.UserMapperImpl;
import com.aegispay.user.idempotency.IdempotencyService;
import com.aegispay.user.kafka.UserEventProducer;
import com.aegispay.user.kyc.KycStateMachine;
import com.aegispay.user.outbox.OutboxEntry;
import com.aegispay.user.outbox.OutboxEntryRepository;
import com.aegispay.user.repository.KycDocumentRepository;
import com.aegispay.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private KycDocumentRepository kycDocumentRepository;
    @Mock private OutboxEntryRepository outboxEntryRepository;
    @Spy  private UserMapperImpl userMapper;
    @Spy  private KycStateMachine kycStateMachine;
    @Mock private IdempotencyService idempotencyService;
    @Mock private UserEventProducer eventProducer;
    @Mock private AiPlatformClient aiPlatformClient;

    @InjectMocks
    private UserService userService;

    private Jwt jwt;

    @BeforeEach
    void setUp() {
        jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("ext-user-123")
                .claim("aegispay_role", "CUSTOMER")
                .claim("aegispay_tenant_id", "tenant-1")
                .build();
    }

    @Test
    void registerCreatesNewUser() {
        when(userRepository.findByExternalId("ext-user-123")).thenReturn(Optional.empty());
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);

        User saved = buildUser();
        when(userRepository.save(any(User.class))).thenReturn(saved);
        when(outboxEntryRepository.save(any())).thenReturn(null);
        when(eventProducer.buildUserRegisteredEntry(any())).thenReturn(OutboxEntry.builder()
                .aggregateId(saved.getId().toString()).aggregateType("User")
                .eventType("UserRegisteredEvent").topic("user.registered")
                .messageKey(saved.getId().toString()).payload("{}").build());

        UserResponse response = userService.register(
                new UserRegistrationRequest("alice@example.com", "+919876543210",
                        "Alice", "Smith", "tenant-1"),
                "idem-key-1", jwt);

        assertThat(response).isNotNull();
        verify(idempotencyService).claim("idem-key-1");
        verify(userRepository).save(any(User.class));
        verify(outboxEntryRepository).save(any());
    }

    @Test
    void registerReturnsExistingUserWhenAlreadyRegistered() {
        User existing = buildUser();
        when(userRepository.findByExternalId("ext-user-123")).thenReturn(Optional.of(existing));

        UserResponse response = userService.register(
                new UserRegistrationRequest("alice@example.com", null, "Alice", "Smith", null),
                "idem-key-2", jwt);

        assertThat(response).isNotNull();
        verifyNoInteractions(idempotencyService);
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerThrowsConflictWhenEmailTaken() {
        when(userRepository.findByExternalId("ext-user-123")).thenReturn(Optional.empty());
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(
                new UserRegistrationRequest("alice@example.com", null, "Alice", "Smith", null),
                "idem-key-3", jwt))
                .isInstanceOf(AegisPayException.class)
                .hasMessageContaining("email");
    }

    @Test
    void getByIdThrowsWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getById(id))
                .isInstanceOf(AegisPayException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void submitKycDocumentTransitionsToDocumentSubmitted() {
        User user = buildUser();
        UUID userId = user.getId();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(kycDocumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.save(any())).thenReturn(user);
        when(outboxEntryRepository.save(any())).thenReturn(null);
        when(eventProducer.buildKycStatusChangedEntry(any(), any(), any(), any()))
                .thenReturn(OutboxEntry.builder().aggregateId(userId.toString())
                        .aggregateType("User").eventType("KycStatusChangedEvent")
                        .topic("kyc.status.changed").messageKey(userId.toString())
                        .payload("{}").build());

        KycStatusResponse response = userService.submitKycDocument(
                userId,
                new KycDocumentUploadRequest("AADHAAR", "s3://bucket/doc.jpg"),
                "ext-user-123",
                false);

        assertThat(response.kycStatus()).isEqualTo(KycStatus.DOCUMENT_SUBMITTED);
        verify(aiPlatformClient).submitKycDocument(any(), eq(userId.toString()));
    }

    private User buildUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .externalId("ext-user-123")
                .email("alice@example.com")
                .firstName("Alice")
                .lastName("Smith")
                .role("CUSTOMER")
                .tenantId("tenant-1")
                .kycStatus(KycStatus.PENDING)
                .build();
    }
}
