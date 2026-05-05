import { useMutation, useQuery } from '@tanstack/react-query'
import { useApiClient } from './context.js'
import type {
  FraudExplainRequest,
  ErrorResolutionRequest,
} from '@aegispay/shared-types'

// ── Error Resolution ──────────────────────────────────────────────────────────

/**
 * One-shot mutation: POST /api/v1/ai/errors/resolve
 * Call on failed transactions to get a plain-English explanation + CTA.
 */
export function useResolveError() {
  const { ai } = useApiClient()
  return useMutation({
    mutationFn: (request: ErrorResolutionRequest) => ai.resolveError(request),
  })
}

// ── Fraud Explanation ─────────────────────────────────────────────────────────

/**
 * One-shot mutation: POST /api/v1/ai/fraud/explain
 * Used in back-office risk case detail.
 */
export function useExplainFraud() {
  const { ai } = useApiClient()
  return useMutation({
    mutationFn: (request: FraudExplainRequest) => ai.explainFraud(request),
  })
}

// ── Incident Triage ───────────────────────────────────────────────────────────

/**
 * One-shot mutation: POST /api/v1/ai/incidents/triage
 * Used in admin incident triage screen.
 */
export function useTriageIncident() {
  const { ai } = useApiClient()
  return useMutation({
    mutationFn: ({
      serviceName,
      incidentDescription,
    }: {
      serviceName: string
      incidentDescription: string
    }) => ai.triageIncident(serviceName, incidentDescription),
  })
}
