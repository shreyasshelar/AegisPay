// FILE: apps/web/app/docs/page.tsx
import Link from 'next/link'
import {
  Layers,
  GitBranch,
  Puzzle,
  Brain,
  Server,
  Database,
  CheckCircle2,
  Cpu,
  Zap,
  ShieldCheck,
  BarChart3,
} from 'lucide-react'

const STATS = [
  { value: '10', label: 'Microservices', icon: Cpu },
  { value: '3', label: 'Kafka topics → saga', icon: Zap },
  { value: 'Zero', label: 'Dual-spend', icon: ShieldCheck },
  { value: 'AI', label: 'Powered triage', icon: Brain },
]

const SECTION_CARDS = [
  {
    href: '/docs/architecture',
    icon: Layers,
    color: 'text-blue-500',
    bg: 'bg-blue-50',
    title: 'Architecture',
    description: 'System design, tech stack, service topology, and data stores.',
  },
  {
    href: '/docs/flows',
    icon: GitBranch,
    color: 'text-purple-500',
    bg: 'bg-purple-50',
    title: 'Transaction Flow',
    description: 'End-to-end saga walkthrough from submit to ledger commit.',
  },
  {
    href: '/docs/patterns',
    icon: Puzzle,
    color: 'text-green-500',
    bg: 'bg-green-50',
    title: 'Patterns',
    description: 'Outbox, double-entry ledger, CQRS, and idempotency deep-dives.',
  },
  {
    href: '/docs/ai',
    icon: Brain,
    color: 'text-amber-500',
    bg: 'bg-amber-50',
    title: 'AI Platform',
    description: 'RAG pipeline, fraud copilot, incident triage, and model fallback.',
  },
  {
    href: '/docs/infrastructure',
    icon: Server,
    color: 'text-red-500',
    bg: 'bg-red-50',
    title: 'Infrastructure',
    description: 'Kubernetes, CI/CD, secrets management, and observability.',
  },
  {
    href: '/docs/services',
    icon: Database,
    color: 'text-gray-600',
    bg: 'bg-gray-100',
    title: 'Services',
    description: 'Full reference for all 10 services: ports, DBs, and key patterns.',
  },
]

const GUARANTEES = [
  {
    icon: ShieldCheck,
    color: 'text-red-500',
    title: 'No double-spend',
    detail: 'Optimistic locking + FOR UPDATE on balance read prevents concurrent deductions.',
  },
  {
    icon: Zap,
    color: 'text-blue-500',
    title: 'No lost events',
    detail: 'Outbox Pattern writes domain entity + event in one atomic DB transaction.',
  },
  {
    icon: BarChart3,
    color: 'text-green-500',
    title: 'No lost money',
    detail: 'Double-entry bookkeeping — SUM(all_entries) = 0 is enforced at all times.',
  },
  {
    icon: CheckCircle2,
    color: 'text-purple-500',
    title: 'No silent failures',
    detail: 'Every failure carries a failureCode + AI explanation + user notification.',
  },
  {
    icon: Layers,
    color: 'text-amber-500',
    title: 'No data drift',
    detail: 'CQRS read models updated by the same Kafka events that drive the state machine.',
  },
]

export default function DocsPage() {
  return (
    <div className="max-w-4xl mx-auto space-y-16">
      {/* Hero */}
      <section className="space-y-4">
        <div className="inline-flex items-center gap-2 rounded-full bg-blue-50 px-3 py-1 text-xs font-medium text-blue-600 border border-blue-100">
          Production-grade · Event-driven · Fintech
        </div>
        <h1 className="text-4xl font-bold text-gray-900 leading-tight">
          AegisPay Developer Docs
        </h1>
        <p className="text-lg text-gray-500 max-w-2xl">
          Production-grade event-driven fintech platform.{' '}
          <span className="font-medium text-gray-700">Money never lost. Never doubled.</span>
        </p>
      </section>

      {/* Stats */}
      <section className="grid grid-cols-2 sm:grid-cols-4 gap-4">
        {STATS.map(({ value, label, icon: Icon }) => (
          <div
            key={label}
            className="rounded-xl border border-gray-100 shadow-sm bg-white px-5 py-4 flex flex-col gap-2"
          >
            <Icon size={18} className="text-blue-500" />
            <p className="text-2xl font-bold text-gray-900">{value}</p>
            <p className="text-xs text-gray-500">{label}</p>
          </div>
        ))}
      </section>

      {/* Section cards */}
      <section className="space-y-4">
        <h2 className="text-2xl font-semibold text-gray-900">Explore the Docs</h2>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {SECTION_CARDS.map(({ href, icon: Icon, color, bg, title, description }) => (
            <Link
              key={href}
              href={href}
              className="rounded-xl border border-gray-100 shadow-sm bg-white p-5 flex flex-col gap-3 hover:shadow-md hover:border-gray-200 transition-all group"
            >
              <div className={`w-9 h-9 rounded-lg ${bg} flex items-center justify-center`}>
                <Icon size={18} className={color} />
              </div>
              <div>
                <p className="font-semibold text-gray-900 group-hover:text-blue-600 transition-colors">
                  {title}
                </p>
                <p className="text-sm text-gray-500 mt-1">{description}</p>
              </div>
            </Link>
          ))}
        </div>
      </section>

      {/* Core Guarantees */}
      <section className="space-y-4">
        <h2 className="text-2xl font-semibold text-gray-900 mb-4">Core Guarantees</h2>
        <div className="rounded-xl border border-gray-100 shadow-sm bg-white divide-y divide-gray-50">
          {GUARANTEES.map(({ icon: Icon, color, title, detail }) => (
            <div key={title} className="flex items-start gap-4 px-6 py-5">
              <Icon size={20} className={`mt-0.5 shrink-0 ${color}`} />
              <div>
                <p className="font-semibold text-gray-900">{title}</p>
                <p className="text-sm text-gray-500 mt-0.5">{detail}</p>
              </div>
            </div>
          ))}
        </div>
      </section>
    </div>
  )
}
