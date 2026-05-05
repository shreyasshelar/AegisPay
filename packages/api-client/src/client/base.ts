import axios, { type AxiosInstance, type AxiosRequestConfig, AxiosError } from 'axios'

export interface ApiClientConfig {
  baseURL: string
  getAccessToken: () => Promise<string | null>
  onUnauthorized?: () => void
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
      // Correlation ID for distributed tracing (propagated server-side via MDC)
      req.headers['X-Correlation-ID'] = crypto.randomUUID()
      return req
    })

    // Unwrap ApiResponse<T> envelope + handle 401
    this.axios.interceptors.response.use(
      (res) => {
        // The backend always returns { success, data, ... }
        // Unwrap so callers receive T directly instead of the envelope.
        const envelope = res.data as ApiEnvelope<unknown>
        if (envelope && typeof envelope === 'object' && 'success' in envelope) {
          res.data = envelope.data
        }
        return res
      },
      (err: AxiosError) => {
        if (err.response?.status === 401) {
          config.onUnauthorized?.()
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
  constructor(
    message: string,
    public readonly status: number,
    public readonly errorCode?: string,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

function mapApiError(err: AxiosError): ApiError {
  const envelope = err.response?.data as ApiEnvelope<never> | undefined
  const status = err.response?.status ?? 0
  const message = envelope?.error?.message ?? err.message ?? 'Unknown error'
  const errorCode = envelope?.error?.code
  return new ApiError(message, status, errorCode)
}
