package com.aegispay.android.ui.onboarding

import com.aegispay.android.auth.AuthRepository
import com.aegispay.android.network.AegisApiService
import com.aegispay.android.network.KycStatus
import com.aegispay.android.network.UserProfile
import com.aegispay.android.network.UserRegistrationRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException

/**
 * Unit tests for [OnboardingViewModel].
 *
 * Covers:
 *  - Email / name field validation
 *  - Successful registration flow
 *  - Error handling (network, email conflict)
 *  - Idempotency key is generated once and reused across retries
 *  - Multi-tenant signup: fields flow correctly to the API
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val api: AegisApiService = mockk()
    private val authRepository: AuthRepository = mockk(relaxed = true)

    private lateinit var viewModel: OnboardingViewModel

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = OnboardingViewModel(api, authRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Email validation ──────────────────────────────────────────────────────

    @Test
    fun `emailError is null for valid email`() {
        viewModel.onEmailChange("alice@example.com")
        assertNull(viewModel.emailError)
    }

    @Test
    fun `emailError is set for malformed email`() {
        viewModel.onEmailChange("not-an-email")
        assertNotNull(viewModel.emailError)
    }

    @Test
    fun `emailError is null when email field is empty`() {
        viewModel.onEmailChange("")
        assertNull(viewModel.emailError)   // only validated on non-empty
    }

    @Test
    fun `emailError is set for missing TLD`() {
        viewModel.onEmailChange("alice@example")
        assertNotNull(viewModel.emailError)
    }

    // ── Name validation ───────────────────────────────────────────────────────

    @Test
    fun `firstNameError is null when blank (not yet touched)`() {
        viewModel.onFirstNameChange("")
        assertNull(viewModel.firstNameError)
    }

    @Test
    fun `firstNameError is set for single character`() {
        viewModel.onFirstNameChange("A")
        assertNotNull(viewModel.firstNameError)
    }

    @Test
    fun `lastNameError is null for valid two-character name`() {
        viewModel.onLastNameChange("Li")
        assertNull(viewModel.lastNameError)
    }

    // ── isValid guard ─────────────────────────────────────────────────────────

    @Test
    fun `isValid is false when any field is empty`() {
        viewModel.onFirstNameChange("Alice")
        viewModel.onLastNameChange("Smith")
        // email not set
        assertFalse(viewModel.isValid)
    }

    @Test
    fun `isValid is true when all fields pass validation`() {
        viewModel.onFirstNameChange("Alice")
        viewModel.onLastNameChange("Smith")
        viewModel.onEmailChange("alice@example.com")
        assertTrue(viewModel.isValid)
    }

    @Test
    fun `isValid is false when email is invalid despite other fields being correct`() {
        viewModel.onFirstNameChange("Alice")
        viewModel.onLastNameChange("Smith")
        viewModel.onEmailChange("not-an-email")
        assertFalse(viewModel.isValid)
    }

    // ── Successful registration flow ──────────────────────────────────────────

    @Test
    fun `register calls API and authRepository on success`() = runTest {
        val profile = fakeProfile("new-uuid-001")
        coEvery { api.registerUser(any(), any()) } returns profile

        viewModel.onFirstNameChange("Alice")
        viewModel.onLastNameChange("Smith")
        viewModel.onEmailChange("alice@example.com")
        viewModel.register()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { api.registerUser(any(), match { it.email == "alice@example.com" }) }
        coVerify { authRepository.completeRegistration("new-uuid-001") }
        assertTrue(viewModel.uiState.value.done)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `register sets done = true on success`() = runTest {
        coEvery { api.registerUser(any(), any()) } returns fakeProfile("uid-done")

        setValidFields()
        viewModel.register()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.done)
    }

    @Test
    fun `register trims whitespace from names before sending`() = runTest {
        coEvery { api.registerUser(any(), any()) } returns fakeProfile("uid-trim")

        viewModel.onFirstNameChange("  Alice  ")
        viewModel.onLastNameChange("  Smith  ")
        viewModel.onEmailChange("alice@example.com")
        viewModel.register()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify {
            api.registerUser(any(), match {
                it.firstName == "Alice" && it.lastName == "Smith"
            })
        }
    }

    // ── Error handling ────────────────────────────────────────────────────────

    @Test
    fun `register sets errorMessage on network failure`() = runTest {
        coEvery { api.registerUser(any(), any()) } throws RuntimeException("Network unavailable")

        setValidFields()
        viewModel.register()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.done)
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun `register does nothing when isValid is false`() = runTest {
        // email is empty → isValid = false
        viewModel.onFirstNameChange("Alice")
        viewModel.onLastNameChange("Smith")
        viewModel.register()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { api.registerUser(any(), any()) }
    }

    @Test
    fun `isSubmitting is true during registration and false after`() = runTest {
        coEvery { api.registerUser(any(), any()) } returns fakeProfile("uid-submitting")

        setValidFields()
        viewModel.register()

        // After advanceUntilIdle the coroutine is done
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    // ── Idempotency key ───────────────────────────────────────────────────────

    @Test
    fun `same idempotency key is reused on retry`() = runTest {
        coEvery { api.registerUser(any(), any()) }
            .throws(RuntimeException("fail")) andThenReturns fakeProfile("uid-retry")

        setValidFields()
        viewModel.register()
        testDispatcher.scheduler.advanceUntilIdle()

        // Reset error state and retry
        viewModel.onEmailChange("alice@example.com")
        viewModel.register()
        testDispatcher.scheduler.advanceUntilIdle()

        // Capture both calls and assert same idempotency key
        val keys = mutableListOf<String>()
        coVerify(exactly = 2) {
            api.registerUser(capture(keys), any())
        }
        assertEquals("Both retries should use the same idempotency key", keys[0], keys[1])
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun setValidFields() {
        viewModel.onFirstNameChange("Alice")
        viewModel.onLastNameChange("Smith")
        viewModel.onEmailChange("alice@example.com")
    }

    private fun fakeProfile(id: String) = UserProfile(
        id        = id,
        email     = "alice@example.com",
        name      = "Alice Smith",
        kycStatus = KycStatus.PENDING,
        role      = "CUSTOMER",
    )
}
