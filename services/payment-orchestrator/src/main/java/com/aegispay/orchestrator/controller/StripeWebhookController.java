package com.aegispay.orchestrator.controller;

import com.aegispay.orchestrator.saga.PaymentSagaOrchestrator;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Receives Stripe webhook events and feeds them back into the Saga.
 *
 * <p>Stripe retries unacknowledged webhooks for up to 72 hours with exponential back-off.
 * We return HTTP 200 immediately after signature validation and process asynchronously
 * to avoid timeout issues on large saga state transitions.
 *
 * <p>Register this endpoint in the Stripe Dashboard:
 *   {@code https://api.aegispay.yourdomain.com/internal/webhooks/stripe}
 *
 * <p>Required events to subscribe to:
 * <ul>
 *   <li>{@code payment_intent.succeeded}
 *   <li>{@code payment_intent.payment_failed}
 *   <li>{@code payment_intent.canceled}
 *   <li>{@code radar.early_fraud_warning.created}
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/internal/webhooks/stripe")
@RequiredArgsConstructor
public class StripeWebhookController {

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    private final PaymentSagaOrchestrator sagaOrchestrator;

    /**
     * Stripe sends raw JSON body — do NOT let Spring parse it with Jackson.
     * We need the raw bytes to verify the {@code Stripe-Signature} header.
     */
    @PostMapping(consumes = "application/json")
    public ResponseEntity<Void> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.warn("Stripe webhook signature verification failed: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }

        log.info("Stripe webhook received: type={} id={}", event.getType(), event.getId());

        switch (event.getType()) {
            case "payment_intent.succeeded"         -> handlePaymentSucceeded(event);
            case "payment_intent.payment_failed"    -> handlePaymentFailed(event);
            case "payment_intent.canceled"          -> handlePaymentCanceled(event);
            case "radar.early_fraud_warning.created" -> handleEarlyFraudWarning(event);
            default -> log.debug("Ignoring unhandled Stripe event type: {}", event.getType());
        }

        return ResponseEntity.ok().build();
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    private void handlePaymentSucceeded(Event event) {
        extractPaymentIntent(event).ifPresent(intent -> {
            String transactionId = intent.getMetadata().get("transaction_id");
            if (transactionId == null) {
                log.warn("Stripe payment_intent.succeeded missing transaction_id metadata: pi={}",
                        intent.getId());
                return;
            }
            log.info("Stripe payment succeeded: pi={} txn={}", intent.getId(), transactionId);
            sagaOrchestrator.onStripePaymentSucceeded(
                    UUID.fromString(transactionId), intent.getId());
        });
    }

    private void handlePaymentFailed(Event event) {
        extractPaymentIntent(event).ifPresent(intent -> {
            String transactionId = intent.getMetadata().get("transaction_id");
            if (transactionId == null) {
                log.warn("Stripe payment_intent.payment_failed missing transaction_id metadata: pi={}",
                        intent.getId());
                return;
            }
            String failureCode = intent.getLastPaymentError() != null
                    ? intent.getLastPaymentError().getCode() : "PAYMENT_FAILED";
            String failureMsg = intent.getLastPaymentError() != null
                    ? intent.getLastPaymentError().getMessage() : "Payment failed";
            log.warn("Stripe payment failed: pi={} txn={} code={}",
                    intent.getId(), transactionId, failureCode);
            sagaOrchestrator.onStripePaymentFailed(
                    UUID.fromString(transactionId), intent.getId(), failureCode, failureMsg);
        });
    }

    private void handlePaymentCanceled(Event event) {
        extractPaymentIntent(event).ifPresent(intent -> {
            String transactionId = intent.getMetadata().get("transaction_id");
            if (transactionId == null) return;
            log.warn("Stripe payment canceled: pi={} txn={}", intent.getId(), transactionId);
            sagaOrchestrator.onStripePaymentFailed(
                    UUID.fromString(transactionId), intent.getId(),
                    "PAYMENT_CANCELED", "Payment was canceled in Stripe");
        });
    }

    /**
     * Handles {@code radar.early_fraud_warning.created} — Stripe Radar detected potential fraud
     * on a charge linked to one of our PaymentIntents.
     *
     * <p>Strategy:
     * <ol>
     *   <li>Extract the {@code charge} and linked {@code payment_intent} from the EFW object.
     *   <li>Read the {@code transaction_id} metadata we stamped on the PaymentIntent at creation.
     *   <li>Publish a risk metadata update so the Risk Engine can escalate to MANUAL_REVIEW.
     * </ol>
     *
     * <p>Stripe EFW events do NOT automatically refund the payment — that is an operator decision.
     * The saga remains in its current state; the risk-engine flag triggers human review.
     *
     * <p>Register in Stripe Dashboard:
     * <ul>
     *   <li>Event: {@code radar.early_fraud_warning.created}
     * </ul>
     */
    private void handleEarlyFraudWarning(Event event) {
        try {
            // The EFW object is a radar.EarlyFraudWarning; deserialize via the raw data map
            EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
            if (!deserializer.getObject().isPresent()) {
                log.error("Failed to deserialize EFW event object: id={}", event.getId());
                return;
            }

            // Retrieve the charge object to get the PaymentIntent ID from the EFW
            com.stripe.model.radar.EarlyFraudWarning efw =
                    (com.stripe.model.radar.EarlyFraudWarning) deserializer.getObject().get();

            String paymentIntentId = efw.getPaymentIntent();
            String fraudType       = efw.getFraudType();

            log.warn("Stripe Radar EFW received: efwId={} piId={} fraudType={} actionable={}",
                    efw.getId(), paymentIntentId, fraudType, efw.getActionable());

            if (paymentIntentId == null || paymentIntentId.isBlank()) {
                log.warn("EFW event has no associated PaymentIntent — skipping: efwId={}", efw.getId());
                return;
            }

            // Look up the PaymentIntent to retrieve our transaction_id metadata
            PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId);
            String transactionId = intent.getMetadata().get("transaction_id");
            if (transactionId == null) {
                log.warn("EFW PaymentIntent missing transaction_id metadata: piId={}", paymentIntentId);
                return;
            }

            // Delegate risk escalation to the saga orchestrator
            sagaOrchestrator.onStripeRadarEarlyFraudWarning(
                    java.util.UUID.fromString(transactionId),
                    paymentIntentId,
                    fraudType,
                    efw.getActionable());

        } catch (com.stripe.exception.StripeException e) {
            log.error("Stripe API error retrieving PaymentIntent for EFW: {}", e.getMessage(), e);
        } catch (ClassCastException e) {
            log.error("EFW event object is not a radar.EarlyFraudWarning: {}", e.getMessage(), e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private java.util.Optional<PaymentIntent> extractPaymentIntent(Event event) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        if (!deserializer.getObject().isPresent()) {
            log.error("Failed to deserialize Stripe event object: id={}", event.getId());
            return java.util.Optional.empty();
        }
        if (deserializer.getObject().get() instanceof PaymentIntent intent) {
            return java.util.Optional.of(intent);
        }
        return java.util.Optional.empty();
    }
}
