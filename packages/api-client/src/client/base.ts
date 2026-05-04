import axios, { type AxiosInstance, type AxiosRequestConfig, AxiosError } from 'axios'

export interface ApiClientConfig {
  baseURL: string
  getAccessToken: () => Promise<string | null>
  onUnauthorized?: () => void
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
      // Correlation ID for distributed tracing
      req.headers['X-Correlation-ID'] = crypto.randomUUID()
      return req
    })

    // Redirect to login on 401
    this.axios.interceptors.response.use(
      (res) => res,
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
  const data = err.response?.data as { message?: string; errorCode?: string } | undefined
  const status = err.response?.status ?? 0
  const message = data?.message ?? err.message ?? 'Unknown error'
  return new ApiError(message, status, data?.errorCode)
}
