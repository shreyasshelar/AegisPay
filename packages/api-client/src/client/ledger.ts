import type { AegisApiClient } from './base'
import { AccountSchema, type Account, type LedgerEntry } from '@aegispay/shared-types'
import { z } from 'zod'

// ── Wallet top-up ─────────────────────────────────────────────────────────────

export const TopUpIntentSchema = z.object({
  paymentIntentId: z.string(),
  clientSecret:    z.string(),
  amount:          z.number(),
  currency:        z.string(),
})
export type TopUpIntent = z.infer<typeof TopUpIntentSchema>

export const TopUpResultSchema = z.object({
  status:      z.enum(['SUCCEEDED', 'PENDING', 'FAILED']),
  referenceId: z.string(),
  amount:      z.number(),
  currency:    z.string(),
})
export type TopUpResult = z.infer<typeof TopUpResultSchema>

export class LedgerClient {
  constructor(private readonly client: AegisApiClient) {}

  /** Customer: fetch all own accounts (one row per currency). */
  async getMyAccounts(): Promise<Account[]> {
    const data = await this.client.get<unknown[]>('/api/v1/ledger/accounts/me')
    return z.array(AccountSchema).parse(data)
  }

  /** @deprecated Use getMyAccounts() — kept for back-compat during migration. */
  async getMyAccount(): Promise<Account> {
    const list = await this.getMyAccounts()
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

  /**
   * Step 1 of the wallet top-up flow.
   * Creates a Stripe PaymentIntent server-side and returns the client_secret
   * so the frontend can confirm with Stripe.js / Stripe Elements.
   */
  async createTopUpIntent(amount: number, currency: string): Promise<TopUpIntent> {
    const data = await this.client.post<unknown>('/api/v1/ledger/topup/intent', {
      amount,
      currency,
    })
    return TopUpIntentSchema.parse(data)
  }

  /**
   * Step 2 of the wallet top-up flow.
   * Called after the Stripe payment is confirmed client-side.
   * The backend verifies the PaymentIntent and credits the account.
   */
  async confirmTopUp(paymentIntentId: string): Promise<TopUpResult> {
    const data = await this.client.post<unknown>('/api/v1/ledger/topup/confirm', {
      paymentIntentId,
    })
    return TopUpResultSchema.parse(data)
  }
}
