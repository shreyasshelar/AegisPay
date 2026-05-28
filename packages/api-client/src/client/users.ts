import { z } from 'zod'
import type { AegisApiClient } from './base'
import {
  UserSchema,
  KycStatusSchema,
  type User,
  type KycStatus,
  type KycUploadRequest,
} from '@aegispay/shared-types'

// ── Back-office user list ─────────────────────────────────────────────────────

export const PagedUsersSchema = z.object({
  content:       z.array(UserSchema),
  totalElements: z.number(),
  totalPages:    z.number(),
  page:          z.number(),
  size:          z.number(),
  last:          z.boolean(),
})
export type PagedUsers = z.infer<typeof PagedUsersSchema>

export interface UserListParams {
  page?:      number
  size?:      number
  kycStatus?: KycStatus | string
}

export class UsersClient {
  constructor(private readonly client: AegisApiClient) {}

  async getMe(): Promise<User> {
    const data = await this.client.get<unknown>('/api/v1/users/me')
    return UserSchema.parse(data)
  }

  async getById(id: string): Promise<User> {
    const data = await this.client.get<unknown>(`/api/v1/users/${id}`)
    return UserSchema.parse(data)
  }

  /** Back-office: paginated user list with optional KYC status filter. */
  async list(params: UserListParams = {}): Promise<PagedUsers> {
    const query: Record<string, string | number> = {
      page: params.page ?? 0,
      size: params.size ?? 50,
    }
    if (params.kycStatus) query.kycStatus = params.kycStatus
    const data = await this.client.get<unknown>('/api/v1/users', { params: query })
    return PagedUsersSchema.parse(data)
  }

  /** Back-office: override a user's KYC status (APPROVED / REJECTED / MANUAL_REVIEW). */
  async updateKycStatus(userId: string, status: string): Promise<void> {
    await this.client.patch(`/api/v1/users/${userId}/kyc/status`, { status })
  }

  /**
   * Submits a KYC document for async AI processing via multipart/form-data.
   *
   * The server returns 202 Accepted immediately — the 4-step vision pipeline runs
   * in the background (up to ~6 min on free-tier providers). When the pipeline
   * finishes the AI Platform calls back User Service, which publishes a
   * KycStatusChangedEvent → Kafka → Notification Service → WebSocket push.
   * The frontend receives the result via the existing notification channel.
   *
   * Multipart is used instead of base64 JSON to avoid buffering a ~6.7 MB JSON
   * string in the Next.js dev proxy.  The raw binary file (≤ 5 MB) streams through
   * the proxy transparently; the server encodes it to base64 for the vision AI API.
   */
  async processKycDocument(request: KycUploadRequest): Promise<void> {
    const formData = new FormData()
    formData.append('file', request.file)
    if (request.documentType) {
      formData.append('documentType', request.documentType)
    }
    if (request.registeredName) {
      formData.append('registeredName', request.registeredName)
    }
    await this.client.postFormData<unknown>('/api/v1/ai/kyc/process', formData)
  }

  async confirmKyc(userId: string, documentType: string): Promise<void> {
    await this.client.patch(`/api/v1/users/${userId}/kyc`, { documentType })
  }

  async registerPushToken(userId: string, token: string, platform: 'ios' | 'android'): Promise<void> {
    await this.client.post(`/api/v1/users/${userId}/push-token`, { token, platform })
  }
}
