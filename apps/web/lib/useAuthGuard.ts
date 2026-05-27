'use client'

import { useEffect } from 'react'
import { useSession } from 'next-auth/react'
import { useRouter } from 'next/navigation'

/**
 * Client-side auth guard.
 *
 * Two layers already exist above this:
 *   1. Middleware   — redirects unauthenticated server requests + clears stale
 *                     RefreshAccessTokenError cookies.
 *   2. DashboardLayout — server-side getServerSession check.
 *
 * This hook is the third layer and catches the gap where a user is already on
 * a page when their session expires (no navigation event to trigger middleware),
 * or when a hard-refresh races against a stale session cookie.
 *
 * Returns `true` only when the session is definitively invalid (unauthenticated
 * or broken refresh token). Does NOT block on `'loading'` — DashboardLayout has
 * already verified the session server-side via getServerSession, so blocking on
 * the client's re-hydration phase causes an unnecessary blank-page flash on every
 * hard-refresh and SSO redirect. The useEffect below will still redirect as soon
 * as the revalidation resolves to unauthenticated.
 */
export function useAuthGuard(): boolean {
  const { status, data: session } = useSession()
  const router = useRouter()

  useEffect(() => {
    // Unauthenticated: no session at all
    if (status === 'unauthenticated') {
      router.replace('/login')
      return
    }
    // Authenticated but with a broken refresh token that providers.tsx hasn't
    // cleared yet — redirect immediately rather than waiting for providers.tsx
    if (status === 'authenticated' && session?.error) {
      router.replace('/login')
    }
  }, [status, session?.error, router])

  // 'loading' is intentionally excluded: server layout already confirmed auth.
  // Blocking here produces a blank page flash on every SSO redirect / hard-refresh.
  return status === 'unauthenticated'
      || (status === 'authenticated' && !!session?.error)
}
