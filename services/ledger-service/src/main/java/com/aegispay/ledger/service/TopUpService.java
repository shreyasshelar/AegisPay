package com.aegispay.ledger.service;

import com.aegispay.common.domain.enums.LedgerEntryType;
import com.aegispay.ledger.domain.dto.TopUpConfirmRequest;
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

    @Value("${stripe.secret-key}")
    private String stripeSecretKey;

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
                            .build()
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
    public void confirmTopUp(UUID userId, TopUpConfirmRequest request) {
        PaymentIntent intent = retrieveIntent(request.paymentIntentId());

        // Verify intent belongs to this user (metadata check)
        String metaUserId = intent.getMetadata().get("aegispay_user_id");
        if (metaUserId == null || !metaUserId.equals(userId.toString())) {
            throw new IllegalArgumentException("PaymentIntent does not belong to the authenticated user");
        }

        if (!"succeeded".equals(intent.getStatus())) {
            throw new IllegalStateException(
                    "PaymentIntent status is '" + intent.getStatus() + "', expected 'succeeded'");
        }

        // Idempotency: check if this intent has already been credited
        if (ledgerEntryRepository.existsByIdempotencyKey(request.paymentIntentId())) {
            log.info("Top-up already credited for pi={} — skipping", request.paymentIntentId());
            return;
        }

        // Amount in base units → decimal
        String currency = intent.getCurrency().toUpperCase();
        BigDecimal amount = BigDecimal.valueOf(intent.getAmount())
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);

        // Locate or create the account for this currency
        Account account = accountRepository
                .findByUserIdAndCurrency(userId, currency)
                .or(() -> accountRepository.findByUserId(userId).stream().findFirst())
                .orElseThrow(() -> new AccountNotFoundException(userId));

        // Lock row for update
        account = accountRepository.findByIdForUpdate(account.getId()).orElseThrow();

        BigDecimal balanceBefore = account.getAvailableBalance();
        account.setAvailableBalance(balanceBefore.add(amount));
        accountRepository.save(account);

        ledgerEntryRepository.save(LedgerEntry.builder()
                .accountId(account.getId())
                .transactionId(null)          // top-ups are not P2P transactions
                .entryType(LedgerEntryType.CREDIT)
                .amount(amount)
                .balanceBefore(balanceBefore)
                .balanceAfter(account.getAvailableBalance())
                .idempotencyKey(request.paymentIntentId())
                .description("Wallet top-up via Stripe " + request.paymentIntentId())
                .build());

        log.info("Top-up credited: user={} pi={} {} {}", userId, request.paymentIntentId(), amount, currency);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private PaymentIntent retrieveIntent(String paymentIntentId) {
        try {
            return PaymentIntent.retrieve(paymentIntentId);
        } catch (StripeException e) {
            log.error("Stripe error retrieving PaymentIntent {}: {}", paymentIntentId, e.getMessage(), e);
            throw new IllegalStateException("Could not retrieve payment: " + e.getMessage(), e);
        }
    }
}
