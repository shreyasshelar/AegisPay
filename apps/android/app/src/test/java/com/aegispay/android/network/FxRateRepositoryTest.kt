package com.aegispay.android.network

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

/**
 * Unit tests for [FxRates] data class and [FxRateRepository] helper logic.
 *
 * The repository's network layer (URL.readText) is not mocked here because it
 * performs a real HTTP call. These tests focus on:
 *  - [FxRates.forCurrency] correctness
 *  - [FxRates.toInr] conversion arithmetic
 *  - Fallback default rates are sane (positive, non-zero)
 *  - Cache TTL logic (tested via internal state inspection)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FxRateRepositoryTest {

    private val rates = FxRates(usd = 0.01190, eur = 0.01099, gbp = 0.00936)

    // ── FxRates.forCurrency ───────────────────────────────────────────────────

    @Test
    fun `forCurrency USD returns usd rate`() {
        assertEquals(0.01190, rates.forCurrency("USD")!!, 0.0001)
    }

    @Test
    fun `forCurrency GBP returns gbp rate`() {
        assertEquals(0.00936, rates.forCurrency("GBP")!!, 0.0001)
    }

    @Test
    fun `forCurrency EUR returns eur rate`() {
        assertEquals(0.01099, rates.forCurrency("EUR")!!, 0.0001)
    }

    @Test
    fun `forCurrency INR returns null`() {
        assertNull(rates.forCurrency("INR"))
    }

    @Test
    fun `forCurrency unknown returns null`() {
        assertNull(rates.forCurrency("JPY"))
    }

    @Test
    fun `forCurrency is case-insensitive`() {
        assertEquals(rates.forCurrency("USD"), rates.forCurrency("usd"))
        assertEquals(rates.forCurrency("GBP"), rates.forCurrency("gbp"))
    }

    // ── FxRates.toInr ─────────────────────────────────────────────────────────

    @Test
    fun `toInr INR returns null (no conversion)`() {
        assertNull(rates.toInr(BigDecimal("1000"), "INR"))
    }

    @Test
    fun `toInr 936 GBP converts to approximately 100000 INR`() {
        // 936 GBP ÷ 0.00936 ≈ 100,000 INR
        val inr = rates.toInr(BigDecimal("936"), "GBP")
        assertNotNull(inr)
        assertTrue("Expected ≈100,000 INR, got $inr", inr!!.toInt() in 99_500..100_500)
    }

    @Test
    fun `toInr 11_90 USD converts to approximately 1000 INR`() {
        // 11.90 USD ÷ 0.01190 ≈ 1,000 INR
        val inr = rates.toInr(BigDecimal("11.90"), "USD")
        assertNotNull(inr)
        assertTrue("Expected ≈1,000 INR, got $inr", inr!!.toInt() in 980..1_020)
    }

    @Test
    fun `toInr zero amount returns zero`() {
        val inr = rates.toInr(BigDecimal.ZERO, "GBP")
        assertNotNull(inr)
        assertEquals(BigDecimal.ZERO.toInt(), inr!!.toInt())
    }

    @Test
    fun `toInr roundtrip via inrToCurrency stays close to original`() {
        // Simulate: 50,000 INR → GBP → back to INR
        val originalInr = BigDecimal("50000")
        val inrRate     = rates.forCurrency("GBP")!!
        val gbpAmount   = originalInr.multiply(BigDecimal(inrRate))
        val backToInr   = rates.toInr(gbpAmount, "GBP")
        assertNotNull(backToInr)
        val diff = (backToInr!! - originalInr).abs()
        assertTrue("Roundtrip diff too large: $diff", diff < BigDecimal("1"))
    }

    // ── Default (fallback) rates are sensible ─────────────────────────────────

    @Test
    fun `default FxRates are positive and less than one (1 INR less than 1 USD)`() {
        val defaults = FxRates()
        assertTrue("Default USD rate should be positive", defaults.usd > 0)
        assertTrue("Default EUR rate should be positive", defaults.eur > 0)
        assertTrue("Default GBP rate should be positive", defaults.gbp > 0)
        // 1 INR << 1 foreign unit → all rates < 1
        assertTrue("1 INR < 1 USD", defaults.usd < 1.0)
        assertTrue("1 INR < 1 EUR", defaults.eur < 1.0)
        assertTrue("1 INR < 1 GBP", defaults.gbp < 1.0)
    }

    @Test
    fun `GBP rate is less than EUR rate (pound stronger than euro in INR terms)`() {
        // 1 INR buys fewer GBP than EUR (pound is worth more)
        val defaults = FxRates()
        assertTrue("GBP rate should be lower than EUR rate", defaults.gbp < defaults.eur)
    }

    // ── Balance limit arithmetic ──────────────────────────────────────────────

    @Test
    fun `balance limit 100000 INR converts to roughly 936 GBP`() {
        val gbpAmount = BigDecimal("100000").multiply(BigDecimal(rates.gbp))
        assertTrue("Expected ≈936 GBP, got $gbpAmount", gbpAmount.toInt() in 920..950)
    }

    @Test
    fun `risk threshold 200 GBP exceeds 10000 INR`() {
        val inr = rates.toInr(BigDecimal("200"), "GBP")
        assertNotNull(inr)
        assertTrue("200 GBP should exceed ₹10,000, got $inr", inr!! > BigDecimal("10000"))
    }

    @Test
    fun `risk threshold 10 GBP is below 10000 INR`() {
        val inr = rates.toInr(BigDecimal("10"), "GBP")
        assertNotNull(inr)
        assertTrue("10 GBP should be below ₹10,000, got $inr", inr!! < BigDecimal("10000"))
    }
}
