package com.aegispay.notification.integration;

import com.aegispay.common.domain.enums.NotificationType;
import com.aegispay.notification.dispatcher.NotificationDispatcher;
import com.aegispay.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = {
        "transaction.completed", "transaction.failed", "transaction.rolled-back",
        "user.registered", "kyc.status.changed", "notification.send.requested"
})
class NotificationIT {

    @Container
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> mongo.getReplicaSetUrl("aegispay_notifications_test"));
        registry.add("spring.kafka.bootstrap-servers",
                () -> "${spring.embedded.kafka.brokers}");
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> "https://test-issuer.aegispay.io");
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", () -> "2525");
    }

    @Autowired NotificationDispatcher dispatcher;
    @Autowired NotificationRepository notificationRepository;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
    }

    @Test
    void dispatch_websocket_persists_notification_in_mongodb() {
        dispatcher.dispatch("user-abc", NotificationType.TRANSACTION_COMPLETED,
                "WEBSOCKET", null,
                Map.of("amount", "250.00", "currency", "USD", "externalReference", "EXT-999"));

        var notifications = notificationRepository.findAll();
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getStatus()).isEqualTo("SENT");
        assertThat(notifications.get(0).getType()).isEqualTo(NotificationType.TRANSACTION_COMPLETED);
        assertThat(notifications.get(0).getTitle()).isEqualTo("Payment Successful");
    }

    @Test
    void dispatch_user_registered_persists_welcome_notification() {
        dispatcher.dispatch("user-xyz", NotificationType.USER_REGISTERED,
                "WEBSOCKET", null, Map.of());

        var notifications = notificationRepository.findAll();
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getTitle()).isEqualTo("Welcome to AegisPay!");
    }
}
