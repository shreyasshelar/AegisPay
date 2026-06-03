import { randomUUID } from 'crypto'
import type { NextAuthOptions, Session } from 'next-auth'
import type { JWT } from 'next-auth/jwt'
import KeycloakProvider from 'next-auth/providers/keycloak'

// ── Startup guard — fail fast in production if secrets are missing ────────────
if (process.env.NODE_ENV === 'production') {
  if (!process.env.NEXTAUTH_SECRET) throw new Error('NEXTAUTH_SECRET is not set')
  if (!process.env.KEYCLOAK_ID)     throw new Error('KEYCLOAK_ID is not set')
  if (!process.env.KEYCLOAK_ISSUER) throw new Error('KEYCLOAK_ISSUER is not set')
}

// ── Internal vs external Keycloak URL ────────────────────────────────────────
// KEYCLOAK_ISSUER   = public URL (https://aegispay-keycloak.shreyasshelar.uk/...)
//                     Used for: JWT `iss` validation, browser authorization redirect
// KEYCLOAK_INTERNAL_URL = cluster-internal URL (http://keycloak.aegispay-infra.svc...)
//                     Used for: server-side OIDC discovery, token exchange, userinfo
//
// Without KEYCLOAK_INTERNAL_URL the web pod would hairpin all server-side Keycloak
// calls through Cloudflare (public URL → GCE NAT → internet → Cloudflare → back to
// pod). This adds ~100ms latency per call and fails if Cloudflare is unreachable.
// With KEYCLOAK_INTERNAL_URL, server-side calls stay in-cluster; the browser still
// redirects to the public Keycloak URL for the login form.
// Use || null so empty string env var (env var set but blank) is treated as unset.
const keycloakInternalBase = process.env.KEYCLOAK_INTERNAL_URL?.replace(/\/$/, '') || null
const keycloakPublicBase   = (process.env.KEYCLOAK_ISSUER ?? '').replace(/\/$/, '')

// NOTE: httpOptions (http.Agent) is intentionally NOT passed to KeycloakProvider.
// openid-client shares the agent across ALL requests — HTTP AND HTTPS — so any
// http.Agent (protocol='http:') passed here causes ERR_INVALID_PROTOCOL on the
// HTTPS JWKS and issuer re-discovery requests.  In Kubernetes, CoreDNS always
// resolves to IPv4, so no custom agent is needed for production.

/**
 * Derive the AegisPay role from a list of Keycloak realm roles.
 * Priority: ADMIN > BACK_OFFICE > MERCHANT_OPS > PARTNER > CUSTOMER.
 * Single source of truth — used by both profile() (ID token) and the
 * jwt() callback (access token decode).
 */
export function extractRole(realmRoles: string[]): string {
  if (realmRoles.includes('ADMIN'))        return 'ADMIN'
  if (realmRoles.includes('BACK_OFFICE'))  return 'BACK_OFFICE'
  if (realmRoles.includes('MERCHANT_OPS')) return 'MERCHANT_OPS'
  if (realmRoles.includes('PARTNER'))      return 'PARTNER'
  return 'CUSTOMER'
}

async function refreshAccessToken(token: JWT): Promise<JWT> {
  try {
    // Prefer internal URL to avoid hairpin routing through Cloudflare
    const base = keycloakInternalBase ?? keycloakPublicBase
    const url = `${base}/protocol/openid-connect/token`

    // Public PKCE clients have no secret — sending client_secret="" causes
    // Keycloak to return 401 "client not found or invalid client credentials".
    const params: Record<string, string> = {
      grant_type:    'refresh_token',
      client_id:     process.env.KEYCLOAK_ID!,
      refresh_token: token.refreshToken,
    }
    if (process.env.KEYCLOAK_SECRET) {
      params.client_secret = process.env.KEYCLOAK_SECRET
    }

    const response = await fetch(url, {
      method:  'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body:    new URLSearchParams(params),
    })

    const refreshed = await response.json() as {
      access_token:       string
      expires_in:         number
      refresh_token?:     string
      error?:             string
      error_description?: string
    }

    if (!response.ok || refreshed.error) {
      console.error('[auth] Refresh failed:', refreshed.error, refreshed.error_description)
      return { ...token, error: 'RefreshAccessTokenError' as const }
    }

    // If the refreshed access token carries aegispay_user_id (written by User
    // Service async attribute write-back) adopt it so the STOMP principal
    // updates without requiring a full re-login.
    let updatedUserId: string = token.userId
    try {
      const parts = refreshed.access_token?.split('.')
      if (parts?.length === 3 && parts[1]) {
        const payload = JSON.parse(
          Buffer.from(parts[1].replace(/-/g, '+').replace(/_/g, '/'), 'base64').toString(),
        ) as Record<string, unknown>
        const newId = payload['aegispay_user_id']
        if (typeof newId === 'string' && newId) updatedUserId = newId
      }
    } catch { /* keep existing userId on decode failure */ }

    return {
      ...token,
      userId:               updatedUserId,
      error:                undefined,
      accessToken:          refreshed.access_token,
      accessTokenExpiresAt: Date.now() + refreshed.expires_in * 1000,
      refreshToken:         refreshed.refresh_token ?? token.refreshToken,
      // Keycloak re-issues an id_token on refresh when openid scope is present.
      // Carry it forward so end_session always has a valid id_token_hint.
      // Never store empty string — end_session rejects id_token_hint=''.
      idToken:              (refreshed as { id_token?: string }).id_token || token.idToken,
    }
  } catch (err) {
    console.error('[auth] refreshAccessToken threw:', err)
    return { ...token, error: 'RefreshAccessTokenError' as const }
  }
}

