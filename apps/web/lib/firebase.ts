import { initializeApp, getApps } from 'firebase/app'
import { getAuth } from 'firebase/auth'

/**
 * Firebase is used for phone-number OTP verification ONLY.
 * It is NOT the primary auth provider — Keycloak handles all login/session management.
 *
 * After OTP verification succeeds the Firebase session is immediately signed out,
 * so the Firebase auth state never interferes with the Keycloak/next-auth session.
 *
 * Required env vars (add to .env.local and inject in CI):
 *   NEXT_PUBLIC_FIREBASE_API_KEY
 *   NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN
 *   NEXT_PUBLIC_FIREBASE_PROJECT_ID
 *   NEXT_PUBLIC_FIREBASE_APP_ID
 */
const apiKey = process.env.NEXT_PUBLIC_FIREBASE_API_KEY

/**
 * firebaseAuth is null when Firebase env vars are not configured.
 * Components that use it must guard: if (!firebaseAuth) { show disabled state }
 * This prevents a module-level crash from breaking pages that only optionally use Firebase.
 */
export let firebaseAuth: ReturnType<typeof getAuth> | null = null

if (apiKey) {
  try {
    const firebaseConfig = {
      apiKey,
      authDomain: process.env.NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN!,
      projectId:  process.env.NEXT_PUBLIC_FIREBASE_PROJECT_ID!,
      appId:      process.env.NEXT_PUBLIC_FIREBASE_APP_ID!,
    }
    const app = getApps().length === 0 ? initializeApp(firebaseConfig) : getApps()[0]
    firebaseAuth = getAuth(app)
  } catch (e) {
    console.warn('[firebase] Init failed — phone OTP disabled:', e)
  }
}
