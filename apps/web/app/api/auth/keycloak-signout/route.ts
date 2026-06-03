import { getToken } from 'next-auth/jwt'
import { NextRequest, NextResponse } from 'next/server'

/**
 * GET /api/auth/keycloak-signout
 *
 * Terminates both the NextAuth session AND the Keycloak SSO session in one
 * redirect chain so the user is truly logged out everywhere.
 *
 * Flow:
 *   1. Read the idToken from the encrypted NextAuth JWT cookie (server-side only).
 *   2. Clear all NextAuth session cookies in the response (base + all chunks).
 *   3. If an idToken is present, redirect to Keycloak's end_session endpoint with
 *      id_token_hint — Keycloak invalidates the SSO session and redirects to /login.
 *   4. Fallback: redirect directly to /login (idToken absent or issuer not configured).
 *
 * Why not use NextAuth's built-in signOut()?
 *   signOut() (client-side) only clears the local NextAuth cookie; it does NOT call
 *   Keycloak's end_session endpoint, leaving the Keycloak SSO session alive.  The
 *   login page works around this with prompt=login, but the SSO session persists
 *   across all Keycloak-integrated apps until it times out.  This route fixes that.
 */
export async function GET(request: NextRequest) {
  const token  = await getToken({ req: request })
  const issuer = process.env.KEYCLOAK_ISSUER

  // Derive post-logout destination.
  // MUST use NEXTAUTH_URL — request.nextUrl.origin inside the pod is the
  // server's bind address (http://0.0.0.0:3000), not the public hostname.
  // Keycloak rejects post_logout_redirect_uri values that aren't in its
  // allowed-redirect-URIs list, so we must send the public HTTPS domain.
  const appUrl        = (process.env.NEXTAUTH_URL ?? request.nextUrl.origin).replace(/\/$/, '')
  const postLogoutUri = `${appUrl}/login`

  let redirectTarget = postLogoutUri

  if (token?.idToken && issuer) {
    const endSessionUrl = new URL(`${issuer}/protocol/openid-connect/logout`)
    endSessionUrl.searchParams.set('id_token_hint',          String(token.idToken))
    endSessionUrl.searchParams.set('post_logout_redirect_uri', postLogoutUri)
    redirectTarget = endSessionUrl.toString()
  }

  const response = NextResponse.redirect(redirectTarget)

  // Clear all NextAuth session cookies — both the base name and the per-chunk
  // variants (.0, .1, …) that next-auth uses when the JWT > 4096 bytes.
  // Use the NextAuth default names (no custom override in authOptions.cookies).
  //
  // IMPORTANT: response.cookies.delete(name) in Next.js App Router does NOT include
  // the Secure flag in the Set-Cookie header.  Browsers silently reject deletion of
  // __Secure-* prefix cookies unless the Set-Cookie also carries Secure=true.
  // Must use response.cookies.set() with explicit maxAge=0 and secure/httpOnly flags.
  const useSecureCookies = process.env.NEXTAUTH_URL?.startsWith('https://') ?? false
  const sessionBase  = useSecureCookies ? '__Secure-next-auth.session-token' : 'next-auth.session-token'
  const csrfBase     = useSecureCookies ? '__Host-next-auth.csrf-token'      : 'next-auth.csrf-token'
  const p            = useSecureCookies ? '__Secure-next-auth.' : 'next-auth.'

  const wipe = (name: string) =>
    response.cookies.set({ name, value: '', maxAge: 0, path: '/', httpOnly: true, secure: useSecureCookies, sameSite: 'lax' })

  wipe(sessionBase)
  for (let i = 0; i < 5; i++) wipe(`${sessionBase}.${i}`)

  // CSRF uses __Host- prefix on HTTPS — no Domain, Path must be /, Secure required.
  response.cookies.set({ name: csrfBase, value: '', maxAge: 0, path: '/', httpOnly: true, secure: useSecureCookies, sameSite: 'lax' })
  wipe(`${p}callback-url`)
  wipe(`${p}pkce.code_verifier`)
  wipe(`${p}state`)
  wipe(`${p}nonce`)

  // Prevent Cloudflare or any CDN from caching this redirect — each call is
  // user-specific (reads idToken) and must reach the origin every time.
  response.headers.set('Cache-Control', 'no-store, no-cache, must-revalidate')

  return response
}
