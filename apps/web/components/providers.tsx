'use client'

import { useCallback, useEffect, useRef } from 'react'
import { useRouter } from 'next/navigation'
import { SessionProvider, signOut, useSession } from 'next-auth/react'
import { QueryClient, QueryClientProvider, useQuery, useQueryClient } from '@tanstack/react-query'
import { ReactQueryDevtools } from '@tanstack/react-query-devtools'
import { ApiProvider, ApiError, useApiClient, userKeys } from '@aegispay/api-client'
import { toast, Toaster } from 'sonner'

// ── QueryClient singleton (per browser tab) ──────────────────────────────────

function makeQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime:            30_000,
        gcTime:               5 * 60_000,
        // Never retry 401s (trigger sign-out) or USER_NOT_FOUND 404s (trigger sign-out).
        // Retry once for transient 5xx / network errors.
        retry: (failureCount, error) => {
          if (error instanceof ApiError && error.status === 401) return false
          if (error instanceof ApiError && error.status === 404 && error.errorCode === 'USER_NOT_FOUND') return false
          return failureCount < 1
        },
        refetchOnWindowFocus: false,
      },
      mutations: { retry: 0 },
    },
  })
}

let browserQueryClient: QueryClient | undefined
function getQueryClient() {
  if (typeof window === 'undefined') return makeQueryClient()
  if (!browserQueryClient) browserQueryClient = makeQueryClient()
  return browserQueryClient
}

// ── Session gate — a promise that resolves once the session is determined ─────
// Initialized lazily so it persists across re-renders but resets on true
// component unmount.  Prevents React Query from firing requests with no token
// during the ~100 ms window while useSession() loads from /api/auth/session.

type Gate = { settled: boolean; resolve: (() => void); promise: Promise<void> }

function makeGate(alreadySettled: boolean): Gate {
  if (alreadySettled) {
    return { settled: true, resolve: () => {}, promise: Promise.resolve() }
  }
  let resolve!: () => void
  const promise = new Promise<void>(r => { resolve = r })
  return { settled: false, resolve, promise }
}

// ── User session bridge — must be inside ApiProvider ─────────────────────────
// Silently calls GET /me once per authenticated session (React Query caches the
// result for 5 min, so this costs at most one request per cache window across the
// entire tab, regardless of how many pages the user visits).
//
// Purpose: detect USER_NOT_FOUND on any page, not just /profile.  Without this,
// a stale-session user who lands on /dashboard never triggers onUserNotFound and
// sits with broken/empty data until they navigate to Profile.
//
// If the query fails with USER_NOT_FOUND the Axios interceptor calls onUserNotFound()
// → toast + triggerSignOut() → /login → fresh login → auth.ts /register →
// DB record re-provisioned → all subsequent calls succeed.
function UserSessionBridge() {
  const { users }  = useApiClient()
  const { status } = useSession()

  useQuery({
    queryKey: userKeys.me(),
    queryFn:  () => users.getMe(),
    enabled:  status === 'authenticated',
    staleTime: 5 * 60_000,
    // No retry — USER_NOT_FOUND is deterministic; the interceptor already handles recovery.
    // Other errors (503, network) are surfaced to the page that owns the relevant UI.
    retry: false,
  })

  return null
}

// ── Inner bridge — must be inside QueryClientProvider ────────────────────────

