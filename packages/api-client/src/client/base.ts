import axios, { type AxiosInstance, type AxiosRequestConfig, AxiosError } from 'axios'

export interface ApiClientConfig {
  baseURL: string
  getAccessToken: () => Promise<string | null>
  onUnauthorized?: () => void
  /**
   * Called when any backend endpoint returns HTTP 404 with errorCode "USER_NOT_FOUND".
   * This happens when the AegisPay DB record doesn't exist for the authenticated Keycloak sub
   * (e.g. post-DB-wipe with a stale session, or a session created before auto-registration was
   * deployed). The handler should trigger sign-out so the user re-authenticates and the
   * auth.ts jwt callback can call /register to re-provision the account.
   */
  onUserNotFound?: () => void
}

/**
 * Shape of the `ApiResponse<T>` envelope returned by all AegisPay backend endpoints.
 * Every successful response is: { success: true, data: T, timestamp: "..." }
 * Every error response is:      { success: false, error: { code, message }, timestamp: "..." }
 */
interface ApiEnvelope<T> {
  success: boolean
  data?: T
  error?: { code?: string; message?: string }
  correlationId?: string
  timestamp?: string
}

export class AegisApiClient {
  readonly axios: AxiosInstance

  constructor(private readonly config: ApiClientConfig) {
    this.axios = axios.create({
      baseURL: config.baseURL,
      timeout: 30_000,
      headers: { 'Content-Type': 'application/json' },
    })

    // Attach Bearer token before every request
    this.axios.interceptors.request.use(async (req) => {
      const token = await config.getAccessToken()
      if (token) {
        req.headers.Authorization = `Bearer ${token}`
      }
      // Correlation ID for distributed tracing (propagated server-side via MDC).
      // crypto.randomUUID() requires a secure context (HTTPS); fall back to a
      // Math.random-based UUID so LAN HTTP dev access still works.
      req.headers['X-Correlation-ID'] =
        (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function')
          ? crypto.randomUUID()
          : 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
              const r = (Math.random() * 16) | 0
              return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16)
            })
      return req
    })

    // Unwrap ApiResponse<T> envelope + handle 401
    this.axios.interceptors.response.use(
      (res) => {
        const envelope = res.data as ApiEnvelope<unknown>
        if (envelope && typeof envelope === 'object' && 'success' in envelope) {
          // Backend returned success:false with HTTP 2xx — treat as application-level error
          if (!envelope.success) {
            return Promise.reject(new ApiError(
              envelope.error?.message ?? 'Request failed',
              res.status,
              envelope.error?.code,
            ))
          }
          res.data = envelope.data
        }
        return res
      },
      (err: AxiosError) => {
        if (err.response?.status === 401) {
          config.onUnauthorized?.()
        }
        // USER_NOT_FOUND on the caller's own identity endpoint means the Keycloak JWT is
        // valid but the AegisPay DB record is absent (stale session / post-DB-wipe /
        // deployment gap where auth.ts ran before always-register was shipped).
        // Trigger sign-out so the user re-authenticates; auth.ts then calls /register
        // (always-idempotent) and re-provisions the record before the first API call.
        //
        // ⚠  CRITICAL SCOPE GUARD: only fire for /users/me — never for /users/{id} calls
        // that resolve a different user (payee lookup, back-office read, etc.).  Those are
        // legitimate 404s and must NEVER sign the caller out mid-session.
        if (err.response?.status === 404) {
          const url      = err.config?.url ?? ''
          const envelope = err.response?.data as ApiEnvelope<never> | undefined
          if (
            envelope?.error?.code === 'USER_NOT_FOUND' &&
            /\/users\/me(\?|$)/.test(url)
          ) {
            config.onUserNotFound?.()
          }
        }
        return Promise.reject(mapApiError(err))
      },
    )
  }

  async get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    const res = await this.axios.get<T>(url, config)
    return res.data
  }

  async post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    const res = await this.axios.post<T>(url, data, config)
    return res.data
  }

  async patch<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    const res = await this.axios.patch<T>(url, data, config)
    return res.data
  }

  async delete<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    const res = await this.axios.delete<T>(url, config)
    return res.data
  }
}

export class ApiError extends Error {
  /**
   * @param status         HTTP status code (0 = network error before any response)
   * @param errorCode      Backend {@code ErrorResponse.errorCode}, e.g. "SERVICE_UNAVAILABLE"
   * @param retryAfterSecs Value of the {@code Retry-After} response header (seconds).
   *                       Present on 503 responses from the gateway circuit-breaker fallback.
   * @param failedService  Value of the {@code X-Failed-Service} header — which downstream
   *                       route tripped the circuit (e.g. "ai-platform", "user-service").
   */
  constructor(
    message: string,
    public readonly status: number,
    public readonly errorCode?: string,
    public readonly retryAfterSecs?: number,
    public readonly failedService?: string,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

function mapApiError(err: AxiosError): ApiError {
  const envelope    = err.response?.data as ApiEnvelope<never> | undefined
  const headers     = err.response?.headers
  const status      = err.response?.status ?? 0
  const message     = envelope?.error?.message ?? err.message ?? 'Unknown error'
  const errorCode   = envelope?.error?.code

  // Parse Retry-After header (integer seconds) injected by FallbackController on 503.
  const retryAfter  = headers?.['retry-after']
  const retryAfterSecs = retryAfter ? parseInt(String(retryAfter), 10) || undefined : undefined

  const failedService = headers?.['x-failed-service'] as string | undefined

  return new ApiError(message, status, errorCode, retryAfterSecs, failedService)
}
