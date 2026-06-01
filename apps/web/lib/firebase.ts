import { initializeApp, getApps } from 'firebase/app'
import { getAuth } from 'firebase/auth'

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
 * reCAPTCHA is SDK-managed through RecaptchaVerifier/signInWithPhoneNumber.
 * The app must not manually mint Enterprise tokens for Firebase Phone Auth.
 *
 * Required env vars (add to .env.local and inject in CI):
 *   NEXT_PUBLIC_FIREBASE_API_KEY
 *   NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN
 *   NEXT_PUBLIC_FIREBASE_PROJECT_ID
 *   NEXT_PUBLIC_FIREBASE_APP_ID
 *
 * Firebase/GCP prerequisites for real-number OTP:
 *   Firebase Console → Authentication → Settings → Authorized domains
 *   → add localhost (dev) + aegispay.shreyasshelar.uk (prod).
 *   Firebase Auth/Identity Platform owns the reCAPTCHA Enterprise key selection.
 */
const apiKey         = process.env.NEXT_PUBLIC_FIREBASE_API_KEY
const authDomain     = process.env.NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN
const projectId      = process.env.NEXT_PUBLIC_FIREBASE_PROJECT_ID
const appId          = process.env.NEXT_PUBLIC_FIREBASE_APP_ID

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

    // ── Local-dev bypass ─────────────────────────────────────────────────────
    // Set NEXT_PUBLIC_FIREBASE_TEST_MODE=true in .env.local to skip reCAPTCHA
    // entirely and use test phone numbers from Firebase Console instead.
    //
    // How to set up test numbers:
    //   Firebase Console → Authentication → Sign-in method → Phone
    //   → Phone numbers for testing → Add number (e.g. +15555550100, code 123456)
    //
    // This flag must NOT be set in production — it disables all app verification.
    if (process.env.NEXT_PUBLIC_FIREBASE_TEST_MODE === 'true') {
      firebaseAuth.settings.appVerificationDisabledForTesting = true
      console.info('[firebase] ⚠ appVerificationDisabledForTesting=true — use test phone numbers only')
    }
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
