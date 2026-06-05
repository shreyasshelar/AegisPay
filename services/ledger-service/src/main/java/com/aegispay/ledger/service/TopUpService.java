package com.aegispay.ledger.service;

import com.aegispay.common.domain.enums.LedgerEntryType;
import com.aegispay.ledger.domain.dto.TopUpConfirmRequest;
import com.aegispay.ledger.domain.dto.TopUpConfirmResponse;
import com.aegispay.ledger.domain.dto.TopUpIntentRequest;
import com.aegispay.ledger.domain.dto.TopUpIntentResponse;
import com.aegispay.ledger.domain.entity.Account;
import com.aegispay.ledger.domain.entity.LedgerEntry;
import com.aegispay.ledger.exception.AccountNotFoundException;
import com.aegispay.ledger.exception.BalanceLimitExceededException;
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
     * Maximum wallet balance per account (native currency).
     * Top-ups that would push the balance above this are rejected before Stripe is charged.
     * Configured via {@code aegispay.ledger.topup.max-balance} (default 100,000).
     */
    @Value("${aegispay.ledger.topup.max-balance:100000}")
    private BigDecimal maxBalance;

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
     *
     * <p>Rejects the request before calling Stripe if the top-up would push the account
     * above the configured {@code max-balance} threshold — preventing a Stripe charge that
     * would then be rolled back at confirm time.
     *
     * <p>The client-side SDK uses {@code clientSecret} to confirm payment.
     * No balance is credited yet — that happens in {@link #confirmTopUp}.
     */
    public TopUpIntentResponse createIntent(UUID userId, TopUpIntentRequest request) {
        // ── Balance cap pre-check ─────────────────────────────────────────────
        // Find existing account for this currency (or any account if none exists yet).
        // If no account exists the user will get one created at confirm time — skip check.
        accountRepository
                .findByUserIdAndCurrency(userId, request.currency().toUpperCase())
                .or(() -> accountRepository.findByUserId(userId).stream().findFirst())
                .ifPresent(account -> {
                    BigDecimal currentBalance = account.getAvailableBalance();
                    BigDecimal projected = currentBalance.add(request.amount());
                    if (projected.compareTo(maxBalance) > 0) {
                        throw new BalanceLimitExceededException(
                                currentBalance, request.amount(), maxBalance, account.getCurrency());
                    }
                });

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

        // Find account — prefer matching currency, fall back to user's primary account.
        // Lazy creation: if no account exists (e.g. user.registered Kafka event was missed
        // during a deployment restart), auto-create a ₹0 INR account here.  This is safe
        // because UserServiceClient.resolveUserId() already proved the user exists and the
        // Stripe payment has already been confirmed as succeeded — we must not drop the credit.
        Account account = accountRepository
                .findByUserIdAndCurrency(userId, paymentCurrency)
                .or(() -> accountRepository.findByUserId(userId).stream().findFirst())
                .orElseGet(() -> {
                    log.warn("No ledger account found for userId={} pi={} — auto-creating default INR account "
                            + "(user.registered Kafka event was likely missed during deployment)", userId,
                            request.paymentIntentId());
                    return accountRepository.save(Account.builder()
                            .userId(userId)
                            .currency("INR")
                            .availableBalance(BigDecimal.ZERO)
                            .reservedBalance(BigDecimal.ZERO)
                            .tenantId("default")
                            .build());
                });

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

        // ── Balance cap defence-in-depth ──────────────────────────────────────
        // The pre-check in createIntent guards against obvious over-limit requests, but
        // concurrent top-ups or a race between check and confirm could still exceed the
        // cap.  Enforce again here while holding the row lock — no charge has settled yet.
        BigDecimal projected = balanceBefore.add(creditAmount);
        if (projected.compareTo(maxBalance) > 0) {
            throw new BalanceLimitExceededException(
                    balanceBefore, creditAmount, maxBalance, accountCurrency);
        }

        account.setAvailableBalance(projected);
        accountRepository.save(account);

        LedgerEntry entry = ledgerEntryRepository.save(LedgerEntry.builder()
                .accountId(account.getId())
                .transactionId(null)
                .entryType(LedgerEntryType.CREDIT)
                .amount(creditAmount)
                .balanceBefore(balanceBefore)
                .balanceAfter(projected)
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
