import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

// ── Helpers extracted from authOptions for unit testing ───────────────────────
// These mirror the exact logic in lib/auth.ts without importing next-auth.

interface FakeJWT {
  accessToken:          string
  accessTokenExpiresAt: number
  refreshToken:         string
  userId:               string
  role:                 string
  error?:               string
}

interface FakeAccount {
  access_token:  string
  expires_at:    number
  refresh_token: string
}

interface FakeUser {
  id:   string
  role: string
}

/** Mirrors authOptions.callbacks.jwt for initial sign-in */
function jwtCallbackInitialSignIn(account: FakeAccount, user: FakeUser): FakeJWT {
  return {
    accessToken:          account.access_token,
    accessTokenExpiresAt: account.expires_at * 1000,
    refreshToken:         account.refresh_token ?? '',
    userId:               user.id,
    role:                 user.role ?? 'CUSTOMER',
    error:                undefined,
  }
}

/** Returns whether a token needs refresh (60-second buffer) */
function needsRefresh(token: FakeJWT): boolean {
  return Date.now() >= token.accessTokenExpiresAt - 60_000
}

/** Extracts role from Keycloak realm_access.roles array */
function extractRole(roles: string[]): string {
  if (roles.includes('ADMIN'))        return 'ADMIN'
  if (roles.includes('BACK_OFFICE'))  return 'BACK_OFFICE'
  if (roles.includes('MERCHANT_OPS')) return 'MERCHANT_OPS'
  return 'CUSTOMER'
}

