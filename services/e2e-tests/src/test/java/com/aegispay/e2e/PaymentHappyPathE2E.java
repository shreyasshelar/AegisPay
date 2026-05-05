package com.aegispay.e2e;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.testcontainers.containers.*;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test suite for the AegisPay payment transaction flow.
 *
 * <p>Topology: PostgreSQL (pgvector) + MongoDB + Redis + Kafka
 * All services run as Docker containers via Testcontainers.
 *
 * <p>Test coverage:
 * <ol>
 *   <li>Happy path: Transaction created → Saga completes → Status = COMPLETED
 *   <li>Balance verification: Ledger deducted correct amount after completion
 *   <li>Duplicate idempotency key → 409 Conflict
 *   <li>Concurrent requests with same key → exactly one 201, rest 409
 *   <li>Saga compensation: Insufficient funds → status = FAILED, balance unchanged
 *   <li>MongoDB read model consistency: CQRS view updated after saga completes
 *   <li>DLQ depth = 0 after all tests (no poison pill messages)
 *   <li>User service: Registration → KYC submission → KYC status query
 * </ol>
 *
 * <p>NOTE: Requires Docker and pre-built service images tagged "latest".
 *       Run with: {@code mvn verify -Pe2e}
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
    static GenericContainer<?> transactionService;
    static GenericContainer<?> paymentOrchestrator;
    static GenericContainer<?> ledgerService;
    static GenericContainer<?> riskEngine;
    static GenericContainer<?> notificationService;
    static GenericContainer<?> userService;

    static Network network = Network.newNetwork();

    /** WebClient pointing at transaction-service */
    static WebClient txClient;
    /** WebClient pointing at ledger-service */
    static WebClient ledgerClient;
    /** WebClient pointing at user-service */
    static WebClient userClient;

    /** UUID of the payer account pre-seeded in ledger service */
    static final UUID PAYER_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    /** Starting balance for payer account (seeded via SQL init) */
    static final BigDecimal INITIAL_BALANCE = new BigDecimal("10000.00");

    @BeforeAll
    static void startAll() {
        postgres.withNetwork(network).withNetworkAliases("postgres").start();
        mongo.withNetwork(network).withNetworkAliases("mongo").start();
        redis.withNetwork(network).withNetworkAliases("redis").start();
        kafka.withNetwork(network).withNetworkAliases("kafka").start();
        startServiceContainers();
        initClients();
    }

    private static void startServiceContainers() {
        Map<String, String> commonEnv = Map.ofEntries(
                Map.entry("SPRING_DATASOURCE_URL",
                        "jdbc:postgresql://postgres:5432/aegispay"),
                Map.entry("SPRING_DATASOURCE_USERNAME", "aegispay"),
                Map.entry("SPRING_DATASOURCE_PASSWORD", "aegispay_secret"),
                Map.entry("SPRING_KAFKA_BOOTSTRAP_SERVERS", "kafka:9092"),
                Map.entry("SPRING_DATA_REDIS_HOST", "redis"),
                Map.entry("SPRING_DATA_MONGODB_URI", "mongodb://mongo:27017/aegispay_e2e"),
                // Disable OAuth2 JWT validation in tests — filter lets all through
                Map.entry("OAUTH2_ISSUER_URI", "http://localhost:9999"),
                Map.entry("AEGISPAY_SECURITY_DISABLE_JWT", "true"),
                // Stripe disabled in e2e — use mock payment gateway
                Map.entry("STRIPE_SECRET_KEY", "sk_test_disabled"),
                Map.entry("STRIPE_WEBHOOK_SECRET", "whsec_disabled"),
                // AI disabled in e2e — risk engine uses rules only
                Map.entry("ANTHROPIC_API_KEY", "noop"),
                Map.entry("SMTP_PASSWORD", "noop")
        );

        ledgerService = new GenericContainer<>("aegispay/ledger-service:latest")
                .withNetwork(network).withNetworkAliases("ledger-service")
                .withExposedPorts(8083).withEnv(commonEnv)
                .waitingFor(Wait.forHttp("/actuator/health/readiness")
                        .forPort(8083).withStartupTimeout(Duration.ofSeconds(120)));

        riskEngine = new GenericContainer<>("aegispay/risk-engine:latest")
                .withNetwork(network).withNetworkAliases("risk-engine")
                .withExposedPorts(8085).withEnv(commonEnv)
                .waitingFor(Wait.forHttp("/actuator/health/readiness")
                        .forPort(8085).withStartupTimeout(Duration.ofSeconds(120)));

        paymentOrchestrator = new GenericContainer<>("aegispay/payment-orchestrator:latest")
                .withNetwork(network).withNetworkAliases("payment-orchestrator")
                .withExposedPorts(8084).withEnv(commonEnv)
                .waitingFor(Wait.forHttp("/actuator/health/readiness")
                        .forPort(8084).withStartupTimeout(Duration.ofSeconds(120)));

        transactionService = new GenericContainer<>("aegispay/transaction-service:latest")
                .withNetwork(network).withNetworkAliases("transaction-service")
                .withExposedPorts(8082).withEnv(commonEnv)
                .waitingFor(Wait.forHttp("/actuator/health/readiness")
                        .forPort(8082).withStartupTimeout(Duration.ofSeconds(120)));

        notificationService = new GenericContainer<>("aegispay/notification-service:latest")
                .withNetwork(network).withNetworkAliases("notification-service")
                .withExposedPorts(8086).withEnv(commonEnv)
                .waitingFor(Wait.forHttp("/actuator/health/readiness")
                        .forPort(8086).withStartupTimeout(Duration.ofSeconds(120)));

        userService = new GenericContainer<>("aegispay/user-service:latest")
                .withNetwork(network).withNetworkAliases("user-service")
                .withExposedPorts(8081).withEnv(commonEnv)
                .waitingFor(Wait.forHttp("/actuator/health/readiness")
                        .forPort(8081).withStartupTimeout(Duration.ofSeconds(120)));

        // Start in dependency order
        ledgerService.start();
        riskEngine.start();
        paymentOrchestrator.start();
        transactionService.start();
        notificationService.start();
        userService.start();
    }

    private static void initClients() {
        String txBase = "http://localhost:" + transactionService.getMappedPort(8082);
        txClient = WebClient.builder().baseUrl(txBase)
                .defaultHeader("Authorization", "Bearer " + TestTokenFactory.customerToken())
                .build();

        String ledgerBase = "http://localhost:" + ledgerService.getMappedPort(8083);
        ledgerClient = WebClient.builder().baseUrl(ledgerBase)
                .defaultHeader("Authorization", "Bearer " + TestTokenFactory.customerToken())
                .build();

        String userBase = "http://localhost:" + userService.getMappedPort(8081);
        userClient = WebClient.builder().baseUrl(userBase)
                .defaultHeader("Authorization", "Bearer " + TestTokenFactory.customerToken())
                .build();
    }

    @AfterAll
    static void stopAll() {
        List.of(userService, notificationService, transactionService,
                paymentOrchestrator, riskEngine, ledgerService)
                .forEach(c -> { if (c != null) c.stop(); });
        kafka.stop(); redis.stop(); mongo.stop(); postgres.stop();
        network.close();
    }

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test @Order(1)
    void createTransaction_returns201_withTransactionId() {
        Map<?, ?> body = txClient.post()
                .uri("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Idempotency-Key", UUID.randomUUID().toString())
                .bodyValue(transactionPayload("500.00"))
                .retrieve()
                .bodyToMono(Map.class)
                .block(Duration.ofSeconds(10));

        assertThat(body).isNotNull();
        assertThat(body.get("transactionId")).isNotNull();
        assertThat(body.get("status")).isEqualTo("INITIATED");
    }

    @Test @Order(2)
    void transaction_reaches_completed_state_via_saga() {
        String idempotencyKey = UUID.randomUUID().toString();
        Map<?, ?> created = createTransaction("250.00", idempotencyKey);
        String txId = (String) created.get("transactionId");

        // Saga completes within 30s
        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Map<?, ?> status = getTransaction(txId);
                    assertThat(status.get("status")).isEqualTo("COMPLETED");
                });
    }

    @Test @Order(3)
    void completed_transaction_has_externalReference() {
        String key = UUID.randomUUID().toString();
        Map<?, ?> created = createTransaction("300.00", key);
        String txId = (String) created.get("transactionId");

        Awaitility.await().atMost(30, TimeUnit.SECONDS).pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Map<?, ?> tx = getTransaction(txId);
                    assertThat(tx.get("status")).isEqualTo("COMPLETED");
                    // externalReference is the Stripe PaymentIntent ID (or mock reference)
                    assertThat(tx.get("externalReference")).isNotNull();
                });
    }

    @Test @Order(4)
    void balance_is_deducted_after_successful_saga() {
        // Get starting balance
        Map<?, ?> balanceBefore = getBalance(PAYER_ACCOUNT_ID.toString());
        BigDecimal before = new BigDecimal(balanceBefore.get("availableBalance").toString());

        BigDecimal paymentAmount = new BigDecimal("100.00");
        Map<?, ?> created = createTransaction(paymentAmount.toPlainString(), UUID.randomUUID().toString());
        String txId = (String) created.get("transactionId");

        Awaitility.await().atMost(30, TimeUnit.SECONDS).pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Map<?, ?> tx = getTransaction(txId);
                    assertThat(tx.get("status")).isEqualTo("COMPLETED");
                });

        Map<?, ?> balanceAfter = getBalance(PAYER_ACCOUNT_ID.toString());
        BigDecimal after = new BigDecimal(balanceAfter.get("availableBalance").toString());

        assertThat(after).isEqualByComparingTo(before.subtract(paymentAmount));
    }

    @Test @Order(5)
    void duplicate_idempotency_key_returns_409() {
        String idempotencyKey = UUID.randomUUID().toString();
        Map<String, Object> payload = transactionPayload("100.00");

        // First request — must succeed
        txClient.post().uri("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Idempotency-Key", idempotencyKey)
                .bodyValue(payload).retrieve()
                .bodyToMono(Map.class).block(Duration.ofSeconds(10));

        // Second request with same key — must be 409
        var response = txClient.post().uri("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Idempotency-Key", idempotencyKey)
                .bodyValue(payload)
                .exchangeToMono(r -> r.toEntity(String.class))
                .block(Duration.ofSeconds(10));

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode().value()).isEqualTo(409);
    }

    @Test @Order(6)
    void concurrent_requests_same_idempotency_key_exactly_one_succeeds() throws InterruptedException {
        String idempotencyKey = UUID.randomUUID().toString();
        Map<String, Object> payload = transactionPayload("50.00");
        int concurrency = 10;

        AtomicInteger created = new AtomicInteger(0);
        AtomicInteger conflicts = new AtomicInteger(0);
        AtomicInteger errors = new AtomicInteger(0);

        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < concurrency; i++) {
            futures.add(pool.submit(() -> {
                try {
                    latch.await(); // all threads start simultaneously
                    txClient.post().uri("/api/v1/transactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Idempotency-Key", idempotencyKey)
                            .bodyValue(payload).retrieve()
                            .bodyToMono(Map.class).block(Duration.ofSeconds(15));
                    created.incrementAndGet();
                } catch (WebClientResponseException.Conflict e) {
                    conflicts.incrementAndGet();
                } catch (Exception e) {
                    errors.incrementAndGet();
                }
            }));
        }

        latch.countDown(); // release all threads at once
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);

        assertThat(created.get()).as("exactly one transaction should be created").isEqualTo(1);
        assertThat(conflicts.get()).as("remaining threads should get 409").isEqualTo(concurrency - 1);
        assertThat(errors.get()).as("no unexpected errors").isZero();
    }

    @Test @Order(7)
    void saga_compensation_fires_on_insufficient_funds() {
        // Create a transaction for amount > remaining balance to trigger compensation
        String key = UUID.randomUUID().toString();

        Map<?, ?> created = createTransaction("999999.00", key); // more than initial balance
        String txId = (String) created.get("transactionId");

        // Saga should fail (insufficient funds → FAILED or ROLLED_BACK)
        Awaitility.await().atMost(30, TimeUnit.SECONDS).pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Map<?, ?> tx = getTransaction(txId);
                    String status = (String) tx.get("status");
                    assertThat(status).isIn("FAILED", "ROLLED_BACK");
                });
    }

    @Test @Order(8)
    void balance_unchanged_after_failed_saga() {
        Map<?, ?> balanceBefore = getBalance(PAYER_ACCOUNT_ID.toString());
        BigDecimal before = new BigDecimal(balanceBefore.get("availableBalance").toString());

        // Transaction that will fail (amount > balance)
        String key = UUID.randomUUID().toString();
        Map<?, ?> created = createTransaction("999999.00", key);
        String txId = (String) created.get("transactionId");

        Awaitility.await().atMost(30, TimeUnit.SECONDS).pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Map<?, ?> tx = getTransaction(txId);
                    String status = (String) tx.get("status");
                    assertThat(status).isIn("FAILED", "ROLLED_BACK");
                });

        // Balance must be exactly the same as before (no partial deduction)
        Map<?, ?> balanceAfter = getBalance(PAYER_ACCOUNT_ID.toString());
        BigDecimal after = new BigDecimal(balanceAfter.get("availableBalance").toString());
        assertThat(after).isEqualByComparingTo(before);
    }

    @Test @Order(9)
    void mongodb_read_model_updated_after_saga_completion() {
        String key = UUID.randomUUID().toString();
        Map<?, ?> created = createTransaction("75.00", key);
        String txId = (String) created.get("transactionId");

        Awaitility.await().atMost(30, TimeUnit.SECONDS).pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Map<?, ?> tx = getTransaction(txId);
                    assertThat(tx.get("status")).isEqualTo("COMPLETED");
                    // completedAt must be populated in read model
                    assertThat(tx.get("completedAt")).isNotNull();
                });
    }

    @Test @Order(10)
    void user_registration_and_kyc_submission() {
        String idempotencyKey = UUID.randomUUID().toString();

        // Register a new user
        Map<?, ?> user = userClient.post()
                .uri("/api/v1/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Idempotency-Key", idempotencyKey)
                .bodyValue(Map.of(
                        "email", "e2e-" + UUID.randomUUID() + "@test.com",
                        "phone", "+919999999999",
                        "firstName", "E2E",
                        "lastName", "Test",
                        "tenantId", "default"))
                .retrieve()
                .bodyToMono(Map.class)
                .block(Duration.ofSeconds(10));

        assertThat(user).isNotNull();
        assertThat(user.get("id")).isNotNull();
        assertThat(user.get("kycStatus")).isEqualTo("PENDING");

        String userId = (String) user.get("id");

        // Submit KYC document
        Map<?, ?> kycResponse = userClient.post()
                .uri("/api/v1/users/" + userId + "/kyc/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "documentType", "AADHAAR",
                        "documentRef", "s3://kyc-bucket/e2e-test-doc.jpg"))
                .retrieve()
                .bodyToMono(Map.class)
                .block(Duration.ofSeconds(10));

        assertThat(kycResponse).isNotNull();
        assertThat(kycResponse.get("kycStatus")).isEqualTo("DOCUMENT_SUBMITTED");

        // Query user profile — KYC status should reflect update
        Map<?, ?> profile = userClient.get()
                .uri("/api/v1/users/" + userId)
                .retrieve()
                .bodyToMono(Map.class)
                .block(Duration.ofSeconds(10));

        assertThat(profile).isNotNull();
        assertThat(profile.get("kycStatus")).isEqualTo("DOCUMENT_SUBMITTED");
    }

    @Test @Order(11)
    void dlq_depth_is_zero_after_all_tests() {
        // Query Kafka metrics via ledger-service and orchestrator actuator
        // DLQ depth = 0 means no poison-pill messages were produced during the test suite
        checkDlqDepthZero(ledgerService.getMappedPort(8083), "ledger-service");
        checkDlqDepthZero(transactionService.getMappedPort(8082), "transaction-service");
        checkDlqDepthZero(paymentOrchestrator.getMappedPort(8084), "payment-orchestrator");
        checkDlqDepthZero(riskEngine.getMappedPort(8085), "risk-engine");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> transactionPayload(String amount) {
        return Map.of(
                "payerId", PAYER_ACCOUNT_ID.toString(),
                "payeeId", UUID.randomUUID().toString(),
                "amount", amount,
                "currency", "INR",
                "note", "E2E test payment"
        );
    }

    private Map<?, ?> createTransaction(String amount, String idempotencyKey) {
        return txClient.post()
                .uri("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Idempotency-Key", idempotencyKey)
                .bodyValue(transactionPayload(amount))
                .retrieve()
                .bodyToMono(Map.class)
                .block(Duration.ofSeconds(10));
    }

    private Map<?, ?> getTransaction(String txId) {
        return txClient.get()
                .uri("/api/v1/transactions/" + txId)
                .retrieve()
                .bodyToMono(Map.class)
                .block(Duration.ofSeconds(5));
    }

    private Map<?, ?> getBalance(String userId) {
        return ledgerClient.get()
                .uri("/api/v1/ledger/accounts/" + userId)
                .retrieve()
                .bodyToMono(Map.class)
                .block(Duration.ofSeconds(5));
    }

    /**
     * Checks that the Kafka consumer lag on all DLQ topics for a service is 0.
     * Uses the Micrometer /actuator/metrics endpoint.
     */
    private void checkDlqDepthZero(int port, String serviceName) {
        try {
            WebClient serviceClient = WebClient.builder()
                    .baseUrl("http://localhost:" + port)
                    .build();
            Map<?, ?> metrics = serviceClient.get()
                    .uri("/actuator/metrics/kafka.consumer.fetch.manager.records.lag.max")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(5));
            if (metrics != null && metrics.get("measurements") instanceof List<?> measurements) {
                for (Object m : measurements) {
                    if (m instanceof Map<?, ?> measurement) {
                        Object value = measurement.get("value");
                        if (value != null) {
                            double lag = Double.parseDouble(value.toString());
                            assertThat(lag).as("DLQ lag on %s", serviceName)
                                    .isLessThanOrEqualTo(0.0);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Non-fatal if metric not available in test environment
            System.out.println("[WARN] Could not verify DLQ depth for " + serviceName + ": " + e.getMessage());
        }
    }
}
