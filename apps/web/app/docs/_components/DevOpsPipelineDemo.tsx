// FILE: apps/web/app/docs/_components/DevOpsPipelineDemo.tsx
'use client'

import { useState } from 'react'

type CiStep = {
  id: number
  icon: string
  label: string
  detail: string
  duration: string
  color: string
}

const CI_STEPS: CiStep[] = [
  {
    id: 1,
    icon: '📤',
    label: 'Push / PR',
    detail: 'Developer pushes to feature branch or opens PR to main',
    duration: '—',
    color: 'bg-blue-500',
  },
  {
    id: 2,
    icon: '🔍',
    label: 'Detect Changes',
    detail: 'GitHub Actions detects which service(s) changed using path filters',
    duration: '~5s',
    color: 'bg-indigo-500',
  },
  {
    id: 3,
    icon: '🔨',
    label: 'Build + Test',
    detail: 'Maven build, unit tests, integration tests with TestContainers',
    duration: '~3m',
    color: 'bg-purple-500',
  },
  {
    id: 4,
    icon: '🛡️',
    label: 'Security Scan',
    detail: 'Trivy image scan + CodeQL SAST + OWASP dependency check',
    duration: '~2m',
    color: 'bg-amber-500',
  },
  {
    id: 5,
    icon: '🚀',
    label: 'Deploy (ArgoCD)',
    detail: 'ArgoCD syncs new image tag to dev/prod cluster via GitOps',
    duration: '~1m',
    color: 'bg-green-500',
  },
]

const K8S_SERVICES = [
  { name: 'api-gateway', ns: 'aegispay', replicas: '2/2', cpu: '200m', mem: '512Mi', health: 'Healthy' },
  { name: 'user-service', ns: 'aegispay', replicas: '2/2', cpu: '200m', mem: '512Mi', health: 'Healthy' },
  { name: 'transaction-service', ns: 'aegispay', replicas: '3/3', cpu: '500m', mem: '1Gi', health: 'Healthy' },
  { name: 'ledger-service', ns: 'aegispay', replicas: '2/2', cpu: '300m', mem: '512Mi', health: 'Healthy' },
  { name: 'payment-orchestrator', ns: 'aegispay', replicas: '2/2', cpu: '300m', mem: '512Mi', health: 'Healthy' },
  { name: 'risk-engine', ns: 'aegispay', replicas: '2/2', cpu: '500m', mem: '1Gi', health: 'Healthy' },
  { name: 'notification-service', ns: 'aegispay', replicas: '2/2', cpu: '200m', mem: '512Mi', health: 'Healthy' },
  { name: 'reconciliation-service', ns: 'aegispay', replicas: '1/1', cpu: '200m', mem: '512Mi', health: 'Healthy' },
  { name: 'data-pipeline', ns: 'aegispay', replicas: '2/2', cpu: '300m', mem: '1Gi', health: 'Healthy' },
  { name: 'ai-platform', ns: 'aegispay', replicas: '2/2', cpu: '500m', mem: '2Gi', health: 'Healthy' },
]

const SECURITY_TOOLS = [
  { tool: 'Trivy', type: 'Container Scan', scope: 'All Docker images on every build', trigger: 'CI push', severity: 'CRITICAL + HIGH block' },
  { tool: 'CodeQL', type: 'SAST', scope: 'Java + TypeScript source code', trigger: 'PR to main', severity: 'Critical code paths' },
  { tool: 'OWASP Dep-Check', type: 'Dependency Audit', scope: 'Maven + npm dependencies', trigger: 'Daily + PR', severity: 'CVSS ≥7.0 block' },
  { tool: 'ESO + Vault', type: 'Secrets Management', scope: 'All env secrets via External Secrets Operator → HashiCorp Vault', trigger: 'Runtime', severity: 'Zero plaintext secrets' },
]

