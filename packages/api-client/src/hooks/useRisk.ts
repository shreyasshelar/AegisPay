import { useQuery } from '@tanstack/react-query'
import { useApiClient } from './context'
import type { ListRiskCasesParams, PagedRiskCases } from '../client/risk'

// ── Query keys ────────────────────────────────────────────────────────────────

export const riskKeys = {
  all:   () => ['risk']                                  as const,
  cases: (params: ListRiskCasesParams) => ['risk', 'cases', params] as const,
}

// ── Hooks ─────────────────────────────────────────────────────────────────────

/**
 * Fetches a paginated, filterable risk-case list for the back-office panel.
 *
 * Supported filters (all optional):
 *  - decision   APPROVED | REVIEW | REJECTED
 *  - minScore / maxScore   inclusive score bounds (0-100)
 *  - fromDate / toDate     ISO-8601 datetime strings
 *  - page / size           pagination
 *
 * Changing any filter or page re-fetches immediately (queryKey includes params).
 */
export function useRiskCases(params: ListRiskCasesParams = {}) {
  const { risk } = useApiClient()
  return useQuery<PagedRiskCases>({
    queryKey:  riskKeys.cases(params),
    queryFn:   () => risk.listRiskCases(params),
    staleTime: 30_000,
    placeholderData: (prev) => prev,  // keep showing previous data while refetching
  })
}