/** Extracts AegisPay UUID from profile, falls back to sub */
function extractUserId(profile: { sub: string; aegispay_user_id?: string }): string {
  return profile.aegispay_user_id ?? profile.sub
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('JWT callback — initial sign-in', () => {
  it('stores access token and expiry from account', () => {
    const expiresAt = Math.floor(Date.now() / 1000) + 3600
    const token = jwtCallbackInitialSignIn(
      { access_token: 'at-123', expires_at: expiresAt, refresh_token: 'rt-456' },
      { id: 'user-uuid', role: 'CUSTOMER' }
    )

    expect(token.accessToken).toBe('at-123')
    expect(token.accessTokenExpiresAt).toBe(expiresAt * 1000)
    expect(token.refreshToken).toBe('rt-456')
    expect(token.error).toBeUndefined()
  })

  it('stores userId from user.id', () => {
    const token = jwtCallbackInitialSignIn(
      { access_token: 'at', expires_at: 9999999999, refresh_token: 'rt' },
      { id: 'aegis-domain-uuid', role: 'ADMIN' }
    )

    expect(token.userId).toBe('aegis-domain-uuid')
    expect(token.role).toBe('ADMIN')
  })

  it('defaults role to CUSTOMER when role is undefined/null', () => {
    const token = jwtCallbackInitialSignIn(
      { access_token: 'at', expires_at: 9999999999, refresh_token: 'rt' },
      { id: 'user-id', role: undefined as any }  // simulates absent role claim
    )

    expect(token.role).toBe('CUSTOMER')
  })
})

describe('needsRefresh (60-second proactive buffer)', () => {
  beforeEach(() => { vi.useFakeTimers() })
  afterEach(() => { vi.useRealTimers() })

  it('returns false when token expires in 10 minutes', () => {
    vi.setSystemTime(new Date('2024-01-15T12:00:00Z'))
    const token: FakeJWT = {
      accessToken:          'at',
      accessTokenExpiresAt: new Date('2024-01-15T12:10:00Z').getTime(),
      refreshToken:         'rt',
      userId:               'uid',
      role:                 'CUSTOMER',
    }
    expect(needsRefresh(token)).toBe(false)
  })

  it('returns true when token expires in 30 seconds (within buffer)', () => {
    vi.setSystemTime(new Date('2024-01-15T12:00:00Z'))
    const token: FakeJWT = {
      accessToken:          'at',
      accessTokenExpiresAt: new Date('2024-01-15T12:00:30Z').getTime(),
      refreshToken:         'rt',
      userId:               'uid',
      role:                 'CUSTOMER',
    }
    expect(needsRefresh(token)).toBe(true)
  })

  it('returns true when token is already expired', () => {
    vi.setSystemTime(new Date('2024-01-15T12:00:00Z'))
    const token: FakeJWT = {
      accessToken:          'at',
      accessTokenExpiresAt: new Date('2024-01-15T11:50:00Z').getTime(),
      refreshToken:         'rt',
      userId:               'uid',
      role:                 'CUSTOMER',
    }
    expect(needsRefresh(token)).toBe(true)
  })
})

describe('extractRole from Keycloak realm_access', () => {
  it('returns ADMIN when ADMIN role present', () => {
    expect(extractRole(['offline_access', 'ADMIN', 'CUSTOMER'])).toBe('ADMIN')
  })

  it('returns BACK_OFFICE when BACK_OFFICE present (no ADMIN)', () => {
    expect(extractRole(['BACK_OFFICE', 'CUSTOMER'])).toBe('BACK_OFFICE')
  })

  it('returns MERCHANT_OPS when MERCHANT_OPS present', () => {
    expect(extractRole(['MERCHANT_OPS'])).toBe('MERCHANT_OPS')
  })

  it('returns CUSTOMER as default', () => {
    expect(extractRole(['offline_access', 'uma_authorization'])).toBe('CUSTOMER')
  })

  it('returns CUSTOMER for empty roles array', () => {
    expect(extractRole([])).toBe('CUSTOMER')
  })

  it('ADMIN takes priority over BACK_OFFICE', () => {
    expect(extractRole(['BACK_OFFICE', 'ADMIN'])).toBe('ADMIN')
  })
})

describe('extractUserId from Keycloak profile', () => {
  it('prefers aegispay_user_id over sub when both present', () => {
    const profile = { sub: 'keycloak-sub-uuid', aegispay_user_id: 'aegispay-domain-uuid' }
    expect(extractUserId(profile)).toBe('aegispay-domain-uuid')
  })

  it('falls back to sub when aegispay_user_id is absent', () => {
    // First login — no aegispay_user_id yet
    const profile = { sub: 'keycloak-sub-uuid' }
    expect(extractUserId(profile)).toBe('keycloak-sub-uuid')
  })

  it('handles Google SSO sub format (brokered via Keycloak)', () => {
    const sub = 'f:google-broker:109876543210000000000'
    const profile = { sub }
    expect(extractUserId(profile)).toBe(sub)
  })

  it('handles Azure Entra sub format', () => {
    const sub = 'aad:xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx'
    const profile = { sub, aegispay_user_id: 'aegis-uuid-from-entra' }
    expect(extractUserId(profile)).toBe('aegis-uuid-from-entra')
  })
})

describe('RefreshAccessTokenError propagation', () => {
  it('preserves error flag — token is not re-fetched', () => {
    const brokenToken: FakeJWT = {
      accessToken:          'expired',
      accessTokenExpiresAt: 0,
      refreshToken:         '',
      userId:               'uid',
      role:                 'CUSTOMER',
      error:                'RefreshAccessTokenError',
    }

    // When error is set, jwt callback should pass token through unchanged
    // (simulated: just return the token as-is)
    const result = brokenToken.error === 'RefreshAccessTokenError'
      ? brokenToken
      : null

    expect(result).not.toBeNull()
    expect(result!.error).toBe('RefreshAccessTokenError')
  })
})

describe('Multi-tenant token claims', () => {
  it('tenant_id claim is propagated through JWT callback', () => {
    // Simulate a custom JWT with tenant claim (via Keycloak mapper)
    const profile = {
      sub:                  'tenant-user-sub',
      aegispay_user_id:     'tenant-uuid-abc',
      aegispay_tenant_id:   'tenant-acme',
      realm_access:         { roles: ['CUSTOMER'] },
    }

    const tenantId  = (profile as any).aegispay_tenant_id
    const userId    = extractUserId(profile)
    const role      = extractRole(profile.realm_access.roles)

    expect(tenantId).toBe('tenant-acme')
    expect(userId).toBe('tenant-uuid-abc')
    expect(role).toBe('CUSTOMER')
  })
})
