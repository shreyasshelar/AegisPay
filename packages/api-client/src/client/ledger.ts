import type { AegisApiClient } from './base.js'
import { AccountSchema, type Account, type LedgerEntry } from '@aegispay/shared-types'
import { z } from 'zod'

export class LedgerClient {
  constructor(private readonly client: AegisApiClient) {}

  async getAccount(userId: string): Promise<Account> {
    const data = await this.client.get<unknown>(`/api/v1/ledger/accounts/${userId}`)
    return AccountSchema.parse(data)
  }

  async getEntries(transactionId: string): Promise<LedgerEntry[]> {
    const data = await this.client.get<unknown>('/api/v1/ledger/entries', {
      params: { transactionId },
    })
    return z.array(z.unknown()).parse(data) as LedgerEntry[]
  }
}
