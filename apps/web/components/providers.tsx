'use client'

import { useCallback, useEffect, useRef } from 'react'
import { useRouter } from 'next/navigation'
import { SessionProvider, useSession } from 'next-auth/react'
import { QueryClient, QueryClientProvider, useQueryClient } from '@tanstack/react-query'
import { ReactQueryDevtools } from '@tanstack/react-query-devtools'
import { ApiProvider } from '@aegispay/api-client'
import { Toaster } from 'sonner'

// ── QueryClient singleton (per browser tab) ──────────────────────────────────

function makeQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime:            30_000,
        gcTime:               5 * 60_000,
        retry:                1,
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

  // Redirect to login when the Keycloak refresh token is exhausted.
  // The middleware also handles this on the next protected-route navigation
  // (deletes cookie, redirects to /login) as a belt-and-suspenders.
  useEffect(() => {
    if (session?.error === 'RefreshAccessTokenError') {
      router.replace('/login')
    }
  }, [session?.error, router])

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

  const baseURL = process.env.NEXT_PUBLIC_API_BASE_URL ?? ''

  return (
    <ApiProvider baseURL={baseURL} getAccessToken={getAccessToken}>
      {children}
    </ApiProvider>
  )
}

// ── Root providers wrapper ───────────────────────────────────────────────────

export function Providers({ children }: { children: React.ReactNode }) {
  const queryClient = getQueryClient()

  return (
    <SessionProvider>
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
