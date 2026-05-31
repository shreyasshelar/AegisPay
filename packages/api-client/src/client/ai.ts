import type { AegisApiClient } from './base'
import {
  FraudExplainResponseSchema,
  ErrorResolutionResponseSchema,
  type FraudExplainRequest,
  type FraudExplainResponse,
  type ErrorResolutionRequest,
  type ErrorResolutionResponse,
} from '@aegispay/shared-types'

export class AiClient {
  constructor(private readonly client: AegisApiClient) {}

  async explainFraud(request: FraudExplainRequest): Promise<FraudExplainResponse> {
    const data = await this.client.post<unknown>('/api/v1/ai/fraud/explain', request)
    return FraudExplainResponseSchema.parse(data)
  }

  async resolveError(request: ErrorResolutionRequest): Promise<ErrorResolutionResponse> {
    const data = await this.client.post<unknown>('/api/v1/ai/errors/resolve', request)
    return ErrorResolutionResponseSchema.parse(data)
  }

  async triageIncident(serviceName: string, incidentDescription: string): Promise<string> {
    const data = await this.client.post<{ analysis: string }>('/api/v1/ai/incidents/triage', {
      serviceName,
      incidentDescription,
    })
    return data.analysis
  }
}
