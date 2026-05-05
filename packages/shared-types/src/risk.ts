import { z } from 'zod'

export const RiskDecisionSchema = z.enum(['APPROVED', 'REVIEW', 'REJECTED'])
export type RiskDecision = z.infer<typeof RiskDecisionSchema>

export const RiskCaseSchema = z.object({
  id: z.string().uuid(),
  transactionId: z.string().uuid(),
  userId: z.string().uuid(),
  riskScore: z.number().min(0).max(100),
  decision: RiskDecisionSchema,
  ruleFlags: z.record(z.unknown()),
  ragExplanation: z.string().nullable(),
  createdAt: z.string().datetime(),
})
export type RiskCase = z.infer<typeof RiskCaseSchema>

export const FraudExplainRequestSchema = z.object({
  transactionId: z.string().uuid(),
  riskScore: z.number().min(0).max(100),
  flaggedRules: z.array(z.string()).min(1),
})
export type FraudExplainRequest = z.infer<typeof FraudExplainRequestSchema>

export const FraudExplainResponseSchema = z.object({
  transactionId: z.string().uuid(),
  explanation: z.string(),
})
export type FraudExplainResponse = z.infer<typeof FraudExplainResponseSchema>

export const ErrorResolutionRequestSchema = z.object({
  errorCode: z.string().min(1),
  errorMessage: z.string().optional(),
})
export type ErrorResolutionRequest = z.infer<typeof ErrorResolutionRequestSchema>

export const ErrorResolutionResponseSchema = z.object({
  errorCode: z.string(),
  resolution: z.string(),
})
export type ErrorResolutionResponse = z.infer<typeof ErrorResolutionResponseSchema>
