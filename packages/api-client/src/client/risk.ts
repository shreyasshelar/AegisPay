import type { AegisApiClient } from './base'
import { RiskCaseSchema, type RiskCase } from '@aegispay/shared-types'
import { z } from 'zod'

const PagedRiskCasesSchema = z.object({
  content:       z.array(RiskCaseSchema),
  totalElements: z.number(),
  last:          z.boolean(),
})

export interface ListRiskCasesParams {
  page?:     number
  size?:     number
  decision?: string
}

export class RiskClient {
  constructor(private readonly client: AegisApiClient) {}

  async listRiskCases(params: ListRiskCasesParams = {}): Promise<RiskCase[]> {
    const data = await this.client.get<unknown>('/api/v1/risk/cases', {
      params: {
        page:     params.page ?? 0,
        size:     params.size ?? 50,
        decision: params.decision,
      },
    })
    // Handle both paged { content: [...] } and plain array shapes
    if (Array.isArray(data)) {
      return z.array(RiskCaseSchema).parse(data)
    }
    return PagedRiskCasesSchema.parse(data).content
  }
}
