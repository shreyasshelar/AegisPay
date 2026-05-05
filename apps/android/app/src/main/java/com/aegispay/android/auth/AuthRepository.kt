package com.aegispay.android.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.aegispay.android.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import net.openid.appauth.*
import org.json.JSONObject
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// ── Auth state ────────────────────────────────────────────────────────────────

sealed interface AuthState {
    data object Loading        : AuthState
    data object Unauthenticated: AuthState
    data class  Authenticated(val user: AuthUser) : AuthState
}

data class AuthUser(
    val id:    String,
    val role:  String,
    val email: String?,
    val name:  String?,
)

// ── AuthRepository ────────────────────────────────────────────────────────────

/**
 * Orchestrates AppAuth PKCE flow + token refresh.
 * All token persistence is delegated to [TokenStore].
 */
@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenStore: TokenStore,
) {
    private val authService = AuthorizationService(context)

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    suspend fun restoreSession() {
        if (tokenStore.isAccessTokenValid) {
            _authState.value = AuthState.Authenticated(buildUser())
        } else if (tokenStore.canRefresh) {
            runCatching { refresh() }
                .onSuccess { _authState.value = AuthState.Authenticated(buildUser()) }
                .onFailure { _authState.value = AuthState.Unauthenticated }
        } else {
            _authState.value = AuthState.Unauthenticated
        }
    }

    // ── PKCE login intent ─────────────────────────────────────────────────────

    /**
     * Builds an [Intent] to start the AppAuth authorization flow.
     * Caller (Activity) passes this to [startActivityForResult].
     */
    suspend fun buildAuthIntent(): Intent = withContext(Dispatchers.IO) {
        val config = fetchServiceConfig()
        val request = AuthorizationRequest.Builder(
            config,
            BuildConfig.OAUTH_CLIENT_ID,
            ResponseTypeValues.CODE,
            Uri.parse(BuildConfig.OAUTH_REDIRECT_URI),
        )
            .setScopes("openid", "email", "profile", "offline_access")
            .build()

        authService.getAuthorizationRequestIntent(request)
    }

    /**
     * Called after the Activity returns from the Chrome Custom Tab.
     * Exchanges the authorization code for tokens and persists them.
     */
    suspend fun handleAuthResponse(intent: Intent) = withContext(Dispatchers.IO) {
        val response = AuthorizationResponse.fromIntent(intent)
            ?: throw AuthException("No authorization response in intent")
        val exception = AuthorizationException.fromIntent(intent)
        if (exception != null) throw exception

        val tokenResponse = exchangeCode(response)
        persistTokens(tokenResponse)
        withContext(Dispatchers.Main) {
            _authState.value = AuthState.Authenticated(buildUser())
        }
    }

    // ── Token access ──────────────────────────────────────────────────────────

    suspend fun validAccessToken(): String {
        if (tokenStore.isAccessTokenValid) {
            return tokenStore.accessToken!!
        }
        if (tokenStore.canRefresh) {
            return refresh()
        }
        signOut()
        throw AuthException("Session expired — please sign in again")
    }

    suspend fun signOut() {
        tokenStore.clear()
        _authState.value = AuthState.Unauthenticated
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private suspend fun fetchServiceConfig(): AuthorizationServiceConfiguration =
        suspendCancellableCoroutine { cont ->
            AuthorizationServiceConfiguration.fetchFromIssuer(
                Uri.parse(BuildConfig.KEYCLOAK_ISSUER)
            ) { config, ex ->
                if (config != null) cont.resume(config)
                else cont.resumeWithException(ex ?: AuthException("Config fetch failed"))
            }
        }

    private suspend fun exchangeCode(
        response: AuthorizationResponse,
    ): TokenResponse = suspendCancellableCoroutine { cont ->
        val tokenRequest = response.createTokenExchangeRequest()
        authService.performTokenRequest(tokenRequest) { tokenResponse, ex ->
            if (tokenResponse != null) cont.resume(tokenResponse)
            else cont.resumeWithException(ex ?: AuthException("Token exchange failed"))
        }
    }

    private suspend fun refresh(): String = withContext(Dispatchers.IO) {
        val config = fetchServiceConfig()
        val request = TokenRequest.Builder(
            config,
            BuildConfig.OAUTH_CLIENT_ID,
        )
            .setGrantType(GrantTypeValues.REFRESH_TOKEN)
            .setRefreshToken(tokenStore.refreshToken!!)
            .build()

        val response: TokenResponse = suspendCancellableCoroutine { cont ->
            authService.performTokenRequest(request) { resp, ex ->
                if (resp != null) cont.resume(resp)
                else cont.resumeWithException(ex ?: AuthException("Token refresh failed"))
            }
        }

        persistTokens(response)
        tokenStore.accessToken!!
    }

    private fun persistTokens(response: TokenResponse) {
        val accessToken  = response.accessToken  ?: return
        val expiresIn    = response.accessTokenExpirationTime
            ?.let { (it - System.currentTimeMillis()) / 1000 } ?: 3600L

        // Extract claims from ID token (best-effort, no sig verification needed here)
        var userId:    String? = null
        var userRole:  String? = null
        var userEmail: String? = null
        var userName:  String? = null

        response.idToken?.let { jwt ->
            decodeJwtPayload(jwt)?.let { claims ->
                userId    = claims.optString("sub").takeIf { it.isNotBlank() }
                userEmail = claims.optString("email").takeIf { it.isNotBlank() }
                userName  = claims.optString("name").takeIf { it.isNotBlank() }
                val roles = claims.optJSONObject("realm_access")?.optJSONArray("roles")
                userRole  = when {
                    roles == null -> "CUSTOMER"
                    (0 until roles.length()).any { roles.getString(it) == "ADMIN" } -> "ADMIN"
                    (0 until roles.length()).any { roles.getString(it) == "BACK_OFFICE" } -> "BACK_OFFICE"
                    else -> "CUSTOMER"
                }
            }
        }

        tokenStore.store(
            accessToken  = accessToken,
            refreshToken = response.refreshToken ?: tokenStore.refreshToken,
            idToken      = response.idToken,
            expiresInSec = expiresIn,
            userId       = userId,
            userRole     = userRole,
            userEmail    = userEmail,
            userName     = userName,
        )
    }

    private fun decodeJwtPayload(jwt: String): JSONObject? = runCatching {
        val parts   = jwt.split(".")
        if (parts.size != 3) return null
        var payload = parts[1]
        // Pad to multiple of 4
        repeat((4 - payload.length % 4) % 4) { payload += "=" }
        val decoded = Base64.getUrlDecoder().decode(payload)
        JSONObject(String(decoded))
    }.getOrNull()

    // ── Convenience accessors ──────────────────────────────────────────────────

    val currentUserId:    String? get() = tokenStore.userId
    val currentUserRole:  String? get() = tokenStore.userRole
    val currentUserEmail: String? get() = tokenStore.userEmail
    val currentUserName:  String? get() = tokenStore.userName

    private fun buildUser() = AuthUser(
        id    = tokenStore.userId    ?: "",
        role  = tokenStore.userRole  ?: "CUSTOMER",
        email = tokenStore.userEmail,
        name  = tokenStore.userName,
    )
}

class AuthException(message: String) : Exception(message)
