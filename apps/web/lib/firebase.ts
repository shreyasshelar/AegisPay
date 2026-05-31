import { initializeApp, getApps } from 'firebase/app'
import { getAuth, initializeRecaptchaConfig } from 'firebase/auth'

/**
 * Firebase is used for phone-number OTP verification ONLY.
 * It is NOT the primary auth provider — Keycloak handles all login/session management.
 *
 * After OTP verification succeeds the Firebase session is immediately signed out,
 * so the Firebase auth state never interferes with the Keycloak/next-auth session.
 *
 * SDK version requirement: firebase >= 11.0.0 (@firebase/auth >= 1.8.0).
 * Firebase Console requires "web SDK version 11+" for reCAPTCHA Enterprise Phone Auth.
 * Older SDKs always used reCAPTCHA v2 for phone auth and received INVALID_APP_CREDENTIAL
 * when the project had PHONE_PROVIDER Enterprise enforcement enabled.
 *
 * Required env vars (add to .env.local and inject in CI):
 *   NEXT_PUBLIC_FIREBASE_API_KEY
 *   NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN
 *   NEXT_PUBLIC_FIREBASE_PROJECT_ID
 *   NEXT_PUBLIC_FIREBASE_APP_ID
 */
const apiKey      = process.env.NEXT_PUBLIC_FIREBASE_API_KEY
const authDomain  = process.env.NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN
const projectId   = process.env.NEXT_PUBLIC_FIREBASE_PROJECT_ID
const appId       = process.env.NEXT_PUBLIC_FIREBASE_APP_ID

/**
 * firebaseAuth is null when Firebase env vars are not configured.
 * Components that use it must guard: if (!firebaseAuth) { show disabled state }
 * This prevents a module-level crash from breaking pages that only optionally use Firebase.
 */
export let firebaseAuth: ReturnType<typeof getAuth> | null = null

if (apiKey && authDomain && projectId && appId) {
  try {
    const firebaseConfig = { apiKey, authDomain, projectId, appId }
    const app = getApps().length === 0 ? initializeApp(firebaseConfig) : getApps()[0]
    firebaseAuth = getAuth(app)

    // Pre-warm reCAPTCHA Enterprise config on module load.
    // signInWithPhoneNumber auto-initialises if this is not called first, but calling
    // it eagerly here avoids a round-trip delay when the user clicks "Send OTP".
    // Requires firebase >= 11 / @firebase/auth >= 1.8.0.
    initializeRecaptchaConfig(firebaseAuth).catch(e => {
      console.warn('[firebase] reCAPTCHA Enterprise pre-warm failed (non-fatal):', e.message)
    })
  } catch (e) {
    console.warn('[firebase] Init failed — phone OTP disabled:', e)
  }
} else if (apiKey) {
  // API key is set but one or more other vars are missing — most common: App ID left blank
  const missing = [
    !authDomain && 'NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN',
    !projectId  && 'NEXT_PUBLIC_FIREBASE_PROJECT_ID',
    !appId      && 'NEXT_PUBLIC_FIREBASE_APP_ID',
  ].filter(Boolean).join(', ')
  console.warn(`[firebase] Phone OTP disabled — missing env vars: ${missing}`)
  console.warn('[firebase] Set the missing vars in .secrets.bat (local) or CI secrets (GCP).')
}
