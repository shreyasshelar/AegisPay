'use client'

import { useEffect, useRef, useState } from 'react'
import { signIn, signOut, useSession } from 'next-auth/react'
import { useRouter, useSearchParams } from 'next/navigation'
import { Loader2, ShieldCheck, AlertCircle } from 'lucide-react'
import { ROLE_LANDING } from '@/lib/role-routing'

const RATE_LIMIT_MS = 3_000 // 3 s cooldown between sign-in attempts

export default function LoginPage() {
  const { data: session, status } = useSession()
  const router      = useRouter()
  const params      = useSearchParams()
  const [loading, setLoading]   = useState(false)
  const [error,   setError]     = useState<string | null>(null)
  const lastAttempt = useRef<number>(0)

  // Redirect already-authenticated users — but NOT if the session has an error.
  // A broken session (RefreshAccessTokenError) has status='authenticated' yet
  // cannot be used; sending it to /dashboard would create a redirect loop with
  // the middleware. Let providers.tsx call signOut() to clear the cookie first.
  useEffect(() => {
    if (status === 'authenticated' && !session?.error) {
      const dest = ROLE_LANDING[session?.user?.role ?? ''] ?? '/dashboard'
      router.replace(dest)
    }
  }, [status, session?.error, session?.user?.role, router])

  // Map NextAuth error codes to friendly messages
  useEffect(() => {
    const err = params.get('error')
    if (!err) return
    const messages: Record<string, string> = {
      OAuthSignin:        'Could not start sign-in. Please try again.',
      OAuthCallback:      'Sign-in callback failed. Please try again.',
      OAuthAccountNotLinked:
        'This email is linked to a different provider.',
      SessionRequired:    'Your session has expired. Please sign in again.',
      default:            'An unexpected error occurred.',
    }
    setError((messages[err] ?? messages['default']) ?? null)
  }, [params])

  async function handleSignIn() {
    const now = Date.now()
    if (now - lastAttempt.current < RATE_LIMIT_MS) return
    lastAttempt.current = now

    setLoading(true)
    setError(null)
    try {
      // prompt=login forces Keycloak to always show its login form,
      // preventing silent SSO re-authentication after sign-out.
      await signIn('keycloak', { callbackUrl: '/' }, { prompt: 'login' })
    } catch {
      setError('Sign-in failed. Please try again.')
      setLoading(false)
    }
  }

  // Show spinner only while loading, or while redirecting a clean authenticated session.
  // A session with an error must fall through to the login form so the user can re-auth.
  if (status === 'loading' || (status === 'authenticated' && !session?.error)) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin text-primary-600" />
      </div>
    )
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-primary-50 via-white to-slate-100 p-4">
      <div className="w-full max-w-sm animate-fade-in">
        {/* Card */}
        <div className="rounded-2xl bg-white p-8 shadow-lg ring-1 ring-slate-200">
          {/* Logo */}
          <div className="mb-8 flex flex-col items-center gap-3">
            <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-primary-600 shadow-md">
              <ShieldCheck className="h-8 w-8 text-white" />
            </div>
            <div className="text-center">
              <h1 className="text-2xl font-bold tracking-tight text-slate-900">
                AegisPay
              </h1>
              <p className="mt-1 text-sm text-slate-500">
                Secure, event-driven payments
              </p>
            </div>
          </div>

          {/* Error banner */}
          {error && (
            <div className="mb-6 flex items-start gap-3 rounded-lg bg-danger-50 p-3 text-danger-700 ring-1 ring-danger-200">
              <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
              <p className="text-sm">{error}</p>
            </div>
          )}

          {/* Sign-in button */}
          <button
            onClick={handleSignIn}
            disabled={loading}
            aria-busy={loading}
            className="flex w-full items-center justify-center gap-3 rounded-xl bg-primary-600 px-4 py-3 text-sm font-semibold text-white shadow-sm transition-all hover:bg-primary-700 active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-60"
          >
            {loading ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              <ShieldCheck className="h-4 w-4" />
            )}
            {loading ? 'Redirecting…' : 'Continue with Keycloak'}
          </button>

          {/* Fine print */}
          <p className="mt-6 text-center text-xs text-slate-400">
            By signing in you agree to our{' '}
            <a href="/terms" className="underline hover:text-slate-600">
              Terms of Service
            </a>{' '}
            and{' '}
            <a href="/privacy" className="underline hover:text-slate-600">
              Privacy Policy
            </a>
            .
          </p>
        </div>

        <p className="mt-6 text-center text-xs text-slate-400">
          Protected by OAuth 2.0 + PKCE · Zero-trust architecture
        </p>
      </div>
    </div>
  )
}
