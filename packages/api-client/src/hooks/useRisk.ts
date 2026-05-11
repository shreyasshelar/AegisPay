import { useQuery } from '@tanstack/react-query'
import { useApiClient } from './context'
import type { RiskCase } from '@aegispay/shared-types'

// ── Query keys ────────────────────────────────────────────────────────────────

export const riskKeys = {
  all:   () => ['risk']         as const,
  cases: () => ['risk', 'cases'] as const,
}

// ── Hooks ─────────────────────────────────────────────────────────────────────

/**
 * Fetches the full risk-case queue for the back-office review panel.
 * GET /api/v1/risk/cases
 */
export function useRiskCases() {
  const { risk } = useApiClient()
  return useQuery<RiskCase[]>({
    queryKey: riskKeys.cases(),
    queryFn:  () => risk.listRiskCases(),
    staleTime: 60_000,
  })
}
