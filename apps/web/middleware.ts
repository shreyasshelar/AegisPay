import { withAuth } from 'next-auth/middleware'
import { NextResponse } from 'next/server'
import { ROLE_LANDING } from '@/lib/role-routing'

export default withAuth(
  function middleware(req) {
    const token    = req.nextauth.token
    const pathname = req.nextUrl.pathname

    // If the refresh token is broken, delete the session cookie so the user
    // arrives at /login with a clean slate (no stale error cookie that would
    // trigger providers.tsx to call signOut and race with their login click).
    if (token?.error === 'RefreshAccessTokenError') {
      const url = req.nextUrl.clone()
      url.pathname = '/login'
      const res = NextResponse.redirect(url)
      // next-auth splits cookies > 4096 bytes into chunks (.0, .1, …).
      // Delete both the base name and all chunk variants so the stale session
      // is fully cleared and the user isn't looped back by a surviving chunk.
      const bases = ['next-auth.session-token', '__Secure-next-auth.session-token']
      for (const base of bases) {
        res.cookies.delete(base)
        for (let i = 0; i < 5; i++) res.cookies.delete(`${base}.${i}`)
      }
      return res
    }

    // Redirect staff roles away from ALL customer pages (direct nav / stale bookmarks).
    const CUSTOMER_PATHS = ['/dashboard', '/send', '/wallet', '/transactions', '/profile', '/notifications']
    const isCustomerPage = CUSTOMER_PATHS.some(p => pathname === p || pathname.startsWith(p + '/'))
    if (isCustomerPage && token?.role) {
      const dest = ROLE_LANDING[token.role]
      if (dest) return NextResponse.redirect(new URL(dest, req.url))
    }

    return NextResponse.next()
  },
  {
    callbacks: {
      authorized: ({ token }) => !!token,
    },
  },
)

export const config = {
  matcher: [
    // Protected customer pages
    '/dashboard/:path*',
    '/transactions/:path*',
    '/send/:path*',
    '/profile/:path*',
    '/notifications/:path*',
    '/wallet/:path*',
    // Protected back-office pages (triage + users were missing — added here)
    '/triage/:path*',
    '/users/:path*',
    '/incidents/:path*',
    '/ledger/:path*',
    '/risk/:path*',
    '/back-office/:path*',
    // Protected API routes — exclude:
    //   /api/auth/*          NextAuth own endpoints
    //   /api/v1/ai/kyc/*     Binary multipart upload — Edge Runtime middleware
    //                        can silently drop large binary body streams before
    //                        the rewrite proxy forwards them. The API Gateway
    //                        authenticates the JWT directly, so middleware auth
    //                        is redundant here and actively harmful for uploads.
    '/api/((?!auth|v1/ai/kyc).+)',
  ],
}
