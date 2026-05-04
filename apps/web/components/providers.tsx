'use client'

import { useEffect, useRef } from 'react'
import { SessionProvider, useSession } from 'next-auth/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ReactQueryDevtools } from '@tanstack/react-query-devtools'
import { ApiProvider } from '@aegispay/api-client'
import { Toaster } from 'sonner'

// ── QueryClient singleton (per browser tab) ──────────────────────────────────

function makeQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime:            30_000,    // 30 s
        gcTime:               5 * 60_000, // 5 min
        retry:                2,
        refetchOnWindowFocus: false,
      },
      mutations: {
        retry: 0,
      },
    },
  })
}

let browserQueryClient: QueryClient | undefined

function getQueryClient() {
  if (typeof window === 'undefined') {
    // Server: always create a new client to avoid sharing between requests
    return makeQueryClient()
  }
  if (!browserQueryClient) browserQueryClient = makeQueryClient()
  return browserQueryClient
}

// ── ApiProvider bridge — injects session token ──────────────────────────────

function ApiProviderWithSession({ children }: { children: React.ReactNode }) {
  const { data: session } = useSession()
  const baseURL = process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080'

  return (
    <ApiProvider
      baseURL={baseURL}
      getAccessToken={async () => session?.accessToken ?? null}
      onUnauthorized={() => {
        // Let NextAuth handle the re-login redirect
        window.location.href = '/login'
      }}
    >
      {children}
    </ApiProvider>
  )
}

// ── Root providers wrapper ───────────────────────────────────────────────────

interface ProvidersProps {
  children: React.ReactNode
}

export function Providers({ children }: ProvidersProps) {
  const queryClient = getQueryClient()

  return (
    <SessionProvider>
      <QueryClientProvider client={queryClient}>
        <ApiProviderWithSession>
          {children}

          {/* Toast notifications */}
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

          {/* React Query devtools — only in development */}
          {process.env.NODE_ENV === 'development' && (
            <ReactQueryDevtools initialIsOpen={false} />
          )}
        </ApiProviderWithSession>
      </QueryClientProvider>
    </SessionProvider>
  )
}
