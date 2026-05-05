/**
 * useBiometric — WebAuthn (Passkey) hook for AegisPay web.
 *
 * Provides biometric re-authentication using the Web Authentication API
 * (navigator.credentials / PublicKeyCredential).
 *
 * Flow:
 *  1. On first use: `register()` creates a platform authenticator credential
 *     stored locally (browser/OS passkey store). The credential ID is saved
 *     to localStorage so subsequent assertions can reference it.
 *  2. On each protected action: `authenticate()` presents the OS biometric
 *     prompt (Face ID on macOS/iOS Safari, Windows Hello, Android fingerprint
 *     in Chrome) and returns the signed assertion.
 *
 * Note: The credential is LOCAL — it is NOT sent to the backend in this
 * implementation. It acts as a client-side session lock (same as mobile).
 * A production deployment would send the assertion to the backend for
 * server-side verification (full FIDO2).
 */

import { useCallback, useEffect, useState } from 'react'

const CRED_ID_KEY  = 'aegispay.webauthn.credId'
const ENABLED_KEY  = 'aegispay.webauthn.enabled'
const RP_ID        = typeof window !== 'undefined' ? window.location.hostname : 'localhost'
const RP_NAME      = 'AegisPay'
const USER_ID_SEED = 'aegispay-user'  // deterministic; a real impl uses the actual userId

export interface BiometricState {
  isSupported:     boolean
  isRegistered:    boolean
  isEnabled:       boolean
  isAuthenticating: boolean
  error:           string | null
  register:        () => Promise<boolean>
  authenticate:    () => Promise<boolean>
  setEnabled:      (v: boolean) => void
  reset:           () => void
}

function isSupportedBrowser(): boolean {
  return (
    typeof window !== 'undefined' &&
    !!window.PublicKeyCredential &&
    typeof window.PublicKeyCredential === 'function'
  )
}

function getStoredCredId(): Uint8Array | null {
  const raw = localStorage.getItem(CRED_ID_KEY)
  if (!raw) return null
  try {
    return Uint8Array.from(atob(raw), c => c.charCodeAt(0))
  } catch {
    return null
  }
}

function storeCredId(credId: ArrayBuffer): void {
  const bytes  = new Uint8Array(credId)
  const base64 = btoa(String.fromCharCode(...bytes))
  localStorage.setItem(CRED_ID_KEY, base64)
}

function randomBytes(length: number): Uint8Array {
  return crypto.getRandomValues(new Uint8Array(length))
}

export function useBiometric(): BiometricState {
  const [isSupported,      setIsSupported]      = useState(false)
  const [isRegistered,     setIsRegistered]      = useState(false)
  const [isEnabled,        setIsEnabledState]    = useState(false)
  const [isAuthenticating, setIsAuthenticating]  = useState(false)
  const [error,            setError]             = useState<string | null>(null)

  // ── Init ──────────────────────────────────────────────────────────────────

  useEffect(() => {
    const supported = isSupportedBrowser()
    setIsSupported(supported)
    if (supported) {
      setIsRegistered(!!getStoredCredId())
      setIsEnabledState(localStorage.getItem(ENABLED_KEY) === 'true')
    }
  }, [])

  // ── Register ──────────────────────────────────────────────────────────────

  const register = useCallback(async (): Promise<boolean> => {
    if (!isSupportedBrowser()) return false
    setError(null)
    setIsAuthenticating(true)

    try {
      const userId = new TextEncoder().encode(USER_ID_SEED)

      const credential = await navigator.credentials.create({
        publicKey: {
          challenge:              randomBytes(32),
          rp:                     { id: RP_ID, name: RP_NAME },
          user:                   { id: userId, name: 'aegispay-user', displayName: 'AegisPay User' },
          pubKeyCredParams:       [
            { type: 'public-key', alg: -7  },   // ES256
            { type: 'public-key', alg: -257 },  // RS256
          ],
          authenticatorSelection: {
            authenticatorAttachment: 'platform',      // device biometric only (no security keys)
            userVerification:        'required',
            residentKey:             'preferred',
          },
          timeout:                60_000,
          attestation:            'none',
        },
      }) as PublicKeyCredential | null

      if (!credential) {
        setError('Registration cancelled.')
        return false
      }

      storeCredId(credential.rawId)
      localStorage.setItem(ENABLED_KEY, 'true')
      setIsRegistered(true)
      setIsEnabledState(true)
      return true

    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Registration failed.'
      setError(msg)
      return false
    } finally {
      setIsAuthenticating(false)
    }
  }, [])

  // ── Authenticate ──────────────────────────────────────────────────────────

  const authenticate = useCallback(async (): Promise<boolean> => {
    if (!isSupportedBrowser()) return false
    const credId = getStoredCredId()
    if (!credId) return false

    setError(null)
    setIsAuthenticating(true)

    try {
      const assertion = await navigator.credentials.get({
        publicKey: {
          challenge:        randomBytes(32),
          rpId:             RP_ID,
          allowCredentials: [{ type: 'public-key', id: credId }],
          userVerification: 'required',
          timeout:          60_000,
        },
      }) as PublicKeyCredential | null

      if (!assertion) {
        setError('Authentication cancelled.')
        return false
      }

      return true  // assertion verified client-side (OS performed the biometric check)

    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Authentication failed.'
      setError(msg)
      return false
    } finally {
      setIsAuthenticating(false)
    }
  }, [])

  // ── setEnabled ────────────────────────────────────────────────────────────

  const setEnabled = useCallback((value: boolean) => {
    localStorage.setItem(ENABLED_KEY, String(value))
    setIsEnabledState(value)
  }, [])

  // ── reset ─────────────────────────────────────────────────────────────────

  const reset = useCallback(() => {
    localStorage.removeItem(CRED_ID_KEY)
    localStorage.removeItem(ENABLED_KEY)
    setIsRegistered(false)
    setIsEnabledState(false)
    setError(null)
  }, [])

  return {
    isSupported,
    isRegistered,
    isEnabled,
    isAuthenticating,
    error,
    register,
    authenticate,
    setEnabled,
    reset,
  }
}
