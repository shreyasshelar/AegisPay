import { z } from 'zod'
import type { AegisApiClient } from './base'
import {
  UserSchema,
  KycStatusSchema,
  KycProcessingResultSchema,
  type User,
  type KycStatus,
  type KycUploadRequest,
  type KycProcessingResult,
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

  async processKycDocument(request: KycUploadRequest): Promise<KycProcessingResult> {
    const data = await this.client.post<unknown>('/api/v1/ai/kyc/process', request)
    return KycProcessingResultSchema.parse(data)
  }

  async confirmKyc(userId: string, documentType: string): Promise<void> {
    await this.client.patch(`/api/v1/users/${userId}/kyc`, { documentType })
  }

  async registerPushToken(userId: string, token: string, platform: 'ios' | 'android'): Promise<void> {
    await this.client.post(`/api/v1/users/${userId}/push-token`, { token, platform })
  }
}
