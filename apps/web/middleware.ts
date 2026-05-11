import { withAuth } from 'next-auth/middleware'
import { NextResponse } from 'next/server'

export default withAuth(
  function middleware(req) {
    const token = req.nextauth.token

    // If the refresh token is broken, delete the session cookie so the user
    // arrives at /login with a clean slate (no stale error cookie that would
    // trigger providers.tsx to call signOut and race with their login click).
    if (token?.error === 'RefreshAccessTokenError') {
      const url = req.nextUrl.clone()
      url.pathname = '/login'
      const res = NextResponse.redirect(url)
      // Delete both the dev (http) and prod (https) session cookie variants
      res.cookies.delete('next-auth.session-token')
      res.cookies.delete('__Secure-next-auth.session-token')
      return res
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
    '/dashboard/:path*',
    '/transactions/:path*',
    '/send/:path*',
    '/profile/:path*',
    '/notifications/:path*',
    '/incidents/:path*',
    '/ledger/:path*',
    '/risk/:path*',
  ],
}
