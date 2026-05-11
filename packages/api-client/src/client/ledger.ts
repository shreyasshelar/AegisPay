import type { AegisApiClient } from './base'
import { AccountSchema, type Account, type LedgerEntry } from '@aegispay/shared-types'
import { z } from 'zod'

export class LedgerClient {
  constructor(private readonly client: AegisApiClient) {}

  /** Customer: fetch own primary (INR) account via JWT sub — no userId needed. */
  async getMyAccount(): Promise<Account> {
    const data = await this.client.get<unknown[]>('/api/v1/ledger/accounts/me')
    const list = z.array(AccountSchema).parse(data)
    const primary = list.find(a => a.currency === 'INR') ?? list[0]
    if (!primary) throw new Error('No account found')
    return primary
  }

  /** Back-office: fetch any user's accounts by userId. */
  async getAccountsForUser(userId: string): Promise<Account[]> {
    const data = await this.client.get<unknown[]>(`/api/v1/ledger/accounts/${userId}`)
    return z.array(AccountSchema).parse(data)
  }

  async getEntries(transactionId: string): Promise<LedgerEntry[]> {
    const data = await this.client.get<unknown>('/api/v1/ledger/entries', {
      params: { transactionId },
    })
    return z.array(z.unknown()).parse(data) as LedgerEntry[]
  }
}
