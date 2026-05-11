import type { AegisApiClient } from './base'
import {
  UserSchema,
  KycProcessingResultSchema,
  type User,
  type KycUploadRequest,
  type KycProcessingResult,
} from '@aegispay/shared-types'

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
