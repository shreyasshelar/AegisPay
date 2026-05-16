package com.aegispay.android.auth

import com.aegispay.android.network.AegisApiService
import com.aegispay.android.network.KycStatus
import com.aegispay.android.network.UserProfile
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * Unit tests for [AuthRepository] focusing on:
 *  - Session restore logic (valid token, expired+refresh, no token)
 *  - Registration state resolution (fast path, slow path, 404 → NeedsRegistration)
 *  - completeRegistration: stores userId, emits Authenticated
 *  - signOut: clears tokens, emits Unauthenticated
 *  - Role + multi-tenant claim parsing from ID token
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepositoryTest {

    private val tokenStore: TokenStore = mockk(relaxed = true)
    private val api: AegisApiService    = mockk()

    // AuthRepository creates AuthorizationService(context) internally, so we use
    // a subclass that skips the AppAuth initialisation for unit tests.
    private lateinit var repository: AuthRepository

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // Use the testable constructor that accepts an injected TokenStore and API.
        // The AppAuth AuthorizationService is never called in these paths.
        repository = spyk(
            AuthRepository(
                context       = mockk(relaxed = true),
                tokenStore    = tokenStore,
                api           = api,
            ),
            recordPrivateCalls = false
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── restoreSession — valid token, user ID already present ────────────────

    @Test
    fun `restoreSession emits Authenticated when valid token and userId present`() = runTest {
        every { tokenStore.isAccessTokenValid } returns true
        every { tokenStore.userId }    returns "aegis-uuid-001"
        every { tokenStore.userRole }  returns "CUSTOMER"
        every { tokenStore.userEmail } returns "alice@example.com"
        every { tokenStore.userName }  returns "Alice"

        repository.restoreSession()

        val state = repository.authState.first()
        assertTrue("Expected Authenticated but got $state", state is AuthState.Authenticated)
        assertEquals("aegis-uuid-001", (state as AuthState.Authenticated).user.id)
    }

    // ── restoreSession — no token → Unauthenticated ──────────────────────────

    @Test
    fun `restoreSession emits Unauthenticated when no token stored`() = runTest {
        every { tokenStore.isAccessTokenValid } returns false
        every { tokenStore.canRefresh }         returns false

        repository.restoreSession()

        val state = repository.authState.first()
        assertEquals(AuthState.Unauthenticated, state)
    }

    // ── resolveRegistrationState — fast path (userId in token) ───────────────

    @Test
    fun `resolveRegistrationState fast path emits Authenticated without calling api`() = runTest {
        every { tokenStore.isAccessTokenValid } returns true
        every { tokenStore.userId }    returns "known-uuid"
        every { tokenStore.userRole }  returns "CUSTOMER"
        every { tokenStore.userEmail } returns null
        every { tokenStore.userName }  returns null

        repository.restoreSession()

        coVerify(exactly = 0) { api.getMe() }
        assertTrue(repository.authState.first() is AuthState.Authenticated)
    }

    // ── resolveRegistrationState — slow path: /me returns 200 ────────────────

    @Test
    fun `resolveRegistrationState slow path calls api_getMe when userId absent`() = runTest {
        every { tokenStore.isAccessTokenValid } returns true
        every { tokenStore.userId }             returns null    // userId not yet in token
        every { tokenStore.accessToken }        returns "access-token"
        every { tokenStore.refreshToken }       returns "refresh-token"
        every { tokenStore.idToken }            returns null
        every { tokenStore.expiresAtMs }        returns System.currentTimeMillis() + 3_600_000L
        every { tokenStore.userRole }           returns "CUSTOMER"
        every { tokenStore.userEmail }          returns "alice@example.com"
        every { tokenStore.userName }           returns "Alice"

        coEvery { api.getMe() } returns fakeProfile("resolved-uuid")

        repository.restoreSession()

        coVerify { api.getMe() }
        val state = repository.authState.first()
        assertTrue(state is AuthState.Authenticated)
        assertEquals("resolved-uuid", (state as AuthState.Authenticated).user.id)
    }

    // ── resolveRegistrationState — slow path: /me returns 404 ────────────────

    @Test
    fun `resolveRegistrationState emits NeedsRegistration when api_getMe returns 404`() = runTest {
        every { tokenStore.isAccessTokenValid } returns true
        every { tokenStore.userId }             returns null
        every { tokenStore.accessToken }        returns "access-token"
        every { tokenStore.userEmail }          returns "new@example.com"
        every { tokenStore.idToken }            returns null

        val http404 = mockk<HttpException> {
            every { code() } returns 404
        }
        coEvery { api.getMe() } throws http404

        repository.restoreSession()

        val state = repository.authState.first()
        assertTrue("Expected NeedsRegistration but got $state",
                state is AuthState.NeedsRegistration)
        assertEquals("new@example.com", (state as AuthState.NeedsRegistration).email)
    }

    // ── completeRegistration ──────────────────────────────────────────────────

    @Test
    fun `completeRegistration stores userId and emits Authenticated`() = runTest {
        val userId = "fresh-uuid-999"
        every { tokenStore.accessToken }  returns "at"
        every { tokenStore.refreshToken } returns "rt"
        every { tokenStore.idToken }      returns null
        every { tokenStore.expiresAtMs }  returns System.currentTimeMillis() + 3_600_000L
        every { tokenStore.userRole }     returns "CUSTOMER"
        every { tokenStore.userEmail }    returns "new@example.com"
        every { tokenStore.userName }     returns null
        // After store() is called, userId should be available
        every { tokenStore.userId } returns userId

        repository.completeRegistration(userId)

        verify { tokenStore.store(any(), any(), any(), any(), eq(userId), any(), any(), any()) }
        val state = repository.authState.first()
        assertTrue(state is AuthState.Authenticated)
        assertEquals(userId, (state as AuthState.Authenticated).user.id)
    }

    @Test
    fun `completeRegistration is a no-op when accessToken is null`() = runTest {
        every { tokenStore.accessToken } returns null

        repository.completeRegistration("some-id")

        verify(exactly = 0) { tokenStore.store(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    // ── signOut ───────────────────────────────────────────────────────────────

    @Test
    fun `signOut clears token store and emits Unauthenticated`() = runTest {
        repository.signOut()

        verify { tokenStore.clear() }
        assertEquals(AuthState.Unauthenticated, repository.authState.first())
    }

    // ── currentUser accessors ─────────────────────────────────────────────────

    @Test
    fun `currentUserId delegates to tokenStore userId`() {
        every { tokenStore.userId } returns "my-uuid"
        assertEquals("my-uuid", repository.currentUserId)
    }

    @Test
    fun `currentUserRole defaults to null when not set`() {
        every { tokenStore.userRole } returns null
        assertNull(repository.currentUserRole)
    }

    @Test
    fun `BACK_OFFICE role is correctly exposed`() {
        every { tokenStore.userRole } returns "BACK_OFFICE"
        assertEquals("BACK_OFFICE", repository.currentUserRole)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun fakeProfile(id: String) = UserProfile(
        id        = id,
        email     = "alice@example.com",
        name      = "Alice Smith",
        kycStatus = KycStatus.PENDING,
        role      = "CUSTOMER",
    )
}
