package com.aegispay.notification.kafka;

import com.aegispay.common.domain.event.UserRegisteredEvent;
import com.aegispay.notification.dispatcher.NotificationDispatcher;
import com.aegispay.notification.domain.document.UserContactDocument;
import com.aegispay.notification.repository.UserContactRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link UserRegisteredConsumer}.
 *
 * <p>Key invariant being tested: the consumer must use upsert semantics so that
 * an OTP-verified phone number is never overwritten if {@code user.registered}
 * is re-delivered by Kafka (at-least-once delivery guarantee).
 */
@ExtendWith(MockitoExtension.class)
class UserRegisteredConsumerTest {

    @Mock NotificationDispatcher   dispatcher;
    @Mock UserContactRepository    contactRepository;

    UserRegisteredConsumer consumer;
    ObjectMapper           objectMapper;

    static final UUID   USER_ID    = UUID.randomUUID();
    static final String EMAIL      = "alice@example.com";
    static final String MASKED     = "a***@example.com";
    static final String PHONE      = "+919876543210";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        consumer = new UserRegisteredConsumer(dispatcher, contactRepository, objectMapper);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ConsumerRecord<String, String> record(UserRegisteredEvent event) throws Exception {
        return new ConsumerRecord<>("user.registered", 0, 0L,
                USER_ID.toString(), objectMapper.writeValueAsString(event));
    }

    private UserRegisteredEvent eventWithPhone(String phone) {
        return UserRegisteredEvent.builder()
                .eventId(UUID.randomUUID())
                .occurredAt(Instant.now())
                .schemaVersion(1)
                .userId(USER_ID)
                .email(EMAIL)
                .maskedEmail(MASKED)
                .phoneNumber(phone)
                .role("CUSTOMER")
                .tenantId("default")
                .build();
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("First delivery: creates new UserContactDocument with email, phone=null, smsEnabled=false")
    void firstDelivery_noExistingDoc_createsNew() throws Exception {
        when(contactRepository.findById(USER_ID.toString())).thenReturn(Optional.empty());
        when(contactRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer.handle(record(eventWithPhone(null)));

        ArgumentCaptor<UserContactDocument> captor = ArgumentCaptor.forClass(UserContactDocument.class);
        verify(contactRepository).save(captor.capture());
        UserContactDocument saved = captor.getValue();

        assertThat(saved.getUserId()).isEqualTo(USER_ID.toString());
        assertThat(saved.getEmail()).isEqualTo(EMAIL);
        assertThat(saved.getMaskedEmail()).isEqualTo(MASKED);
        assertThat(saved.getPhoneNumber()).isNull();
        assertThat(saved.isSmsNotificationsEnabled()).isFalse();
    }

    @Test
    @DisplayName("First delivery with phone: phone stored, smsEnabled=false (not OTP-verified yet)")
    void firstDelivery_withPhone_storedButSmsDisabled() throws Exception {
        when(contactRepository.findById(USER_ID.toString())).thenReturn(Optional.empty());
        when(contactRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer.handle(record(eventWithPhone(PHONE)));

        ArgumentCaptor<UserContactDocument> captor = ArgumentCaptor.forClass(UserContactDocument.class);
        verify(contactRepository).save(captor.capture());
        UserContactDocument saved = captor.getValue();

        assertThat(saved.getPhoneNumber()).isEqualTo(PHONE);
        assertThat(saved.isSmsNotificationsEnabled()).isFalse();
    }

    @Test
    @DisplayName("Re-delivery: OTP-verified phone is NOT overwritten when user.registered is re-delivered")
    void redelivery_otpVerifiedPhonePreserved() throws Exception {
        // Simulate existing document where user has OTP-verified their phone
        UserContactDocument existing = UserContactDocument.builder()
                .userId(USER_ID.toString())
                .email(EMAIL)
                .maskedEmail(MASKED)
                .phoneNumber(PHONE)
                .smsNotificationsEnabled(true)   // OTP-verified: SMS enabled
                .updatedAt(Instant.now().minusSeconds(3600))
                .build();

        when(contactRepository.findById(USER_ID.toString())).thenReturn(Optional.of(existing));
        when(contactRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Re-deliver user.registered (phone=null for social login user at registration time)
        consumer.handle(record(eventWithPhone(null)));

        ArgumentCaptor<UserContactDocument> captor = ArgumentCaptor.forClass(UserContactDocument.class);
        verify(contactRepository).save(captor.capture());
        UserContactDocument saved = captor.getValue();

        // Phone and SMS preference MUST be preserved — not overwritten by re-delivery
        assertThat(saved.getPhoneNumber())
                .as("OTP-verified phone must not be overwritten on re-delivery")
                .isEqualTo(PHONE);
        assertThat(saved.isSmsNotificationsEnabled())
                .as("SMS enabled flag must not be reset on re-delivery")
                .isTrue();

        // Email is always updated (authoritative from IdP)
        assertThat(saved.getEmail()).isEqualTo(EMAIL);
        assertThat(saved.getMaskedEmail()).isEqualTo(MASKED);
    }

    @Test
    @DisplayName("Re-delivery: email updated even when phone is already on file")
    void redelivery_emailAlwaysUpdated() throws Exception {
        String oldEmail  = "old@example.com";
        String newEmail  = EMAIL;

        UserContactDocument existing = UserContactDocument.builder()
                .userId(USER_ID.toString())
                .email(oldEmail)
                .maskedEmail("o***@example.com")
                .phoneNumber(PHONE)
                .smsNotificationsEnabled(true)
                .updatedAt(Instant.now().minusSeconds(3600))
                .build();

        when(contactRepository.findById(USER_ID.toString())).thenReturn(Optional.of(existing));
        when(contactRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer.handle(record(eventWithPhone(null)));

        ArgumentCaptor<UserContactDocument> captor = ArgumentCaptor.forClass(UserContactDocument.class);
        verify(contactRepository).save(captor.capture());
        UserContactDocument saved = captor.getValue();

        // Email updated (IdP is authoritative)
        assertThat(saved.getEmail()).isEqualTo(newEmail);
        assertThat(saved.getMaskedEmail()).isEqualTo(MASKED);
        // Phone unchanged
        assertThat(saved.getPhoneNumber()).isEqualTo(PHONE);
        assertThat(saved.isSmsNotificationsEnabled()).isTrue();
    }

    @Test
    @DisplayName("WebSocket USER_REGISTERED notification dispatched on every delivery")
    void dispatchesWebSocketNotification() throws Exception {
        when(contactRepository.findById(any())).thenReturn(Optional.empty());
        when(contactRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer.handle(record(eventWithPhone(null)));

        verify(dispatcher).dispatch(
                eq(USER_ID.toString()),
                any(),
                eq("WEBSOCKET"),
                isNull(),
                any()
        );
    }
}
