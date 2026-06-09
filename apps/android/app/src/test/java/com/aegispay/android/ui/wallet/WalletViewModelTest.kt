package com.aegispay.android.ui.wallet

import com.aegispay.android.network.Account
import com.aegispay.android.network.AegisApiService
import com.aegispay.android.network.FxRateRepository
import com.aegispay.android.network.FxRates
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

/**
 * Unit tests for [WalletViewModel].
 *
 * Tests cover:
 * - BALANCE_LIMIT_INR constant value
 * - inrAvailable / inrRoom computed properties
 * - wouldExceedLimit guard
 * - loadAccounts: success and error paths
 * - createTopUpIntent: hard-block when INR would exceed limit
 * - FX rates loaded alongside accounts
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WalletViewModelTest {

    @MockK lateinit var api:        AegisApiService
    @MockK lateinit var fxRateRepo: FxRateRepository

    private lateinit var viewModel: WalletViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    private val liveRates = FxRates(usd = 0.01190, eur = 0.01099, gbp = 0.00936)

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(testDispatcher)
        coEvery { fxRateRepo.rates() } returns liveRates
        coEvery { api.getMyAccount()  } returns listOf(inrAccount(BigDecimal("0")))
        viewModel = WalletViewModel(api, fxRateRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    // ── BALANCE_LIMIT_INR ─────────────────────────────────────────────────────

    @Test
    fun `BALANCE_LIMIT_INR is 100000`() {
        assertEquals(BigDecimal("100000"), BALANCE_LIMIT_INR)
    }

    // ── inrAvailable ──────────────────────────────────────────────────────────

    @Test
    fun `inrAvailable returns null when no INR account`() = runTest {
        coEvery { api.getMyAccount() } returns listOf(gbpAccount(BigDecimal("500")))
        viewModel.loadAccounts()
        assertNull(viewModel.inrAvailable)
    }

    @Test
    fun `inrAvailable returns balance of INR account`() = runTest {
        coEvery { api.getMyAccount() } returns listOf(inrAccount(BigDecimal("45000")))
        viewModel.loadAccounts()
        assertEquals(BigDecimal("45000"), viewModel.inrAvailable)
    }

    // ── inrRoom ───────────────────────────────────────────────────────────────

    @Test
    fun `inrRoom is full limit when no accounts`() = runTest {
        coEvery { api.getMyAccount() } returns emptyList()
        viewModel.loadAccounts()
        assertEquals(BALANCE_LIMIT_INR, viewModel.inrRoom)
    }

    @Test
    fun `inrRoom is 50000 when INR balance is 50000`() = runTest {
        coEvery { api.getMyAccount() } returns listOf(inrAccount(BigDecimal("50000")))
        viewModel.loadAccounts()
        assertEquals(BigDecimal("50000"), viewModel.inrRoom)
    }

    @Test
    fun `inrRoom is zero when at limit`() = runTest {
        coEvery { api.getMyAccount() } returns listOf(inrAccount(BigDecimal("100000")))
        viewModel.loadAccounts()
        assertEquals(BigDecimal.ZERO, viewModel.inrRoom)
    }

    @Test
    fun `inrRoom is never negative`() = runTest {
        coEvery { api.getMyAccount() } returns listOf(inrAccount(BigDecimal("150000")))
        viewModel.loadAccounts()
        assertTrue("Room must be ≥ 0", viewModel.inrRoom >= BigDecimal.ZERO)
    }

    // ── wouldExceedLimit ──────────────────────────────────────────────────────

    @Test
    fun `wouldExceedLimit false for small amount`() = runTest {
        coEvery { api.getMyAccount() } returns listOf(inrAccount(BigDecimal("50000")))
        viewModel.loadAccounts()
        assertFalse(viewModel.wouldExceedLimit(BigDecimal("1000")))
    }

    @Test
    fun `wouldExceedLimit false at exact limit`() = runTest {
        coEvery { api.getMyAccount() } returns listOf(inrAccount(BigDecimal("90000")))
        viewModel.loadAccounts()
        assertFalse(viewModel.wouldExceedLimit(BigDecimal("10000")))
    }

    @Test
    fun `wouldExceedLimit true for one rupee over`() = runTest {
        coEvery { api.getMyAccount() } returns listOf(inrAccount(BigDecimal("90000")))
        viewModel.loadAccounts()
        assertTrue(viewModel.wouldExceedLimit(BigDecimal("10001")))
    }

    // ── loadAccounts ──────────────────────────────────────────────────────────

    @Test
    fun `loadAccounts success emits Success state`() = runTest {
        val accounts = listOf(inrAccount(BigDecimal("10000")))
        coEvery { api.getMyAccount() } returns accounts
        viewModel.loadAccounts()

        val state = viewModel.uiState.first()
        assertTrue(state is WalletUiState.Success)
        assertEquals(accounts, (state as WalletUiState.Success).accounts)
    }

    @Test
    fun `loadAccounts error emits Error state`() = runTest {
        coEvery { api.getMyAccount() } throws RuntimeException("Network error")
        viewModel.loadAccounts()

        val state = viewModel.uiState.first()
        assertTrue(state is WalletUiState.Error)
        assertTrue((state as WalletUiState.Error).message.contains("Network error"))
    }

    @Test
    fun `loadAccounts also loads fx rates`() = runTest {
        viewModel.loadAccounts()
        coVerify { fxRateRepo.rates() }
        assertEquals(liveRates, viewModel.fxRates.first())
    }

    // ── createTopUpIntent: hard limit block ───────────────────────────────────

    @Test
    fun `createTopUpIntent blocked when INR would exceed limit`() = runTest {
        coEvery { api.getMyAccount() } returns listOf(inrAccount(BigDecimal("95000")))
        viewModel.loadAccounts()

        // 95,000 + 6,000 = 101,000 → exceeds limit
        viewModel.createTopUpIntent(BigDecimal("6000"), "INR")

        // Should not call the API
        coVerify(exactly = 0) { api.createTopUpIntent(any()) }
        // State should reflect failure
        val state = viewModel.uiState.first()
        assertTrue(state is WalletUiState.Success)
        assertEquals(TopUpResult.FAILED, (state as WalletUiState.Success).topUpResult)
    }

    @Test
    fun `createTopUpIntent allowed when within limit`() = runTest {
        coEvery { api.getMyAccount() } returns listOf(inrAccount(BigDecimal("50000")))
        coEvery { api.createTopUpIntent(any()) } returns
                com.aegispay.android.network.TopUpIntentResponse("pi_123", "pi_123_secret", BigDecimal("1000"), "INR")
        viewModel.loadAccounts()

        viewModel.createTopUpIntent(BigDecimal("1000"), "INR")

        coVerify(exactly = 1) { api.createTopUpIntent(any()) }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun inrAccount(balance: BigDecimal) = Account(
        id               = "acc-inr-1",
        userId           = "user-1",
        currency         = "INR",
        availableBalance = balance,
        reservedBalance  = BigDecimal.ZERO,
    )

    private fun gbpAccount(balance: BigDecimal) = Account(
        id               = "acc-gbp-1",
        userId           = "user-1",
        currency         = "GBP",
        availableBalance = balance,
        reservedBalance  = BigDecimal.ZERO,
    )
}
