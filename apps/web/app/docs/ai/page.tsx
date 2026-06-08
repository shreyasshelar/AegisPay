// FILE: apps/web/app/docs/ai/page.tsx
import dynamic from 'next/dynamic'

const RagPipelineDemo = dynamic(
  () => import('../_components/RagPipelineDemo'),
  { ssr: false, loading: () => <div className="animate-pulse bg-gray-100 rounded-xl h-64" /> }
)

const IncidentTriageDemo = dynamic(
  () => import('../_components/IncidentTriageDemo'),
  { ssr: false, loading: () => <div className="animate-pulse bg-gray-100 rounded-xl h-64" /> }
)

const AI_CAPABILITIES = [
  {
    icon: '🛡️',
    title: 'Fraud Copilot',
    color: 'border-red-200 bg-red-50',
    desc: 'Real-time fraud analysis via RAG. Retrieves similar past fraud patterns from pgvector and generates human-readable explanations for every risk decision. Feeds into Risk Engine ALLOW/BLOCK decisions.',
    tech: 'pgvector HNSW + Claude Sonnet',
  },
  {
    icon: '🔧',
    title: 'Error Resolution',
    color: 'border-amber-200 bg-amber-50',
    desc: 'When a payment fails, the AI generates a user-friendly explanation of the failureCode and actionable resolution steps. Injected into every transaction.failed notification.',
    tech: 'Claude Sonnet + failureCode taxonomy',
  },
  {
    icon: '🚨',
    title: 'Incident Triage',
    color: 'border-purple-200 bg-purple-50',
    desc: 'Agentic reasoning loop that reads logs, queries metrics, checks deployment history, and takes action (rollback, restart) autonomously. Mean time to resolution measured in minutes.',
    tech: 'Claude tool_use API + ReAct pattern',
  },
  {
    icon: '🪪',
    title: 'KYC OCR',
    color: 'border-blue-200 bg-blue-50',
    desc: 'Extracts and validates identity fields from uploaded documents (Aadhaar, PAN, passport). Flags anomalies and populates the user profile for compliance.',
    tech: 'Claude vision + document classification',
  },
]

export default function AiPage() {
  return (
    <div className="max-w-4xl mx-auto space-y-12">
      <section>
        <h1 className="text-3xl font-bold text-gray-900 mb-4">AI Platform</h1>
        <p className="text-gray-600 leading-relaxed">
          The AI Platform (port 8091) provides four capabilities: fraud copilot via RAG, error resolution
          explanations, autonomous incident triage, and KYC OCR. All inference goes through a multi-provider
          fallback chain to guarantee availability.
        </p>
      </section>

      {/* Capability cards */}
      <section>
        <h2 className="text-2xl font-semibold text-gray-900 mb-4">Capabilities</h2>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          {AI_CAPABILITIES.map(({ icon, title, color, desc, tech }) => (
            <div key={title} className={`rounded-xl border p-5 ${color}`}>
              <div className="text-2xl mb-2">{icon}</div>
              <h3 className="font-semibold text-gray-900 mb-1">{title}</h3>
              <p className="text-sm text-gray-600 mb-3">{desc}</p>
              <span className="text-xs bg-white/70 border border-gray-200 text-gray-600 px-2 py-1 rounded-full font-mono">
                {tech}
              </span>
            </div>
          ))}
        </div>
      </section>

      {/* RAG Pipeline */}
      <section>
        <h2 className="text-2xl font-semibold text-gray-900 mb-4">RAG Pipeline</h2>
        <p className="text-gray-600 mb-4">
          Retrieval-Augmented Generation ensures every AI response is grounded in retrieved evidence.
          The pipeline embeds the query, performs vector similarity search on the knowledge base, assembles
          a prompt with retrieved context, and streams the explanation back — logging everything to
          ai_audit_log for compliance.
        </p>
        <RagPipelineDemo />
      </section>

      {/* Incident Triage */}
      <section>
        <h2 className="text-2xl font-semibold text-gray-900 mb-4">Incident Triage Agent</h2>
        <p className="text-gray-600 mb-4">
          An agentic ReAct loop that autonomously diagnoses and resolves production incidents. The agent
          alternates between reasoning (thinking about what it knows) and acting (calling tools like
          readLogs, queryMetrics, restartDeployment). Watch it triage a real saga failure spike below.
        </p>
        <IncidentTriageDemo />
      </section>

      {/* Fallback chain */}
      <section>
        <h2 className="text-2xl font-semibold text-gray-900 mb-4">Provider Fallback Chain</h2>
        <p className="text-gray-600 mb-4">
          The AI Platform automatically falls back across providers if the primary fails, ensuring
          high availability without manual intervention.
        </p>
        <div className="rounded-xl border border-gray-100 bg-white divide-y divide-gray-50">
          {[
            { provider: 'OpenRouter', model: 'claude-sonnet-4.5 (via API)', priority: 1, color: 'text-rose-600 bg-rose-50' },
            { provider: 'Groq', model: 'llama-3.3-70b-versatile', priority: 2, color: 'text-orange-600 bg-orange-50' },
            { provider: 'Gemini', model: 'gemini-1.5-flash', priority: 3, color: 'text-blue-600 bg-blue-50' },
          ].map(({ provider, model, priority, color }) => (
            <div key={provider} className="flex items-center gap-4 px-5 py-4">
              <span className={`w-6 h-6 rounded-full flex items-center justify-center text-xs font-bold shrink-0 ${color}`}>
                {priority}
              </span>
              <div className="flex-1">
                <p className="font-semibold text-gray-900">{provider}</p>
                <p className="text-xs font-mono text-gray-400">{model}</p>
              </div>
              <span className="text-xs text-gray-400">{priority === 1 ? 'Primary' : priority === 2 ? 'Fallback 1' : 'Fallback 2'}</span>
            </div>
          ))}
        </div>
        <div className="mt-3 rounded-xl bg-gray-50 border border-gray-100 px-5 py-4">
          <p className="text-xs text-gray-500">
            Fallback is automatic: if a provider returns a 429, 500, or timeout, the next provider in the
            chain is tried immediately. Metrics track per-provider success rates in Grafana.
          </p>
        </div>
      </section>
    </div>
  )
}
