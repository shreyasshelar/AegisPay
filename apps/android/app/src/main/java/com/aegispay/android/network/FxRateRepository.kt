package com.aegispay.android.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URL
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

// ── Data ──────────────────────────────────────────────────────────────────────

/**
 * ECB-sourced FX rates via Frankfurter API.
 * Rates are from the perspective of 1 INR = X foreign currency.
 *
 * Example: rates.usd = 0.01190 means 1 INR = 0.01190 USD.
 * To convert `amount` foreign currency to INR: inr = amount / rates.forCurrency(currency)
 */
data class FxRates(
    val usd: Double = 0.01190,
    val eur: Double = 0.01099,
    val gbp: Double = 0.00936,
) {
    /** Returns rate for 1 INR → currency, or null if currency is INR. */
    fun forCurrency(currency: String): Double? = when (currency.uppercase()) {
        "USD" -> usd
        "EUR" -> eur
        "GBP" -> gbp
        else  -> null
    }

    /**
     * Convert [amount] in [currency] to its INR equivalent.
     * Returns null when currency == "INR" (no conversion needed).
     */
    fun toInr(amount: java.math.BigDecimal, currency: String): java.math.BigDecimal? {
        val rate = forCurrency(currency) ?: return null
        return if (rate > 0) amount.divide(
            java.math.BigDecimal(rate),
            4,
            java.math.RoundingMode.HALF_UP,
        ) else null
    }
}

@JsonClass(generateAdapter = true)
private data class FrankfurterResponse(
    @Json(name = "rates") val rates: Map<String, Double>,
)

// ── Repository ────────────────────────────────────────────────────────────────

/**
 * Fetches live FX rates from Frankfurter API and caches them for 1 hour.
 *
 * URL: https://api.frankfurter.app/latest?base=INR&symbols=USD,EUR,GBP
 *
 * This is a lightweight singleton — no Retrofit, no OkHttp dependency to add.
 * Hilt injects it as a singleton so the 1-hour cache is shared app-wide.
 */
@Singleton
class FxRateRepository @Inject constructor() {

    private val endpointUrl =
        "https://api.frankfurter.app/latest?base=INR&symbols=USD,EUR,GBP"

    private val cacheTtlMs = 3_600_000L  // 1 hour

    private data class CacheEntry(val rates: FxRates, val fetchedAt: Instant)

    private val cache = AtomicReference<CacheEntry?>(null)

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val adapter = moshi.adapter(FrankfurterResponse::class.java)

    /** Returns cached rates if fresh, otherwise fetches from network. Falls back to defaults on error. */
    suspend fun rates(): FxRates {
        val entry = cache.get()
        if (entry != null &&
            Instant.now().toEpochMilli() - entry.fetchedAt.toEpochMilli() < cacheTtlMs
        ) {
            return entry.rates
        }
        return try {
            val fresh = fetch()
            cache.set(CacheEntry(fresh, Instant.now()))
            fresh
        } catch (e: Exception) {
            // Return stale cache if available, otherwise hard-coded ECB mid rates
            entry?.rates ?: FxRates()
        }
    }

    /** Force-clear the cache (e.g. on manual refresh). */
    fun invalidate() { cache.set(null) }

    // ── Network ───────────────────────────────────────────────────────────────

    private suspend fun fetch(): FxRates = withContext(Dispatchers.IO) {
        val json = URL(endpointUrl).readText()
        val response = adapter.fromJson(json)
            ?: throw IOException("Empty response from Frankfurter")
        FxRates(
            usd = response.rates["USD"] ?: 0.01190,
            eur = response.rates["EUR"] ?: 0.01099,
            gbp = response.rates["GBP"] ?: 0.00936,
        )
    }
}
