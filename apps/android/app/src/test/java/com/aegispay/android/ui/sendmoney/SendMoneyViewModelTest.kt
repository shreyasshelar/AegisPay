package com.aegispay.android.ui.sendmoney

import com.aegispay.android.auth.AuthRepository
import com.aegispay.android.network.AegisApiService
import com.aegispay.android.network.FxRateRepository
import com.aegispay.android.network.FxRates
import com.aegispay.android.offline.OfflinePaymentQueue
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SendMoneyViewModel].
 *
 * Tests cover:
 * - recalculateRiskWarning(): INR amounts below/above ₹10,000 threshold
 * - recalculateRiskWarning(): foreign-currency amounts converted to INR via live rates
 * - riskWarning cleared on reset()
 * - Payee/amount validation
 * - Step navigation
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SendMoneyViewModelTest {

    @MockK lateinit var api:                AegisApiService
    @MockK lateinit var authRepository:     AuthRepository
    @MockK lateinit var okHttpClient:       OkHttpClient
    @MockK lateinit var offlinePaymentQueue: OfflinePaymentQueue
    @MockK lateinit var fxRateRepository:   FxRateRepository

    private lateinit var viewModel: SendMoneyViewModel
    private val testDispatcher = UnconfinedTestDispatcher()
    private val liveRates = FxRates(usd = 0.01190, eur = 0.01099, gbp = 0.00936)

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(testDispatcher)
        every { authRepository.currentUserId } returns null
        coEvery { fxRateRepository.rates() }  returns liveRates

        viewModel = SendMoneyViewModel(
            api, authRepository, okHttpClient, offlinePaymentQueue, fxRateRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    // ── Risk warning — INR ────────────────────────────────────────────────────

    @Test
    fun `riskWarning null below threshold INR 9999`() = runTest {
        viewModel.onCurrencyChange("INR")
        viewModel.onAmountChange("9999")
        viewModel.recalculateRiskWarning()

        assertNull(viewModel.uiState.first().riskWarning)
    }

    @Test
    fun `riskWarning shown at exact threshold INR 10000`() = runTest {
        viewModel.onCurrencyChange("INR")
        viewModel.onAmountChange("10000")
        viewModel.recalculateRiskWarning()

        assertNotNull(viewModel.uiState.first().riskWarning)
    }

    @Test
    fun `riskWarning shown above threshold INR 50000`() = runTest {
        viewModel.onCurrencyChange("INR")
        viewModel.onAmountChange("50000")
        viewModel.recalculateRiskWarning()

        val warning = viewModel.uiState.first().riskWarning
        assertNotNull(warning)
        assertTrue("Should mention risk review", warning!!.contains("risk"))
    }

    @Test
    fun `riskWarning cleared when amount drops below threshold`() = runTest {
        // First set above threshold
        viewModel.onCurrencyChange("INR")
        viewModel.onAmountChange("15000")
        viewModel.recalculateRiskWarning()
        assertNotNull(viewModel.uiState.first().riskWarning)

        // Then drop below
        viewModel.onAmountChange("5000")
        viewModel.recalculateRiskWarning()
        assertNull(viewModel.uiState.first().riskWarning)
    }

    @Test
    fun `riskWarning cleared when amount is empty`() = runTest {
        viewModel.onCurrencyChange("INR")
        viewModel.onAmountChange("15000")
        viewModel.recalculateRiskWarning()

        viewModel.onAmountChange("")
        viewModel.recalculateRiskWarning()
        assertNull(viewModel.uiState.first().riskWarning)
    }

    @Test
    fun `riskWarning cleared for invalid text`() = runTest {
        viewModel.onCurrencyChange("INR")
        viewModel.onAmountChange("not-a-number")
        viewModel.recalculateRiskWarning()

        assertNull(viewModel.uiState.first().riskWarning)
    }

    // ── Risk warning — foreign currency ───────────────────────────────────────

    @Test
    fun `riskWarning shown for 200 GBP (approx 21367 INR)`() = runTest {
        viewModel.onCurrencyChange("GBP")
        viewModel.onAmountChange("200")
        viewModel.recalculateRiskWarning()

        assertNotNull("200 GBP ≈ ₹21,367 should trigger warning",
            viewModel.uiState.first().riskWarning)
    }

    @Test
    fun `riskWarning NOT shown for 10 GBP (approx 1068 INR)`() = runTest {
        viewModel.onCurrencyChange("GBP")
        viewModel.onAmountChange("10")
        viewModel.recalculateRiskWarning()

        assertNull("10 GBP ≈ ₹1,068 should NOT trigger warning",
            viewModel.uiState.first().riskWarning)
    }

    @Test
    fun `riskWarning shown for 120 USD (approx 10084 INR)`() = runTest {
        viewModel.onCurrencyChange("USD")
        viewModel.onAmountChange("120")
        viewModel.recalculateRiskWarning()

        assertNotNull("120 USD ≈ ₹10,084 should trigger warning",
            viewModel.uiState.first().riskWarning)
    }

    @Test
    fun `riskWarning NOT shown for 50 USD (approx 4202 INR)`() = runTest {
        viewModel.onCurrencyChange("USD")
        viewModel.onAmountChange("50")
        viewModel.recalculateRiskWarning()

        assertNull("50 USD ≈ ₹4,202 should NOT trigger warning",
            viewModel.uiState.first().riskWarning)
    }

    @Test
    fun `riskWarning shown for 110 EUR (approx 10009 INR)`() = runTest {
        viewModel.onCurrencyChange("EUR")
        viewModel.onAmountChange("110")
        viewModel.recalculateRiskWarning()

        assertNotNull("110 EUR ≈ ₹10,009 should trigger warning",
            viewModel.uiState.first().riskWarning)
    }

    // ── reset() ───────────────────────────────────────────────────────────────

    @Test
    fun `reset clears riskWarning`() = runTest {
        viewModel.onCurrencyChange("INR")
        viewModel.onAmountChange("50000")
        viewModel.recalculateRiskWarning()
        assertNotNull(viewModel.uiState.first().riskWarning)

        viewModel.reset()
        assertNull(viewModel.uiState.first().riskWarning)
    }

    @Test
    fun `reset clears all form fields`() = runTest {
        viewModel.onPayeeIdChange("some-uuid")
        viewModel.onAmountChange("5000")
        viewModel.onCurrencyChange("GBP")
        viewModel.onNoteChange("birthday gift")
        viewModel.reset()

        val state = viewModel.uiState.first()
        assertEquals("", state.payeeId)
        assertEquals("", state.amountText)
        assertEquals("INR", state.currency)
        assertEquals("", state.note)
        assertEquals(SendStep.PAYEE, state.step)
    }

    // ── Validation ────────────────────────────────────────────────────────────

    @Test
    fun `amountError null for empty text`() {
        viewModel.onAmountChange("")
        assertNull(viewModel.amountError)
    }

    @Test
    fun `amountError null for valid positive amount`() {
        viewModel.onAmountChange("1500")
        assertNull(viewModel.amountError)
    }

    @Test
    fun `amountError not null for zero`() {
        viewModel.onAmountChange("0")
        assertNotNull(viewModel.amountError)
    }

    @Test
    fun `amountError not null for non-numeric`() {
        viewModel.onAmountChange("abc")
        assertNotNull(viewModel.amountError)
    }

    @Test
    fun `amountError not null for amount exceeding max`() {
        viewModel.onAmountChange("1000001")
        assertNotNull(viewModel.amountError)
    }

    @Test
    fun `payeeIdError not null for invalid UUID format`() {
        viewModel.onPayeeIdChange("not-a-uuid")
        assertNotNull(viewModel.payeeIdError)
    }

    @Test
    fun `payeeIdError null for valid UUID`() {
        viewModel.onPayeeIdChange("550e8400-e29b-41d4-a716-446655440000")
        assertNull(viewModel.payeeIdError)
    }

    // ── Step navigation ───────────────────────────────────────────────────────

    @Test
    fun `goTo changes step`() = runTest {
        viewModel.goTo(SendStep.AMOUNT)
        assertEquals(SendStep.AMOUNT, viewModel.uiState.first().step)
    }

    @Test
    fun `back from AMOUNT goes to PAYEE`() = runTest {
        viewModel.goTo(SendStep.AMOUNT)
        viewModel.back()
        assertEquals(SendStep.PAYEE, viewModel.uiState.first().step)
    }

    @Test
    fun `back from REVIEW goes to AMOUNT`() = runTest {
        viewModel.goTo(SendStep.REVIEW)
        viewModel.back()
        assertEquals(SendStep.AMOUNT, viewModel.uiState.first().step)
    }

    @Test
    fun `back from PAYEE does not change step`() = runTest {
        viewModel.back()
        assertEquals(SendStep.PAYEE, viewModel.uiState.first().step)
    }
}
