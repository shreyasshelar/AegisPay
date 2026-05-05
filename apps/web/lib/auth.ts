import type { NextAuthOptions, Session } from 'next-auth'
import type { JWT } from 'next-auth/jwt'

/**
 * NextAuth configuration.
 *
 * Supports Keycloak as the primary IdP.  Azure Entra and Okta providers can
 * be added here in the same pattern — NextAuth merges accounts by email.
 *
 * Env vars (see .env.local.example):
 *   KEYCLOAK_ID          — client_id registered in Keycloak
 *   KEYCLOAK_SECRET      — client_secret
 *   KEYCLOAK_ISSUER      — https://auth.example.com/realms/<realm>
 *   NEXTAUTH_URL         — http://localhost:3000
 *   NEXTAUTH_SECRET      — random 32-byte string (openssl rand -base64 32)
 */

async function refreshAccessToken(token: JWT): Promise<JWT> {
  try {
    const issuer = process.env.KEYCLOAK_ISSUER!
    const url = `${issuer}/protocol/openid-connect/token`

    const response = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        grant_type:    'refresh_token',
        client_id:     process.env.KEYCLOAK_ID!,
        client_secret: process.env.KEYCLOAK_SECRET!,
        refresh_token: token.refreshToken as string,
      }),
    })

    const refreshed = await response.json() as {
      access_token: string
      expires_in:   number
      refresh_token: string
      error?: string
    }

    if (!response.ok || refreshed.error) throw refreshed

    return {
      ...token,
      accessToken:          refreshed.access_token,
      accessTokenExpiresAt: Date.now() + refreshed.expires_in * 1000,
      refreshToken:         refreshed.refresh_token ?? token.refreshToken,
    }
  } catch {
    return { ...token, error: 'RefreshAccessTokenError' }
  }
}

export const authOptions: NextAuthOptions = {
  providers: [
    {
      id:   'keycloak',
      name: 'Keycloak',
      type: 'oauth',
      wellKnown: `${process.env.KEYCLOAK_ISSUER}/.well-known/openid-configuration`,
      authorization: { params: { scope: 'openid email profile offline_access' } },
      clientId:     process.env.KEYCLOAK_ID,
      clientSecret: process.env.KEYCLOAK_SECRET,
      idToken:      true,
      checks:       ['pkce', 'state'],
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
    },
  ],

  session: { strategy: 'jwt', maxAge: 24 * 60 * 60 }, // 24 h

  callbacks: {
    async jwt({ token, account, user }) {
      // Initial sign-in
      if (account && user) {
        return {
          ...token,
          accessToken:          account.access_token,
          accessTokenExpiresAt: account.expires_at! * 1000,
          refreshToken:         account.refresh_token,
          userId:               user.id,
          role:                 (user as { role?: string }).role ?? 'CUSTOMER',
        }
      }

      // Return token if it hasn't expired yet (5-min buffer)
      if (Date.now() < (token.accessTokenExpiresAt as number) - 5 * 60 * 1000) {
        return token
      }

      // Refresh expired token
      return refreshAccessToken(token)
    },

    async session({ session, token }): Promise<Session> {
      return {
        ...session,
        user: {
          ...session.user,
          id:    token.userId as string,
          role:  token.role as string,
        },
        accessToken: token.accessToken as string,
        error:       token.error as string | undefined,
      }
    },
  },

  pages: {
    signIn: '/login',
    error:  '/login',
  },
}

// ── Type augmentations ──────────────────────────────────────────────────────

declare module 'next-auth' {
  interface Session {
    accessToken: string
    error?: string
    user: {
      id:    string
      name?: string | null
      email?: string | null
      image?: string | null
      role:  string
    }
  }
  interface User {
    role: string
  }
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
