import type { AegisApiClient } from './base'
import { RiskCaseSchema, type RiskCase } from '@aegispay/shared-types'
import { z } from 'zod'

// ── Paged response schema ─────────────────────────────────────────────────────

const PagedRiskCasesSchema = z.object({
  content:       z.array(RiskCaseSchema),
  page:          z.number(),
  size:          z.number(),
  totalElements: z.number(),
  totalPages:    z.number(),
  first:         z.boolean(),
  last:          z.boolean(),
})

export type PagedRiskCases = z.infer<typeof PagedRiskCasesSchema>

// ── Filter params ─────────────────────────────────────────────────────────────

export interface ListRiskCasesParams {
  page?:      number
  size?:      number
  /** APPROVED | REVIEW | REJECTED  (empty string = no filter) */
  decision?:  string
  /** Inclusive lower bound on riskScore (0-100) */
  minScore?:  number
  /** Inclusive upper bound on riskScore (0-100) */
  maxScore?:  number
  /** ISO-8601 datetime string e.g. "2026-05-01T00:00:00Z" */
  fromDate?:  string
  /** ISO-8601 datetime string e.g. "2026-05-31T23:59:59Z" */
  toDate?:    string
}

// ── Client ────────────────────────────────────────────────────────────────────

export class RiskClient {
  constructor(private readonly client: AegisApiClient) {}

  async listRiskCases(params: ListRiskCasesParams = {}): Promise<PagedRiskCases> {
    const query: Record<string, unknown> = {
      page: params.page ?? 0,
      size: params.size ?? 20,
    }
    if (params.decision) query.decision  = params.decision
    if (params.minScore != null) query.minScore = params.minScore
    if (params.maxScore != null) query.maxScore = params.maxScore
    if (params.fromDate) query.fromDate  = params.fromDate
    if (params.toDate)   query.toDate    = params.toDate

    const data = await this.client.get<unknown>('/api/v1/risk/cases', { params: query })

    // Handle both paged { content: [...], totalElements, ... } and plain array shapes
    if (Array.isArray(data)) {
      const content = z.array(RiskCaseSchema).parse(data)
      return {
        content,
        page: 0,
        size: content.length,
        totalElements: content.length,
        totalPages: 1,
        first: true,
        last: true,
      }
    }
    return PagedRiskCasesSchema.parse(data)
  }
}
