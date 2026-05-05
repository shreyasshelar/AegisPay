package com.aegispay.orchestrator.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalPaymentGatewayClient {

    private final WebClient paymentGatewayWebClient;

    public record PaymentRequest(UUID transactionId, UUID payerId, UUID payeeId,
                                  BigDecimal amount, String currency) {}

    public record PaymentResult(boolean success, String externalReference,
                                 String failureCode, String failureMessage) {}

    @CircuitBreaker(name = "payment-gateway", fallbackMethod = "fallbackProcess")
    public PaymentResult processPayment(PaymentRequest request) {
        log.info("Calling external payment gateway for txn={}", request.transactionId());
        return paymentGatewayWebClient.post()
                .uri("/payments")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(PaymentResult.class)
                .block();
    }

    PaymentResult fallbackProcess(PaymentRequest request, Throwable t) {
        log.error("Payment gateway circuit breaker open for txn={}: {}", request.transactionId(), t.getMessage());
        return new PaymentResult(false, null, "GATEWAY_UNAVAILABLE",
                "Payment gateway circuit breaker open: " + t.getMessage());
    }
}
