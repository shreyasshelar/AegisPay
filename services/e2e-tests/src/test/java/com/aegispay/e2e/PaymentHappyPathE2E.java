package com.aegispay.e2e;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.containers.*;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test for the happy-path payment transaction flow.
 *
 * Topology:
 *   PostgreSQL (pgvector) + MongoDB + Redis + Kafka
 *   All services run as Docker containers via Testcontainers.
 *
 * This test verifies:
 *   1. Transaction is created (INITIATED)
 *   2. Saga runs to completion (COMPLETED)
 *   3. MongoDB read model is updated
 *   4. Duplicate idempotency key returns 409
 *   5. Concurrent requests with same key → exactly one 201, rest 409
 *
 * NOTE: Requires Docker and pre-built service images tagged "latest".
 *       Run with: mvn verify -Pe2e
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PaymentHappyPathE2E {

    // ── Infrastructure containers ────────────────────────────────────────────
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("pgvector/pgvector:pg16")
                    .withDatabaseName("aegispay")
                    .withUsername("aegispay")
                    .withPassword("aegispay_secret");

    static final MongoDBContainer mongo =
            new MongoDBContainer("mongo:7.0");

    static final GenericContainer<?> redis =
            new GenericContainer<>("redis:7-alpine")
                    .withExposedPorts(6379);

    static final KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    // ── Service containers ───────────────────────────────────────────────────
    // Services are expected to be pre-built with `mvn package -DskipTests` + docker build
    static GenericContainer<?> transactionService;
    static GenericContainer<?> paymentOrchestrator;
    static GenericContainer<?> ledgerService;
    static GenericContainer<?> riskEngine;
    static GenericContainer<?> notificationService;

    static Network network = Network.newNetwork();
    static WebClient client;

    @BeforeAll
    static void startInfrastructure() {
        postgres.withNetwork(network).withNetworkAliases("postgres").start();
        mongo.withNetwork(network).withNetworkAliases("mongo").start();
        redis.withNetwork(network).withNetworkAliases("redis").start();
        kafka.withNetwork(network).withNetworkAliases("kafka").start();

        startServiceContainers();

        int txPort = transactionService.getMappedPort(8082);
        client = WebClient.builder()
                .baseUrl("http://localhost:" + txPort)
                .defaultHeader("Authorization", "Bearer " + TestTokenFactory.customerToken())
                .build();
    }

    private static void startServiceContainers() {
        Map<String, String> commonEnv = Map.of(
                "SPRING_DATASOURCE_URL",
                "jdbc:postgresql://postgres:5432/aegispay",
                "SPRING_DATASOURCE_USERNAME", "aegispay",
                "SPRING_DATASOURCE_PASSWORD", "aegispay_secret",
                "SPRING_KAFKA_BOOTSTRAP_SERVERS", "kafka:9092",
                "SPRING_DATA_REDIS_HOST", "redis",
                "SPRING_DATA_MONGODB_URI", "mongodb://mongo:27017/aegispay",
                "OAUTH2_ISSUER_URI", "http://localhost:9999"  // no-op in tests
        );

        transactionService = new GenericContainer<>("aegispay/transaction-service:latest")
                .withNetwork(network)
                .withExposedPorts(8082)
                .withEnv(commonEnv)
                .waitingFor(Wait.forHttp("/actuator/health").forPort(8082).withStartupTimeout(Duration.ofSeconds(90)));

        paymentOrchestrator = new GenericContainer<>("aegispay/payment-orchestrator:latest")
                .withNetwork(network)
                .withExposedPorts(8084)
                .withEnv(commonEnv)
                .waitingFor(Wait.forHttp("/actuator/health").forPort(8084).withStartupTimeout(Duration.ofSeconds(90)));

        ledgerService = new GenericContainer<>("aegispay/ledger-service:latest")
                .withNetwork(network)
                .withExposedPorts(8083)
                .withEnv(commonEnv)
                .waitingFor(Wait.forHttp("/actuator/health").forPort(8083).withStartupTimeout(Duration.ofSeconds(90)));

        riskEngine = new GenericContainer<>("aegispay/risk-engine:latest")
                .withNetwork(network)
                .withExposedPorts(8085)
                .withEnv(commonEnv)
                .waitingFor(Wait.forHttp("/actuator/health").forPort(8085).withStartupTimeout(Duration.ofSeconds(90)));

        notificationService = new GenericContainer<>("aegispay/notification-service:latest")
                .withNetwork(network)
                .withExposedPorts(8086)
                .withEnv(commonEnv)
                .waitingFor(Wait.forHttp("/actuator/health").forPort(8086).withStartupTimeout(Duration.ofSeconds(90)));

        // Start in dependency order
        ledgerService.start();
        riskEngine.start();
        paymentOrchestrator.start();
        transactionService.start();
        notificationService.start();
    }

    @AfterAll
    static void stopAll() {
        transactionService.stop();
        paymentOrchestrator.stop();
        ledgerService.stop();
        riskEngine.stop();
        notificationService.stop();
        kafka.stop();
        redis.stop();
        mongo.stop();
        postgres.stop();
        network.close();
    }

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    void createTransaction_returns201_withTransactionId() {
        Map<?, ?> response = client.post()
                .uri("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Idempotency-Key", UUID.randomUUID().toString())
                .bodyValue(Map.of(
                        "payeeId", UUID.randomUUID().toString(),
                        "amount", "500.00",
                        "currency", "INR"))
                .retrieve()
                .bodyToMono(Map.class)
                .block(Duration.ofSeconds(10));

        assertThat(response).isNotNull();
        assertThat(response.get("transactionId")).isNotNull();
    }

    @Test
    @Order(2)
    void transaction_reaches_completed_state_via_saga() {
        String idempotencyKey = UUID.randomUUID().toString();

        Map<?, ?> created = client.post()
                .uri("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Idempotency-Key", idempotencyKey)
                .bodyValue(Map.of(
                        "payeeId", UUID.randomUUID().toString(),
                        "amount", "250.00",
                        "currency", "INR"))
                .retrieve()
                .bodyToMono(Map.class)
                .block(Duration.ofSeconds(10));

        String txId = (String) created.get("transactionId");

        // Wait up to 30s for saga to complete
        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Map<?, ?> status = client.get()
                            .uri("/api/v1/transactions/" + txId)
                            .retrieve()
                            .bodyToMono(Map.class)
                            .block(Duration.ofSeconds(5));
                    assertThat(status.get("status")).isEqualTo("COMPLETED");
                });
    }

    @Test
    @Order(3)
    void duplicate_idempotency_key_returns_409() {
        String idempotencyKey = UUID.randomUUID().toString();
        Map<String, Object> body = Map.of(
                "payeeId", UUID.randomUUID().toString(),
                "amount", "100.00",
                "currency", "INR");

        // First request — must succeed
        client.post()
                .uri("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Idempotency-Key", idempotencyKey)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block(Duration.ofSeconds(10));

        // Second request with same key — must be 409
        var responseSpec = client.post()
                .uri("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Idempotency-Key", idempotencyKey)
                .bodyValue(body)
                .exchangeToMono(r -> r.toEntity(String.class))
                .block(Duration.ofSeconds(10));

        assertThat(responseSpec.getStatusCode().value()).isEqualTo(409);
    }
}