const PROMETHEUS_RULES = [
  { rule: 'SagaTimeoutRateHigh', condition: 'saga_timeout_rate > 5%', severity: 'critical', action: 'Page on-call' },
  { rule: 'DlqDepthNonZero', condition: 'kafka_dlq_messages > 0', severity: 'warning', action: 'Alert channel' },
  { rule: 'BalanceNegative', condition: 'ledger_balance < 0', severity: 'critical', action: 'Immediate page' },
  { rule: 'NotificationDeliveryFailureHigh', condition: 'notification_failure_rate > 10%', severity: 'warning', action: 'Alert channel' },
  { rule: 'DataPipelineSinkErrorHigh', condition: 'sink_error_rate > 1%', severity: 'warning', action: 'Alert channel' },
  { rule: 'ReconciliationBreakCountHigh', condition: 'recon_breaks > 0', severity: 'critical', action: 'Page + freeze' },
]

const TABS = ['⚙️ CI/CD', '☸️ Kubernetes', '🛡️ Security', '📊 Observability']

export default function DevOpsPipelineDemo() {
  const [tab, setTab] = useState(0)
  const [selectedCiStep, setSelectedCiStep] = useState<number | null>(null)

  return (
    <div className="rounded-xl border border-gray-100 shadow-sm bg-white overflow-hidden">
      {/* Tabs */}
      <div className="border-b border-gray-100 flex overflow-x-auto">
        {TABS.map((label, i) => (
          <button
            key={i}
            onClick={() => setTab(i)}
            className={`shrink-0 px-5 py-3 text-sm font-medium border-b-2 transition-colors ${
              tab === i
                ? 'border-blue-500 text-blue-600'
                : 'border-transparent text-gray-500 hover:text-gray-700'
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      <div className="p-5">
        {/* CI/CD */}
        {tab === 0 && (
          <div>
            <p className="text-sm text-gray-500 mb-4">Click any step for details</p>
            <div className="flex flex-col sm:flex-row gap-2 items-start sm:items-center">
              {CI_STEPS.map((step, i) => (
                <div key={step.id} className="flex flex-col sm:flex-row items-center gap-2 w-full sm:w-auto">
                  <button
                    onClick={() => setSelectedCiStep(selectedCiStep === step.id ? null : step.id)}
                    className={`w-full sm:w-auto rounded-xl border-2 p-3 text-center transition-all hover:shadow-md ${
                      selectedCiStep === step.id
                        ? 'border-blue-400 bg-blue-50'
                        : 'border-gray-100 bg-white'
                    }`}
                  >
                    <div className={`w-8 h-8 rounded-full ${step.color} flex items-center justify-center text-white text-base mx-auto mb-1`}>
                      <span className="text-sm">{step.icon}</span>
                    </div>
                    <p className="text-xs font-semibold text-gray-800 whitespace-nowrap">{step.label}</p>
                    <p className="text-xs text-gray-400">{step.duration}</p>
                  </button>
                  {i < CI_STEPS.length - 1 && (
                    <div className="hidden sm:block text-gray-300 text-lg">→</div>
                  )}
                </div>
              ))}
            </div>
            {selectedCiStep && (
              <div className="mt-4 rounded-xl bg-blue-50 border border-blue-200 p-4">
                <p className="font-semibold text-blue-800 mb-1">
                  {CI_STEPS.find((s) => s.id === selectedCiStep)?.label}
                </p>
                <p className="text-sm text-blue-700">
                  {CI_STEPS.find((s) => s.id === selectedCiStep)?.detail}
                </p>
              </div>
            )}
          </div>
        )}

        {/* Kubernetes */}
        {tab === 1 && (
          <div className="overflow-x-auto">
            <table className="w-full text-xs">
              <thead className="bg-gray-50 border-b border-gray-100">
                <tr>
                  {['Service', 'Namespace', 'Replicas', 'CPU', 'Memory', 'Status'].map((h) => (
                    <th key={h} className="text-left px-3 py-2 text-gray-500 font-medium whitespace-nowrap">
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {K8S_SERVICES.map((svc) => (
                  <tr key={svc.name} className="hover:bg-gray-50">
                    <td className="px-3 py-2 font-mono text-gray-800 font-medium whitespace-nowrap">{svc.name}</td>
                    <td className="px-3 py-2 text-gray-500">{svc.ns}</td>
                    <td className="px-3 py-2 font-mono text-gray-600">{svc.replicas}</td>
                    <td className="px-3 py-2 text-gray-600">{svc.cpu}</td>
                    <td className="px-3 py-2 text-gray-600">{svc.mem}</td>
                    <td className="px-3 py-2">
                      <span className="text-green-600 bg-green-50 px-1.5 py-0.5 rounded text-xs font-medium">
                        {svc.health}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {/* Security */}
        {tab === 2 && (
          <div className="space-y-3">
            {SECURITY_TOOLS.map((tool) => (
              <div key={tool.tool} className="rounded-xl border border-gray-100 p-4 flex flex-col sm:flex-row gap-3">
                <div className="sm:w-36 shrink-0">
                  <p className="font-bold text-gray-900 text-sm">{tool.tool}</p>
                  <span className="text-xs bg-gray-100 text-gray-600 px-1.5 py-0.5 rounded">{tool.type}</span>
                </div>
                <div className="flex-1 space-y-1">
                  <p className="text-sm text-gray-700">{tool.scope}</p>
                  <div className="flex gap-3 text-xs flex-wrap">
                    <span className="text-gray-400"><span className="font-medium text-gray-600">Trigger:</span> {tool.trigger}</span>
                    <span className="text-red-600 font-medium">{tool.severity}</span>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}

        {/* Observability */}
        {tab === 3 && (
          <div className="space-y-6">
            <div>
              <h4 className="text-sm font-semibold text-gray-700 mb-3">Grafana Instances</h4>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div className="rounded-xl border border-orange-200 bg-orange-50 p-4">
                  <p className="font-semibold text-orange-800 text-sm">kube-prometheus-stack Grafana</p>
                  <p className="text-xs text-orange-600 mt-1">Source: Prometheus</p>
                  <p className="text-xs text-gray-600 mt-2">JVM metrics, Kafka consumer lag, K8s workload health, HTTP error rates, circuit breaker state</p>
                </div>
                <div className="rounded-xl border border-blue-200 bg-blue-50 p-4">
                  <p className="font-semibold text-blue-800 text-sm">AegisPay Grafana (port 3100)</p>
                  <p className="text-xs text-blue-600 mt-1">Source: ClickHouse</p>
                  <p className="text-xs text-gray-600 mt-2">Payment Operations, Fraud Intelligence, SLA & Latency, Reconciliation dashboards</p>
                </div>
              </div>
            </div>
            <div>
              <h4 className="text-sm font-semibold text-gray-700 mb-3">PrometheusRules</h4>
              <div className="overflow-x-auto rounded-xl border border-gray-100">
                <table className="w-full text-xs">
                  <thead className="bg-gray-50 border-b border-gray-100">
                    <tr>
                      {['Rule', 'Condition', 'Severity', 'Action'].map((h) => (
                        <th key={h} className="text-left px-3 py-2 text-gray-500 font-medium whitespace-nowrap">{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-50">
                    {PROMETHEUS_RULES.map((rule) => (
                      <tr key={rule.rule} className="hover:bg-gray-50">
                        <td className="px-3 py-2 font-mono text-gray-800 whitespace-nowrap">{rule.rule}</td>
                        <td className="px-3 py-2 font-mono text-gray-600 whitespace-nowrap">{rule.condition}</td>
                        <td className="px-3 py-2">
                          <span className={`px-1.5 py-0.5 rounded text-xs font-medium ${
                            rule.severity === 'critical'
                              ? 'bg-red-100 text-red-700'
                              : 'bg-amber-100 text-amber-700'
                          }`}>
                            {rule.severity}
                          </span>
                        </td>
                        <td className="px-3 py-2 text-gray-600">{rule.action}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
