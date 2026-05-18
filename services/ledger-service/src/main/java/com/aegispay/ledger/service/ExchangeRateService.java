package com.aegispay.ledger.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Map;

/**
 * Live exchange-rate service backed by the Frankfurter API
 * (https://www.frankfurter.app — ECB data, free, no API key required).
 *
 * <p>Fetched rates are stored in Redis under {@code fx:rates:{BASE}} with a
 * 1-hour TTL so every transaction does not hit the network.  The same Redis
 * instance that the rest of the ledger-service uses ({@code REDIS_HOST /
 * REDIS_PORT / REDIS_PASSWORD}) is reused — no extra infrastructure needed.
 *
 * <p>On any network / parse / Redis failure the service falls back to
 * hardcoded mid-market approximations so the ledger never blocks a
 * transaction due to a transient outage.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeRateService {

    private static final String FRANKFURTER_BASE_URL = "https://api.frankfurter.app";
    private static final String REDIS_KEY_PREFIX     = "fx:rates:";
    private static final Duration CACHE_TTL          = Duration.ofHours(1);

    /** Fallback rates (1 unit → INR) used only when Frankfurter is unreachable. */
    private static final Map<String, BigDecimal> FALLBACK_TO_INR = Map.of(
        "INR", BigDecimal.ONE,
        "USD", new BigDecimal("83.50"),
        "EUR", new BigDecimal("90.25"),
        "GBP", new BigDecimal("105.75")
    );

    private static final TypeReference<Map<String, BigDecimal>> RATE_MAP_TYPE =
            new TypeReference<>() {};

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private final RestClient restClient = RestClient.builder()
            .baseUrl(FRANKFURTER_BASE_URL)
            .requestFactory(frankfurterRequestFactory())
            .build();

    private static SimpleClientHttpRequestFactory frankfurterRequestFactory() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(3_000);   // 3 s — fail fast if Frankfurter unreachable
        f.setReadTimeout(5_000);      // 5 s — response timeout
        return f;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Converts {@code amount} from {@code fromCurrency} to {@code toCurrency}.
     * Rates are served from Redis (TTL 1 h); populated on first miss from
     * Frankfurter; hardcoded fallback if both are unavailable.
     */
    public BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency) {
        String from = fromCurrency.toUpperCase();
        String to   = toCurrency.toUpperCase();
        if (from.equals(to)) return amount;

        BigDecimal rate = getRate(from, to);
        return amount.multiply(rate).setScale(4, RoundingMode.HALF_UP);
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private BigDecimal getRate(String from, String to) {
        // 1. Try Redis
        Map<String, BigDecimal> rates = loadFromRedis(from);

        // 2. Cache miss — fetch live and store
        if (rates == null) {
            rates = fetchAndCache(from);
        }

        // 3. Use rate if available
        if (rates != null) {
            BigDecimal rate = rates.get(to);
            if (rate != null) return rate;
        }

        // 4. Final fallback
        log.warn("Using hardcoded fallback rate for {}/{}", from, to);
        return fallbackRate(from, to);
    }

    private Map<String, BigDecimal> loadFromRedis(String base) {
        try {
            String json = redisTemplate.opsForValue().get(REDIS_KEY_PREFIX + base);
            if (json == null) return null;
            return objectMapper.readValue(json, RATE_MAP_TYPE);
        } catch (Exception e) {
            log.warn("Redis read failed for fx:rates:{}: {}", base, e.getMessage());
            return null;
        }
    }

    /** Fetch from Frankfurter, write to Redis with TTL, return rates map. */
    private Map<String, BigDecimal> fetchAndCache(String base) {
        try {
            FrankfurterResponse resp = restClient.get()
                    .uri("/latest?from={base}", base)
                    .retrieve()
                    .body(FrankfurterResponse.class);

            if (resp == null || resp.rates() == null || resp.rates().isEmpty()) {
                log.warn("Empty Frankfurter response for base={}", base);
                return null;
            }

            writeToRedis(base, resp.rates());
            log.debug("Fetched and cached {} rates from Frankfurter (base={}, date={})",
                    resp.rates().size(), base, resp.date());
            return resp.rates();

        } catch (RestClientException e) {
            log.warn("Frankfurter API unavailable for base={}: {}", base, e.getMessage());
            return null;
        }
    }

    private void writeToRedis(String base, Map<String, BigDecimal> rates) {
        try {
            String json = objectMapper.writeValueAsString(rates);
            redisTemplate.opsForValue().set(REDIS_KEY_PREFIX + base, json, CACHE_TTL);
        } catch (Exception e) {
            // Non-fatal — next request will re-fetch
            log.warn("Redis write failed for fx:rates:{}: {}", base, e.getMessage());
        }
    }

    /** Converts via INR as pivot when live rates are unavailable. */
    private BigDecimal fallbackRate(String from, String to) {
        BigDecimal fromInINR = FALLBACK_TO_INR.getOrDefault(from, BigDecimal.ONE);
        BigDecimal toInINR   = FALLBACK_TO_INR.getOrDefault(to,   BigDecimal.ONE);
        return fromInINR.divide(toInINR, 6, RoundingMode.HALF_UP);
    }

    // ── Frankfurter response ──────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record FrankfurterResponse(
            String base,
            String date,
            Map<String, BigDecimal> rates
    ) {}
}
