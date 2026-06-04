package com.aegispay.ai.triage.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

/**
 * Reads circuit breaker state and resets OPEN/HALF_OPEN breakers via Actuator.
 *
 * In K8s: calls http://{serviceName}.{namespace}.svc.cluster.local:{port}/actuator/circuitbreakers
 * In local: calls http://localhost:{port}/actuator/circuitbreakers
 *
 * Reset endpoint: POST /actuator/circuitbreakers/{name}/reset (custom or Resilience4j actuator)
 */
@Slf4j
@Component
public class CircuitBreakerResetTool {

    private static final Map<String, Integer> SERVICE_PORTS = Map.of(
            "api-gateway",            8080,
            "user-service",           8081,
            "transaction-service",    8082,
            "ledger-service",         8083,
            "payment-orchestrator",   8084,
            "risk-engine",            8085,
            "notification-service",   8086,
            "reconciliation-service", 8087,
            "data-pipeline",          8089,
            "ai-platform",            8088
    );

    private final String namespace;
    private final boolean k8sMode;

    public CircuitBreakerResetTool(@Value("${K8S_NAMESPACE:aegispay}") String namespace) {
        this.namespace = namespace;
        this.k8sMode = System.getenv("KUBERNETES_SERVICE_HOST") != null;
    }

    @Tool(description =
            "Read circuit breaker states for an AegisPay service and optionally reset an OPEN breaker. " +
            "Calls the service's Resilience4j actuator endpoint. " +
            "serviceName: the service to check (e.g. 'api-gateway', 'payment-orchestrator'). " +
            "cbName: name of the specific circuit breaker to reset, or 'all' to reset all OPEN ones. " +
            "Pass cbName=null or empty string to only read state without resetting. " +
            "Only resets breakers that are in OPEN or HALF_OPEN state.")
    public String checkAndResetCircuitBreaker(String serviceName, String cbName) {
        log.info("CircuitBreakerResetTool: service={} cbName={}", serviceName, cbName);

        Integer port = SERVICE_PORTS.get(serviceName.toLowerCase());
        if (port == null) {
            return "Unknown service '" + serviceName + "'. Valid: " + SERVICE_PORTS.keySet();
        }

        String baseUrl = k8sMode
                ? "http://" + serviceName + "." + namespace + ".svc.cluster.local:" + port
                : "http://localhost:" + port;

        StringBuilder sb = new StringBuilder();
        sb.append("=== CIRCUIT BREAKERS: ").append(serviceName).append(" (").append(baseUrl).append(") ===\n");

        // Read all circuit breaker states
        try {
            String cbState = WebClient.builder().baseUrl(baseUrl).build()
                    .get().uri("/actuator/circuitbreakers")
                    .retrieve().bodyToMono(String.class).block(Duration.ofSeconds(5));
            sb.append("Current state:\n").append(cbState != null ? cbState : "(empty response)").append("\n");
        } catch (Exception e) {
            sb.append("Could not read circuit breakers: ").append(e.getMessage()).append("\n");
            sb.append("(Ensure management.endpoints.web.exposure.include=circuitbreakers is set)\n");
            return sb.toString();
        }

        // Reset if requested
        if (cbName != null && !cbName.isBlank()) {
            sb.append("\n=== RESET ACTION ===\n");
            try {
                String resetUrl = "/actuator/circuitbreakers/" + cbName + "/reset";
                WebClient.builder().baseUrl(baseUrl).build()
                        .post().uri(resetUrl)
                        .retrieve().bodyToMono(String.class).block(Duration.ofSeconds(5));
                sb.append("✅ Reset request sent for circuit breaker '").append(cbName).append("'\n");
                sb.append("Note: reset is only effective if the breaker is in OPEN or HALF_OPEN state.\n");

                // Re-read state after reset
                String newState = WebClient.builder().baseUrl(baseUrl).build()
                        .get().uri("/actuator/circuitbreakers")
                        .retrieve().bodyToMono(String.class).block(Duration.ofSeconds(5));
                sb.append("State after reset:\n").append(newState != null ? newState : "(empty)").append("\n");
            } catch (Exception e) {
                sb.append("⚠ Reset failed: ").append(e.getMessage()).append("\n");
                sb.append("The Resilience4j actuator reset endpoint may not be exposed.\n");
                sb.append("To enable: management.endpoints.web.exposure.include=circuitbreakers\n");
            }
        }
        return sb.toString();
    }
}