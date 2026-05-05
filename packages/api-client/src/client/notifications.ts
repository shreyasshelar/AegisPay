import type { AegisApiClient } from './base.js'
import { PagedNotificationsSchema, type PagedNotifications } from '@aegispay/shared-types'

export class NotificationsClient {
  constructor(private readonly client: AegisApiClient) {}

  async list(page = 0, size = 20): Promise<PagedNotifications> {
    const data = await this.client.get<unknown>('/api/v1/notifications', {
      params: { page, size },
    })
    return PagedNotificationsSchema.parse(data)
  }
}
