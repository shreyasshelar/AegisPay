import http from 'http'
import type { NextAuthOptions, Session } from 'next-auth'
import type { JWT } from 'next-auth/jwt'
import KeycloakProvider from 'next-auth/providers/keycloak'

// ── Startup guard — fail fast in production if secrets are missing ────────────
if (process.env.NODE_ENV === 'production') {
  if (!process.env.NEXTAUTH_SECRET) throw new Error('NEXTAUTH_SECRET is not set')
  if (!process.env.KEYCLOAK_ID)     throw new Error('KEYCLOAK_ID is not set')
  if (!process.env.KEYCLOAK_ISSUER) throw new Error('KEYCLOAK_ISSUER is not set')
}

// Force IPv4 for openid-client — Next.js 14 native fetch (undici) resolves
// localhost → ::1 on macOS; Keycloak only listens on 127.0.0.1 → 3.5s timeout.
const ipv4Agent = new http.Agent({ family: 4 })

async function refreshAccessToken(token: JWT): Promise<JWT> {
  try {
    const url = `${process.env.KEYCLOAK_ISSUER}/protocol/openid-connect/token`

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

    return {
      ...token,
      error:                undefined,
      accessToken:          refreshed.access_token,
      accessTokenExpiresAt: Date.now() + refreshed.expires_in * 1000,
      refreshToken:         refreshed.refresh_token ?? token.refreshToken,
    }
  } catch (err) {
    console.error('[auth] refreshAccessToken threw:', err)
    return { ...token, error: 'RefreshAccessTokenError' as const }
  }
}

export const authOptions: NextAuthOptions = {
  providers: [
    KeycloakProvider({
      clientId:     process.env.KEYCLOAK_ID!,
      clientSecret: process.env.KEYCLOAK_SECRET ?? '',
      issuer:       process.env.KEYCLOAK_ISSUER!,
      authorization: { params: { scope: 'openid email profile offline_access' } },
      httpOptions: { agent: ipv4Agent, timeout: 10000 },
      profile(profile) {
        return {
          id:    profile.sub,
          name:  profile.name ?? profile.preferred_username,
          email: profile.email,
          image: profile.picture,
          role:  (profile.realm_access?.roles ?? []).includes('ADMIN')
                   ? 'ADMIN'
                   : (profile.realm_access?.roles ?? []).includes('BACK_OFFICE')
                     ? 'BACK_OFFICE'
                     : 'CUSTOMER',
        }
      },
    }),
  ],

  session: {
    strategy:   'jwt',
    maxAge:     24 * 60 * 60,  // absolute expiry: 24 h
    updateAge:   5 * 60,       // sliding: re-issue JWT every 5 min of activity
  },

  debug: process.env.NODE_ENV === 'development',

  callbacks: {
    async jwt({ token, account, user }) {
      // ── Initial sign-in: Keycloak just issued fresh tokens ──────────────────
      if (account && user) {
        return {
          ...token,
          error:                undefined,          // clear any previous error
          accessToken:          account.access_token!,
          accessTokenExpiresAt: account.expires_at! * 1000,
          refreshToken:         account.refresh_token ?? '',
          userId:               user.id,
          role:                 (user as { role?: string }).role ?? 'CUSTOMER',
        }
      }

      // ── Propagate existing error — no retry, user must re-login ─────────────
      if (token.error === 'RefreshAccessTokenError') {
        return token
      }

      // ── Token still valid (60-second proactive buffer) ──────────────────────
      // Buffer must be much smaller than the token lifetime.
      // Keycloak default access token = 5 min → 60s buffer leaves 4 min headroom.
      if (Date.now() < token.accessTokenExpiresAt - 60 * 1000) {
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
    error?: string
    user: {
      id:     string
      name?:  string | null
      email?: string | null
      image?: string | null
      role:   string
    }
  }
  interface User { role: string }
}

declare module 'next-auth/jwt' {
  interface JWT {
    accessToken:          string
    accessTokenExpiresAt: number
    refreshToken:         string
    userId:               string
    role:                 string
    error?:               string
  }
}
