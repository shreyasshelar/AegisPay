package com.aegispay.ledger.service;

import com.aegispay.common.domain.enums.LedgerEntryType;
import com.aegispay.ledger.domain.dto.TopUpConfirmRequest;
import com.aegispay.ledger.domain.dto.TopUpConfirmResponse;
import com.aegispay.ledger.domain.dto.TopUpIntentRequest;
import com.aegispay.ledger.domain.dto.TopUpIntentResponse;
import com.aegispay.ledger.domain.entity.Account;
import com.aegispay.ledger.domain.entity.LedgerEntry;
import com.aegispay.ledger.exception.AccountNotFoundException;
import com.aegispay.ledger.repository.AccountRepository;
import com.aegispay.ledger.repository.LedgerEntryRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentConfirmParams;
import com.stripe.param.PaymentIntentCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TopUpService {

    private final AccountRepository     accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final ExchangeRateService   exchangeRateService;

    @Value("${stripe.secret-key}")
    private String stripeSecretKey;

    /**
     * Stripe SDK request options applied to every outbound call.
     *
     * <p>The Stripe Java SDK default read timeout is 80 seconds, which is longer
     * than the API Gateway's effective HTTP-client timeout.  If Stripe is slow the
     * gateway times out waiting for ledger-service to respond, generates a
     * network-level failure, and the circuit breaker opens → 503 Service Unavailable.
     *
     * <p>By capping the read timeout at 15 s the ledger service responds to the
     * gateway quickly (with an HTTP 422) instead of hanging.  The circuit breaker
     * never sees a timeout, stays closed, and subsequent requests succeed normally.
     */
    private static final RequestOptions STRIPE_REQUEST_OPTIONS = RequestOptions.builder()
            .setConnectTimeout(5_000)   // 5 s to open TCP connection to api.stripe.com
            .setReadTimeout(15_000)     // 15 s to receive the response body
            .build();

    @PostConstruct
    void initStripe() {
        Stripe.apiKey = stripeSecretKey;
    }

    // ── Create intent ─────────────────────────────────────────────────────────

    /**
     * Creates a Stripe PaymentIntent for the requested top-up amount.
     * The client-side SDK uses {@code clientSecret} to confirm payment.
     * No balance is credited yet — that happens in {@link #confirmTopUp}.
     */
    public TopUpIntentResponse createIntent(UUID userId, TopUpIntentRequest request) {
        // Stripe amounts are in the smallest currency unit (paise for INR, cents for USD).
        long amountInSmallestUnit = request.amount()
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();

        try {
            PaymentIntent intent = PaymentIntent.create(
                    PaymentIntentCreateParams.builder()
                            .setAmount(amountInSmallestUnit)
                            .setCurrency(request.currency().toLowerCase())
                            .addPaymentMethodType("card")
                            .putMetadata("aegispay_user_id", userId.toString())
                            .putMetadata("top_up", "true")
                            .build(),
                    STRIPE_REQUEST_OPTIONS
            );

            log.info("Created PaymentIntent pi={} amount={} {} user={}",
                    intent.getId(), request.amount(), request.currency(), userId);

            return new TopUpIntentResponse(
                    intent.getId(),
                    intent.getClientSecret(),
                    request.amount(),
                    request.currency().toUpperCase()
            );
        } catch (StripeException e) {
            log.error("Stripe error creating PaymentIntent for user={}: {}", userId, e.getMessage(), e);
            throw new IllegalStateException("Payment gateway error: " + e.getMessage(), e);
        }
    }

    // ── Confirm & credit ──────────────────────────────────────────────────────

    /**
     * Called after the client has confirmed the payment.
     * Retrieves the PaymentIntent from Stripe to verify {@code status == "succeeded"},
     * then credits the user's ledger account. Idempotent — safe to call twice.
     */
    @Transactional
    public TopUpConfirmResponse confirmTopUp(UUID userId, TopUpConfirmRequest request) {
        PaymentIntent intent = retrieveIntent(request.paymentIntentId());

        // Verify intent belongs to this user (metadata check)
        String metaUserId = intent.getMetadata().get("aegispay_user_id");
        if (metaUserId == null || !metaUserId.equals(userId.toString())) {
            throw new IllegalArgumentException("PaymentIntent does not belong to the authenticated user");
        }

        // In Stripe test mode auto-confirm with pm_card_visa so the frontend
        // doesn't need a full Stripe.js card-collection flow during development.
        if (!"succeeded".equals(intent.getStatus()) && stripeSecretKey.startsWith("sk_test_")) {
            if ("requires_payment_method".equals(intent.getStatus())
                    || "requires_confirmation".equals(intent.getStatus())) {
                try {
                    intent = intent.confirm(
                            PaymentIntentConfirmParams.builder()
                                    .setPaymentMethod("pm_card_visa")
                                    .build(),
                            STRIPE_REQUEST_OPTIONS);
                    log.info("Test-mode auto-confirm: pi={} status={}", intent.getId(), intent.getStatus());
                } catch (StripeException e) {
                    throw new IllegalStateException("Auto-confirm failed: " + e.getMessage(), e);
                }
            }
        }

        if (!"succeeded".equals(intent.getStatus())) {
            throw new IllegalStateException(
                    "PaymentIntent status is '" + intent.getStatus() + "', expected 'succeeded'");
        }

        // Idempotency: return existing entry if already credited
        var existing = ledgerEntryRepository.findByIdempotencyKey(request.paymentIntentId());
        if (existing.isPresent()) {
            log.info("Top-up already credited for pi={} — returning cached result", request.paymentIntentId());
            LedgerEntry e = existing.get();
            Account acct = accountRepository.findById(e.getAccountId()).orElseThrow();
            return new TopUpConfirmResponse("SUCCEEDED", e.getId(), e.getAmount(), acct.getCurrency());
        }

        // Amount in base units → decimal (payment currency)
        String paymentCurrency = intent.getCurrency().toUpperCase();
        BigDecimal paymentAmount = BigDecimal.valueOf(intent.getAmount())
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);

        // Find account — prefer matching currency, fall back to user's primary account
        Account account = accountRepository
                .findByUserIdAndCurrency(userId, paymentCurrency)
                .or(() -> accountRepository.findByUserId(userId).stream().findFirst())
                .orElseThrow(() -> new AccountNotFoundException(userId));

        String accountCurrency = account.getCurrency();

        // Convert to account's native currency via Frankfurter live rates (Redis-cached 1 h)
        BigDecimal creditAmount = exchangeRateService.convert(paymentAmount, paymentCurrency, accountCurrency);
        if (!paymentCurrency.equals(accountCurrency)) {
            log.info("Top-up FX: {} {} → {} {} (pi={})",
                    paymentAmount, paymentCurrency, creditAmount, accountCurrency, request.paymentIntentId());
        }

        // Lock row for update
        account = accountRepository.findByIdForUpdate(account.getId()).orElseThrow();

        BigDecimal balanceBefore = account.getAvailableBalance();
        account.setAvailableBalance(balanceBefore.add(creditAmount));
        accountRepository.save(account);

        LedgerEntry entry = ledgerEntryRepository.save(LedgerEntry.builder()
                .accountId(account.getId())
                .transactionId(null)
                .entryType(LedgerEntryType.CREDIT)
                .amount(creditAmount)
                .balanceBefore(balanceBefore)
                .balanceAfter(account.getAvailableBalance())
                .idempotencyKey(request.paymentIntentId())
                .description("Wallet top-up via Stripe " + request.paymentIntentId()
                        + (paymentCurrency.equals(accountCurrency) ? ""
                           : " (" + paymentAmount + " " + paymentCurrency + ")"))
                .build());

        log.info("Top-up credited: user={} pi={} {} {} (paid {} {})",
                userId, request.paymentIntentId(), creditAmount, accountCurrency, paymentAmount, paymentCurrency);

        return new TopUpConfirmResponse("SUCCEEDED", entry.getId(), creditAmount, accountCurrency);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private PaymentIntent retrieveIntent(String paymentIntentId) {
        try {
            return PaymentIntent.retrieve(paymentIntentId, STRIPE_REQUEST_OPTIONS);
        } catch (StripeException e) {
            log.error("Stripe error retrieving PaymentIntent {}: {}", paymentIntentId, e.getMessage(), e);
            throw new IllegalStateException("Could not retrieve payment: " + e.getMessage(), e);
        }
    }
}
