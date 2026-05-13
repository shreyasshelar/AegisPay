package com.aegispay.orchestrator.client;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Stripe-backed payment gateway client.
 *
 * <p>Currency handling:
 * <ul>
 *   <li>Most currencies (USD, EUR, GBP, INR…): multiply by 100 to get smallest unit (cents/paise).
 *   <li>Zero-decimal currencies (JPY, KRW, VND, BIF, CLP, GNF, MGA, PYG, RWF, UGX, XAF, XOF, XPF):
 *       use the amount as-is (no multiplication).
 * </ul>
 *
 * <p>Cross-currency settlement is handled natively by Stripe — pass the charge currency and
 * Stripe settles to the connected account's payout currency automatically.
 *
 * <p>Payment method strategy:
 * <ul>
 *   <li>Dev/test: uses {@code stripe.default-payment-method} (default: {@code pm_card_visa}).
 *   <li>Production: pass a stored {@code paymentMethodId} per-user via {@link PaymentRequest}.
 * </ul>
 * Stripe requires a payment method to be attached before a PaymentIntent can be confirmed
 * server-side ({@code confirm: true}).  Creating the intent without one results in
 * {@code payment_intent_unexpected_state}.
 */
@Slf4j
@Component
public class StripePaymentGatewayClient {

    /** Zero-decimal currencies per Stripe documentation (as of May 2025). */
    private static final Set<String> ZERO_DECIMAL_CURRENCIES = Set.of(
            "BIF", "CLP", "GNF", "JPY", "KMF", "KRW", "MGA", "PYG",
            "RWF", "UGX", "VND", "VUV", "XAF", "XOF", "XPF"
    );

    @Value("${stripe.secret-key}")
    private String secretKey;

    /** Default PM used for server-side confirmation (pm_card_visa in test mode). */
    @Value("${stripe.default-payment-method:pm_card_visa}")
    private String defaultPaymentMethodId;

    @PostConstruct
    void init() {
        Stripe.apiKey = secretKey;
        log.info("Stripe client initialised (key prefix: {})", secretKey.substring(0, 7));
    }

    // ── Records (keep API-compatible with the old stub) ────────────────────────

    public record PaymentRequest(
            java.util.UUID transactionId,
            java.util.UUID payerId,
            java.util.UUID payeeId,
            BigDecimal amount,
            String currency,
            /** Optional stored PaymentMethod ID (pm_…). Null → falls back to defaultPaymentMethodId. */
            String paymentMethodId
    ) {
        /** Convenience constructor without explicit PM — uses service default. */
        public PaymentRequest(java.util.UUID transactionId, java.util.UUID payerId,
                              java.util.UUID payeeId, BigDecimal amount, String currency) {
            this(transactionId, payerId, payeeId, amount, currency, null);
        }
    }

    public record PaymentResult(
            boolean success,
            String externalReference,   // Stripe PaymentIntent ID (pi_…)
            String failureCode,
            String failureMessage
    ) {}

    // ── Core method ────────────────────────────────────────────────────────────

    @CircuitBreaker(name = "payment-gateway", fallbackMethod = "fallbackProcess")
    public PaymentResult processPayment(PaymentRequest request) {
        log.info("Initiating Stripe PaymentIntent for txn={} amount={} {}",
                request.transactionId(), request.amount(), request.currency());

        long stripeAmount = toStripeAmount(request.amount(), request.currency());
        String currency   = request.currency().toLowerCase();
        // Resolve payment method: explicit per-request → service default
        String pmId = (request.paymentMethodId() != null && !request.paymentMethodId().isBlank())
                ? request.paymentMethodId()
                : defaultPaymentMethodId;

        try {
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(stripeAmount)
                    .setCurrency(currency)
                    // Attach payment method so server-side confirm works
                    .setPaymentMethod(pmId)
                    .setConfirm(true)
                    // off_session = no 3DS redirect; fine for server-driven P2P flows
                    .setOffSession(true)
                    .putMetadata("transaction_id", request.transactionId().toString())
                    .putMetadata("payer_id",       request.payerId().toString())
                    .putMetadata("payee_id",       request.payeeId().toString())
                    .setDescription("AegisPay txn " + request.transactionId())
                    .build();

            PaymentIntent intent = PaymentIntent.create(params);
            log.info("Stripe PaymentIntent created: id={} status={} txn={}",
                    intent.getId(), intent.getStatus(), request.transactionId());

            boolean succeeded = "succeeded".equals(intent.getStatus())
                    || "requires_capture".equals(intent.getStatus());

            if (succeeded) {
                return new PaymentResult(true, intent.getId(), null, null);
            } else {
                String code = intent.getLastPaymentError() != null
                        ? intent.getLastPaymentError().getCode() : "PAYMENT_FAILED";
                String msg = intent.getLastPaymentError() != null
                        ? intent.getLastPaymentError().getMessage() : "Payment did not succeed";
                log.warn("Stripe payment not succeeded: status={} code={} txn={}",
                        intent.getStatus(), code, request.transactionId());
                return new PaymentResult(false, intent.getId(), code, msg);
            }

        } catch (StripeException e) {
            log.error("Stripe API error for txn={}: code={} msg={}",
                    request.transactionId(), e.getCode(), e.getMessage());
            return new PaymentResult(false, null, e.getCode(), e.getMessage());
        }
    }

    PaymentResult fallbackProcess(PaymentRequest request, Throwable t) {
        log.error("Payment gateway circuit breaker open for txn={}: {}",
                request.transactionId(), t.getMessage());
        return new PaymentResult(false, null, "GATEWAY_UNAVAILABLE",
                "Payment gateway circuit breaker open: " + t.getMessage());
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Converts a BigDecimal amount to Stripe's integer smallest-unit format.
     *
     * @param amount   human-readable amount (e.g. 10.50 USD or 1000 JPY)
     * @param currency ISO-4217 currency code (case-insensitive)
     * @return         amount in smallest currency unit
     */
    public static long toStripeAmount(BigDecimal amount, String currency) {
        if (ZERO_DECIMAL_CURRENCIES.contains(currency.toUpperCase())) {
            return amount.longValue();
        }
        return amount.multiply(BigDecimal.valueOf(100)).longValue();
    }
}