function ApiProviderWithSession({ children }: { children: React.ReactNode }) {
  const { data: session, status } = useSession()
  const queryClient = useQueryClient()
  const router      = useRouter()

  // Current session in a ref so the stable Axios callback always has the
  // latest value without causing the Axios client to be recreated.
  const sessionRef = useRef(session)
  useEffect(() => { sessionRef.current = session }, [session])

  // Session gate: blocks getAccessToken until status leaves 'loading'.
  // If status is already non-loading on the first render (e.g. SSR injected
  // the session), the gate is immediately settled.
  const gate = useRef<Gate | null>(null)
  if (!gate.current) {
    gate.current = makeGate(status !== 'loading')
  }

  useEffect(() => {
    if (status !== 'loading' && gate.current && !gate.current.settled) {
      gate.current.settled = true
      gate.current.resolve()
    }
  }, [status])

  // ── Single shared sign-out path ──────────────────────────────────────────────
  // Both RefreshAccessTokenError and HTTP 401 funnel here so they can never
  // race and call signOut() twice (which produces double redirects / loops).
  const signingOut = useRef(false)
  const triggerSignOut = useCallback(() => {
    if (signingOut.current) return
    signingOut.current = true
    queryClient.clear()
    // signOut() calls /api/auth/signout, clears the session cookie, then
    // redirects to /login. Using router.replace() instead would skip cookie
    // deletion → login page sees status='authenticated' → redirect loop.
    void signOut({ callbackUrl: '/login' })
  }, [queryClient])

  // Watch for expired Keycloak refresh token (set by auth.ts jwt callback).
  // Middleware handles server-side navigation; this covers the client-side case
  // where the user is already on a page when the token expires.
  useEffect(() => {
    if (session?.error === 'RefreshAccessTokenError') {
      triggerSignOut()
    }
  }, [session?.error, triggerSignOut])

  // getAccessToken: waits for the session gate before returning the token so
  // Axios never sends a request without an Authorization header during loading.
  // Max wait: 5 s (safety net for network hiccups on /api/auth/session).
  const getAccessToken = useCallback(async (): Promise<string | null> => {
    const g = gate.current!
    if (!g.settled) {
      await Promise.race([
        g.promise,
        new Promise<void>(r => setTimeout(r, 5_000)),
      ])
    }
    const s = sessionRef.current
    if (!s || s.error || !s.accessToken) return null
    return s.accessToken
  }, []) // stable — reads from refs at call time

  // onUnauthorized: fired by Axios interceptor on any HTTP 401.
  // Routes through triggerSignOut so the debounce is shared with the
  // RefreshAccessTokenError handler above.
  const onUnauthorized = useCallback(() => {
    triggerSignOut()
  }, [triggerSignOut])

  // onUserNotFound: fired by Axios interceptor when GET /api/v1/users/me returns
  // HTTP 404 + errorCode "USER_NOT_FOUND".  This means the session JWT is valid in
  // Keycloak but the AegisPay DB record is absent — common after a dev DB wipe, or
  // when the user's session predates the always-register auth.ts deployment.
  //
  // Recovery path:
  //   sign-out → /login → fresh Keycloak login → auth.ts jwt() account&&user block
  //   → POST /register (idempotent) → DB record created → session.userId updated
  //   → all subsequent API calls succeed.
  //
  // The toast fires before queryClient.clear() so it has time to display; signOut()
  // is async (calls /api/auth/signout then redirects) so the user sees the message
  // for ~1 second before being redirected to /login.
  const onUserNotFound = useCallback(() => {
    toast.warning('Session refresh required', {
      description: 'Your account needs to be re-provisioned. Signing you out…',
      duration: 3_000,
    })
    triggerSignOut()
  }, [triggerSignOut])

  const baseURL = process.env.NEXT_PUBLIC_API_BASE_URL ?? ''

  return (
    <ApiProvider
      baseURL={baseURL}
      getAccessToken={getAccessToken}
      onUnauthorized={onUnauthorized}
      onUserNotFound={onUserNotFound}
    >
      {/* Silent bootstrap check — detects USER_NOT_FOUND on every authenticated page,
          not only when the user navigates to /profile. Must live inside ApiProvider so
          it can call useApiClient().  Shares the ['user','me'] React Query cache entry
          with useMe() in profile-client.tsx — no duplicate network requests. */}
      <UserSessionBridge />
      {children}
    </ApiProvider>
  )
}

// ── Root providers wrapper ───────────────────────────────────────────────────

export function Providers({ children }: { children: React.ReactNode }) {
  const queryClient = getQueryClient()

  return (
    // refetchInterval: poll /api/auth/session every 4 min so the jwt callback
    // proactively refreshes the Keycloak access token (5 min lifetime, 60 s
    // buffer) before it expires.  Without this, a user idle on a page >5 min
    // gets a 401 on their next API call → onUnauthorized → mid-work sign-out.
    // refetchOnWindowFocus: re-check immediately when the user returns to the tab.
    <SessionProvider refetchInterval={4 * 60} refetchOnWindowFocus>
      <QueryClientProvider client={queryClient}>
        <ApiProviderWithSession>
          {children}

          <Toaster
            position="top-right"
            richColors
            closeButton
            toastOptions={{
              duration: 5_000,
              classNames: {
                toast:       'font-sans text-sm',
                title:       'font-semibold',
                description: 'text-muted-foreground',
              },
            }}
          />

          {process.env.NODE_ENV === 'development' && (
            <ReactQueryDevtools initialIsOpen={false} />
          )}
        </ApiProviderWithSession>
      </QueryClientProvider>
    </SessionProvider>
  )
}