// On HTTP (LAN IP dev), suppress __Host- prefix cookies — browsers only accept
// __Host- on HTTPS or localhost, so they silently drop them and the state is lost.
const useSecureCookies = process.env.NEXTAUTH_URL?.startsWith('https://') ?? false
const cookiePrefix = useSecureCookies ? '__Secure-' : 'next-auth.'
const hostCookiePrefix = useSecureCookies ? '__Host-' : 'next-auth.'

export const authOptions: NextAuthOptions = {
  cookies: {
    sessionToken:      { name: `${cookiePrefix}session-token`,       options: { httpOnly: true, sameSite: 'lax', path: '/', secure: useSecureCookies } },
    callbackUrl:       { name: `${cookiePrefix}callback-url`,        options: { httpOnly: true, sameSite: 'lax', path: '/', secure: useSecureCookies } },
    csrfToken:         { name: `${hostCookiePrefix}csrf-token`,      options: { httpOnly: true, sameSite: 'lax', path: '/', secure: useSecureCookies } },
    pkceCodeVerifier:  { name: `${cookiePrefix}pkce.code_verifier`,  options: { httpOnly: true, sameSite: 'lax', path: '/', secure: useSecureCookies, maxAge: 60 * 15 } },
    state:             { name: `${cookiePrefix}state`,               options: { httpOnly: true, sameSite: 'lax', path: '/', secure: useSecureCookies, maxAge: 60 * 15 } },
    nonce:             { name: `${cookiePrefix}nonce`,               options: { httpOnly: true, sameSite: 'lax', path: '/', secure: useSecureCookies } },
  },
  providers: [
    KeycloakProvider({
      clientId:     process.env.KEYCLOAK_ID!,
      clientSecret: process.env.KEYCLOAK_SECRET ?? '',
      // issuer is always the PUBLIC URL — openid-client validates the `iss` claim
      // in returned JWTs against this value. Must match KC_HOSTNAME on Keycloak.
      issuer: process.env.KEYCLOAK_ISSUER!,
      // When KEYCLOAK_INTERNAL_URL is set, fetch the OIDC discovery document from
      // the in-cluster address. Keycloak responds with the public hostname in all
      // endpoint URLs (due to KC_HOSTNAME), so the issuer check still passes and
      // the browser authorization redirect still uses the public URL. Only the
      // wellKnown fetch itself goes internal, avoiding Cloudflare hairpin on startup.
      ...(keycloakInternalBase ? {
        wellKnown: `${keycloakInternalBase}/.well-known/openid-configuration`,
        // Override token + userinfo endpoints to in-cluster URL so every token
        // exchange and refresh stays within the cluster network.
        token:    `${keycloakInternalBase}/protocol/openid-connect/token`,
        userinfo: `${keycloakInternalBase}/protocol/openid-connect/userinfo`,
      } : {}),
      authorization: { params: { scope: 'openid email profile offline_access' } },
      profile(profile) {
        const p = profile as Record<string, unknown>
        const aegisUserId = p.aegispay_user_id as string | undefined
        return {
          // Use the AegisPay domain UUID if present; fall back to Keycloak sub.
          // Social-login users won't have aegispay_user_id until the User Service
          // provisions them — the jwt callback handles that registration step.
          id:                aegisUserId ?? profile.sub,
          name:              profile.name ?? profile.preferred_username,
          email:             profile.email,
          image:             profile.picture,
          role:              extractRole(profile.realm_access?.roles ?? []),
          // Extra fields forwarded to jwt callback for auto-registration
          rawSub:            profile.sub,
          firstName:         (p.given_name as string | undefined) ?? profile.preferred_username ?? '',
          lastName:          (p.family_name as string | undefined) ?? '',
          needsRegistration: !aegisUserId,
        }
      },
    }),
  ],

  session: {
    strategy:   'jwt',
    maxAge:     24 * 60 * 60,  // absolute expiry: 24 h
    // Always rewrite the JWT cookie on every session check so a freshly-rotated
    // Keycloak refresh token (refreshTokenMaxReuse=0) is immediately persisted.
    // Without this, the new RT can miss the updateAge window and the old RT gets
    // reused on the next poll → Keycloak rejects it → RefreshAccessTokenError.
    updateAge:  0,
  },

  debug: process.env.NODE_ENV === 'development',

  callbacks: {
    async jwt({ token, account, user }) {
      // ── Initial sign-in: Keycloak just issued fresh tokens ──────────────────
      if (account && user) {
        const u = user as { role?: string; needsRegistration?: boolean; firstName?: string; lastName?: string }
        let userId = user.id

        // Always call /register on every initial sign-in — the endpoint is fully idempotent
        // on externalId (Keycloak sub).  For established users it is a cheap no-op that returns
        // the existing record; for new or re-provisioned users it creates the record.
        //
        // Calling unconditionally (rather than only when needsRegistration=true) covers the
        // "stale attribute" edge case: if the DB was wiped after the user's last login, the
        // Keycloak user-attribute aegispay_user_id still points to the old (now-missing) UUID.
        // Without this call the session would carry a UUID that resolves to 404 on every API
        // call.  By always registering we get the correct (possibly new) UUID in the session.
        //
        // Cases handled:
        //   (a) First-time / social login — no aegispay_user_id in JWT yet.
        //   (b) Keycloak-native users created via admin console.
        //   (c) Established users (normal re-login) — idempotent, returns existing record.
        //   (d) Stale-attribute users (post-DB-wipe) — re-creates the record.
        if (!user.email) {
          // Guard: some OAuth providers don't return an email (e.g. GitHub with private email).
          // Without an email we cannot register — fail gracefully rather than sending null.
          console.error(
            '[auth] Cannot auto-register user: no email in IdP profile. ' +
            'sub=%s provider=%s — user will see USER_NOT_FOUND until email is set in IdP.',
            user.rawSub ?? user.id,
            account.provider,
          )
          // Fall through — userId stays as Keycloak sub (or the stale aegispay_user_id set by
          // profile()).  All API calls will fail with 404 until email is configured in the IdP.
        } else {
          try {
            const apiBase    = process.env.API_BASE_URL ?? 'http://localhost:8080'
            const nameParts  = (user.name ?? '').split(' ')
            const firstName  = u.firstName || nameParts[0] || 'User'
            const lastName   = u.lastName  || nameParts.slice(1).join(' ') || 'Account'

            const resp = await fetch(`${apiBase}/api/v1/users/register`, {
              method: 'POST',
              headers: {
                'Content-Type':      'application/json',
                'Authorization':     `Bearer ${account.access_token}`,
                'X-Idempotency-Key': randomUUID(),
              },
              body: JSON.stringify({
                email:     user.email,
                firstName,
                lastName,
                tenantId:  'default',
              }),
            })

            if (resp.ok) {
              const body = await resp.json() as { data?: { id?: string } }
              if (body.data?.id) {
                userId = body.data.id
                if (u.needsRegistration) {
                  console.log('[auth] User provisioned — aegispay_user_id=%s sub=%s',
                    userId, user.rawSub ?? user.id)

                  // ── Propagate aegispay_user_id into the access token ────────────
                  // writeUserAttributes is @Async in User Service — it writes the
                  // aegispay_user_id attribute to Keycloak after /register returns.
                  // The STOMP channel interceptor uses this claim to set the principal
                  // for convertAndSendToUser routing.  Without refreshing, the current
                  // session token lacks the claim → STOMP principal falls back to the
                  // Keycloak sub → WebSocket notifications are silently dropped.
                  // Wait 1.2 s (covers the @Async Keycloak admin write latency), then
                  // refresh so the new token carries aegispay_user_id.
                  try {
                    await new Promise<void>(r => setTimeout(r, 1200))
                    // Prefer internal URL to avoid hairpin routing
                    const kcBase     = keycloakInternalBase ?? keycloakPublicBase
                    const kcTokenUrl = `${kcBase}/protocol/openid-connect/token`
                    const refreshParams: Record<string, string> = {
                      grant_type:    'refresh_token',
                      client_id:     process.env.KEYCLOAK_ID!,
                      refresh_token: account.refresh_token ?? '',
                    }
                    if (process.env.KEYCLOAK_SECRET) {
                      refreshParams.client_secret = process.env.KEYCLOAK_SECRET
                    }
                    const kcResp = await fetch(kcTokenUrl, {
                      method:  'POST',
                      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                      body:    new URLSearchParams(refreshParams),
                    })
                    if (kcResp.ok) {
                      const refreshed = await kcResp.json() as {
                        access_token: string; expires_in: number; refresh_token?: string
                      }
                      account.access_token = refreshed.access_token
                      account.expires_at   = Math.floor(Date.now() / 1000) + refreshed.expires_in
                      if (refreshed.refresh_token) account.refresh_token = refreshed.refresh_token
                      console.log('[auth] Token refreshed after provisioning — aegispay_user_id claim now in JWT')
                    } else {
                      console.warn('[auth] Post-provisioning token refresh failed: HTTP', kcResp.status)
                    }
                  } catch (refreshErr) {
                    console.warn('[auth] Post-provisioning token refresh threw:', refreshErr)
                  }
                  // ───────────────────────────────────────────────────────────────
                } else if (userId !== user.id) {
                  // Stale-attribute case: the DB returned a different UUID than what Keycloak
                  // had stored.  Use the authoritative DB value for this session.
                  console.warn('[auth] Stale aegispay_user_id corrected — old=%s new=%s sub=%s',
                    user.id, userId, user.rawSub ?? user.id)
                }
              } else {
                // Should never happen: 2xx but no id in envelope.
                console.error('[auth] Registration 2xx but response contained no user id. ' +
                  'body=%j sub=%s', body, user.rawSub ?? user.id)
                // userId stays as whatever profile() set — /me fallback in UserController will
                // recover via getByExternalId if the DB record exists.
              }
            } else if (resp.status === 409) {
              // 409 can mean:
              //   (a) EMAIL_ALREADY_EXISTS — a different Keycloak sub already owns this email.
              //       This is a genuine account conflict (e.g. email+password first, then social
              //       login with the same address).  We CANNOT merge accounts automatically —
              //       that could grant access to someone else's funds.  The user must sign in
              //       with their original method to access their account.
              //   (b) DATA_CONFLICT — concurrent first-time logins for the same sub raced past
              //       the findByExternalId check.  The DB constraint fired.  The /register
              //       controller retries and returns the existing user (so this path is rare),
              //       but if /register itself returns 409 we recover here by calling /me.
              //   (c) Idempotency key collision (extremely unlikely with randomUUID).
              const errBody = await resp.json().catch(() => ({})) as { error?: { code?: string } }
              if (errBody.error?.code === 'EMAIL_ALREADY_EXISTS') {
                console.warn(
                  '[auth] EMAIL_ALREADY_EXISTS during registration for sub=%s email=%s — ' +
                  'an existing account owns this email.  Profile will show a "not found" error; ' +
                  'the user must sign in with their original method (email+password) to access ' +
                  'their account.  userId stays as Keycloak sub for this session.',
                  user.rawSub ?? user.id,
                  user.email,
                )
                // userId stays as Keycloak sub — profile/ledger calls will return 404, which
                // is intentional: this session has no valid AegisPay account to display.
              } else if (errBody.error?.code === 'DATA_CONFLICT') {
                // Concurrent registration: another request for this sub won the race.
                // Recover by reading the existing user from /me (external_id lookup).
                console.warn(
                  '[auth] DATA_CONFLICT on registration for sub=%s — fetching existing user via /me',
                  user.rawSub ?? user.id,
                )
                try {
                  const meResp = await fetch(`${apiBase}/api/v1/users/me`, {
                    headers: { 'Authorization': `Bearer ${account.access_token}` },
                  })
                  if (meResp.ok) {
                    const meBody = await meResp.json() as { data?: { id?: string } }
                    if (meBody.data?.id) {
                      userId = meBody.data.id
                      console.log('[auth] DATA_CONFLICT recovered via /me: userId=%s sub=%s',
                        userId, user.rawSub ?? user.id)
                    }
                  }
                } catch (meErr) {
                  console.error('[auth] DATA_CONFLICT /me recovery failed: %o sub=%s',
                    meErr, user.rawSub ?? user.id)
                  // userId stays as user.id (sub for new users → getMe bootstrap fallback works)
                }
              } else {
                console.error('[auth] Registration 409 (unexpected code): %j sub=%s',
                  errBody, user.rawSub ?? user.id)
              }
            } else {
              const errText = await resp.text().catch(() => '(unreadable)')
              console.error('[auth] Registration failed: status=%d body=%s sub=%s',
                resp.status, errText, user.rawSub ?? user.id)
              // userId stays as whatever profile() set.
              // /me will work via getByExternalId if the user exists in DB by externalId.
              // If not, the user will see errors — logging out and back in retries registration.
            }
          } catch (err) {
            console.error('[auth] Registration fetch threw (network/timeout): %o sub=%s',
              err, user.rawSub ?? user.id)
            // userId stays as whatever profile() set — transient error, retry on next login.
          }
        }

        // Access token carries the authoritative realm_access.roles regardless
        // of whether the Keycloak ID-token mapper is configured.  Decode it to
        // get the role; fall back to profile()-derived role on any failure
        // (opaque token, malformed JWT, missing payload segment).
        let role: string = u.role ?? 'CUSTOMER'
        try {
          const parts = account.access_token?.split('.')
          if (parts && parts.length === 3 && parts[1]) {
            const b64 = parts[1].replace(/-/g, '+').replace(/_/g, '/')
            const atPayload = JSON.parse(Buffer.from(b64, 'base64').toString())
            role = extractRole(atPayload?.realm_access?.roles ?? [])
          }
        } catch { /* keep profile-derived role on decode failure */ }

        return {
          ...token,
          error:                undefined,          // clear any previous error
          accessToken:          account.access_token!,
          accessTokenExpiresAt: account.expires_at! * 1000,
          refreshToken:         account.refresh_token ?? '',
          // Stored for Keycloak end_session logout — lets us terminate the
          // Keycloak SSO session so sign-out actually requires re-authentication.
          // Only store idToken when Keycloak actually issued one.
          // An empty string would be sent as id_token_hint='' to end_session
          // which Keycloak rejects (400), silently leaving the SSO session alive.
          ...(account.id_token ? { idToken: account.id_token } : {}),
          userId,
          role,
        }
      }

      // ── Propagate existing error — no retry, user must re-login ─────────────
      if (token.error === 'RefreshAccessTokenError') {
        return token
      }

      // ── Token still valid — proactive refresh buffer ─────────────────────────
      // Buffer must be larger than the polling interval (SessionProvider
      // refetchInterval = 4 min = 240 s) so the refresh triggers at the poll
      // *before* expiry, not at/after it.  5 min (300 s) > 240 s. ✓
      if (Date.now() < token.accessTokenExpiresAt - 5 * 60 * 1000) {
        return token
      }

      // ── No refresh token — can't refresh, force re-login ────────────────────
      if (!token.refreshToken) {
        return { ...token, error: 'RefreshAccessTokenError' as const }
      }

      // ── Token expiring — attempt proactive refresh ───────────────────────────
      return refreshAccessToken(token)
    },

    async session({ session, token }): Promise<Session> {
      // idToken is intentionally NOT included here: it lives only in the
      // encrypted server-side JWT cookie and is used by refreshAccessToken
      // for Keycloak end_session. Exposing it in the browser-visible session
      // response would leak Keycloak user claims to any client-side code.
      return {
        ...session,
        user: {
          ...session.user,
          id:   token.userId,
          role: token.role,
        },
        accessToken: token.accessToken,
        // Only propagate the error field when it is actually set
        ...(token.error ? { error: token.error } : {}),
      }
    },
  },

  pages: {
    signIn: '/login',
    error:  '/login',
  },
}

// ── Type augmentations ────────────────────────────────────────────────────────

declare module 'next-auth' {
  interface Session {
    accessToken: string
    // idToken is server-side only (JWT cookie) — not included in the session
    // response to avoid exposing raw Keycloak claims to browser-side code.
    error?: string
    user: {
      id:     string
      name?:  string | null
      email?: string | null
      image?: string | null
      role:   string
    }
  }
  interface User {
    role: string
    rawSub?: string
    firstName?: string
    lastName?: string
    needsRegistration?: boolean
  }
}

declare module 'next-auth/jwt' {
  interface JWT {
    accessToken:          string
    accessTokenExpiresAt: number
    refreshToken:         string
    idToken?:             string   // absent when Keycloak didn't issue an id_token
    userId:               string
    role:                 string
    error?:               string
  }
}
