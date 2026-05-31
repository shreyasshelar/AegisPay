import type { AegisApiClient } from './base'
import {
  TransactionSchema,
  PagedTransactionsSchema,
  type Transaction,
  type CreateTransactionRequest,
  type PagedTransactions,
} from '@aegispay/shared-types'

export interface ListTransactionsParams {
  page?: number
  size?: number
  status?: string
  fromDate?: string
  toDate?: string
}

export class TransactionsClient {
  constructor(private readonly client: AegisApiClient) {}

  async create(
    request: CreateTransactionRequest,
    idempotencyKey: string,
  ): Promise<Transaction> {
    const data = await this.client.post<unknown>('/api/v1/transactions', request, {
      headers: { 'X-Idempotency-Key': idempotencyKey },
    })
    return TransactionSchema.parse(data)
  }

  async get(id: string): Promise<Transaction> {
    const data = await this.client.get<unknown>(`/api/v1/transactions/${id}`)
    return TransactionSchema.parse(data)
  }

  async list(params: ListTransactionsParams = {}): Promise<PagedTransactions> {
    const data = await this.client.get<unknown>('/api/v1/transactions', { params })
    return PagedTransactionsSchema.parse(data)
  }
}
