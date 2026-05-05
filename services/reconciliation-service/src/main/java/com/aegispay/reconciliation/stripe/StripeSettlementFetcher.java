package com.aegispay.reconciliation.stripe;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.BalanceTransaction;
import com.stripe.model.BalanceTransactionCollection;
import com.stripe.param.BalanceTransactionListParams;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Fetches settled payment data from Stripe's Balance Transactions API.
 *
 * <p>Stripe's balance transactions represent the actual money movement
 * that happened on the Stripe side — what we compare against our ledger.
 *
 * <p>We filter to {@code type=payment} (successful charges) and
 * {@code type=payment_refund} entries within the report date window.
 */
@Slf4j
@Component
public class StripeSettlementFetcher {

    private static final Set<String> RELEVANT_TYPES = Set.of("payment", "payment_refund");

    @Value("${stripe.secret-key}")
    private String secretKey;

    @Value("${aegispay.reconciliation.lookback-days:2}")
    private int lookbackDays;

    @PostConstruct
    void init() {
        Stripe.apiKey = secretKey;
    }

    /**
     * Returns all Stripe balance transactions for the given report date.
     * Fetches with auto-pagination to handle > 100 entries.
     *
     * @param reportDate the UTC date to reconcile (typically yesterday)
     * @return list of Stripe settlement entries
     */
    public List<StripeSettlementEntry> fetchForDate(LocalDate reportDate) {
        long createdAfter  = reportDate.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        long createdBefore = reportDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond();

        log.info("Fetching Stripe balance transactions for date={} window=[{}, {})",
                reportDate, createdAfter, createdBefore);

        List<StripeSettlementEntry> entries = new ArrayList<>();
        try {
            BalanceTransactionListParams params = BalanceTransactionListParams.builder()
                    .setCreatedRange(BalanceTransactionListParams.Created.builder()
                            .setGte(createdAfter)
                            .setLt(createdBefore)
                            .build())
                    .setLimit(100L)
                    .build();

            BalanceTransactionCollection collection = BalanceTransaction.list(params);

            // Auto-paginate through all pages
            for (BalanceTransaction bt : collection.autoPagingIterable()) {
                if (!RELEVANT_TYPES.contains(bt.getType())) continue;

                String piId = extractPaymentIntentId(bt);
                if (piId == null) continue;

                entries.add(new StripeSettlementEntry(
                        piId,
                        bt.getId(),
                        toDecimal(bt.getAmount(), bt.getCurrency()),
                        bt.getCurrency().toUpperCase(),
                        bt.getStatus(),
                        bt.getType()
                ));
            }

            log.info("Fetched {} relevant Stripe transactions for date={}", entries.size(), reportDate);
        } catch (StripeException e) {
            log.error("Failed to fetch Stripe balance transactions for date={}: {}", reportDate, e.getMessage(), e);
            throw new RuntimeException("Stripe settlement fetch failed", e);
        }

        return entries;
    }

    /**
     * Extract the PaymentIntent ID from a balance transaction.
     * The BalanceTransaction.source is a PaymentIntent or Charge ID —
     * we need the PaymentIntent ID for matching with our metadata.
     */
    private String extractPaymentIntentId(BalanceTransaction bt) {
        // Source can be: pi_xxx (PaymentIntent) or ch_xxx (Charge)
        // Our saga stores the PaymentIntent ID as externalReference
        String source = bt.getSource();
        if (source != null && source.startsWith("pi_")) {
            return source;
        }
        // For charges, we'd need to look up the PaymentIntent — skip for now
        return source; // return as-is; matching logic handles it
    }

    /**
     * Convert Stripe integer amount (smallest currency unit) to BigDecimal.
     * Zero-decimal currencies (JPY, KRW) are not divided by 100.
     */
    private static final Set<String> ZERO_DECIMAL = Set.of(
            "BIF", "CLP", "GNF", "JPY", "KMF", "KRW", "MGA", "PYG",
            "RWF", "UGX", "VND", "VUV", "XAF", "XOF", "XPF"
    );

    private BigDecimal toDecimal(long amount, String currency) {
        if (ZERO_DECIMAL.contains(currency.toUpperCase())) {
            return BigDecimal.valueOf(amount);
        }
        return BigDecimal.valueOf(amount, 2); // divide by 100
    }

    /** Immutable value object for a single Stripe settlement entry. */
    public record StripeSettlementEntry(
            String paymentIntentId,
            String balanceTransactionId,
            BigDecimal amount,
            String currency,
            String status,      // available | pending
            String type         // payment | payment_refund
    ) {}
}
