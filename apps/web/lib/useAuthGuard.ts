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
 * Returns `true` while the session is still resolving (caller should render null).
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

  return status === 'loading'
      || status === 'unauthenticated'
      || (status === 'authenticated' && !!session?.error)
}
