// FILE: apps/web/app/docs/flows/page.tsx
import dynamic from 'next/dynamic'

const TransactionFlowDemo = dynamic(
  () => import('../_components/TransactionFlowDemo'),
  { ssr: false, loading: () => <div className="animate-pulse bg-gray-100 rounded-xl h-64" /> }
)

const SagaStateMachineDemo = dynamic(
  () => import('../_components/SagaStateMachineDemo'),
  { ssr: false, loading: () => <div className="animate-pulse bg-gray-100 rounded-xl h-64" /> }
)

const STATE_MACHINE = [
  { state: 'PENDING', trigger: 'Transaction Service creates record', next: 'Outbox publishes transaction.initiated' },
  { state: 'RESERVED', trigger: 'Ledger reserves payer balance', next: 'Publishes balance.reserved' },
  { state: 'RISK_CLEARED', trigger: 'Risk Engine issues ALLOW', next: 'Publishes risk.assessed' },
  { state: 'PROCESSING', trigger: 'Orchestrator calls Stripe', next: 'Awaits payment confirmation' },
  { state: 'COMPLETED', trigger: 'Ledger commits double-entry', next: 'Publishes transaction.completed' },
  { state: 'FAILED', trigger: 'Any saga step fails', next: 'Compensations run, publishes transaction.failed' },
]

const FAILURE_TABLE = [
  { failure: 'Gateway crash before tx write', compensate: 'Idempotency key: client retries safely', result: 'No duplicate' },
  { failure: 'Transaction Svc crash after INSERT', compensate: 'Outbox not written → no event → saga never starts', result: 'DB rollback, clean state' },
  { failure: 'Outbox relay crash mid-publish', compensate: 'Restart re-reads unpublished events, consumer idempotency key deduplicates', result: 'Exactly-once' },
  { failure: 'Risk Engine: BLOCK decision', compensate: 'Saga triggers compensation: release reserved balance', result: 'FAILED + notification' },
  { failure: 'Stripe API timeout', compensate: 'Retry with same idempotency key, then compensation on retry exhaustion', result: 'Funds released' },
  { failure: 'Ledger commit fails', compensate: 'Compensate: credit Stripe refund, release reserved balance', result: 'Funds returned to payer' },
]

export default function FlowsPage() {
  return (
    <div className="max-w-4xl mx-auto space-y-12">
      <section>
        <h1 className="text-3xl font-bold text-gray-900 mb-4">Transaction &amp; Saga Flow</h1>
        <p className="text-gray-600 leading-relaxed">
          Every payment flows through a distributed saga — a sequence of steps where each step can be
          compensated (rolled back) if a subsequent step fails. This guarantees money is never lost even
          when individual services crash mid-transaction.
        </p>
      </section>

      {/* Flow demo */}
      <section>
        <h2 className="text-2xl font-semibold text-gray-900 mb-4">Step-by-Step Flow</h2>
        <TransactionFlowDemo />
      </section>

      {/* State machine explanation */}
      <section>
        <h2 className="text-2xl font-semibold text-gray-900 mb-4">Transaction State Machine</h2>
        <div className="rounded-xl border border-gray-100 shadow-sm overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 border-b border-gray-100">
              <tr>
                {['State', 'Entry Trigger', 'Exit Action'].map((h) => (
                  <th key={h} className="text-left px-4 py-3 text-gray-500 font-medium">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50 bg-white">
              {STATE_MACHINE.map((row) => (
                <tr key={row.state} className="hover:bg-gray-50">
                  <td className="px-4 py-3">
                    <span className="font-mono text-xs bg-gray-800 text-gray-100 px-2 py-1 rounded">
                      {row.state}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-gray-600">{row.trigger}</td>
                  <td className="px-4 py-3 text-gray-500 text-xs">{row.next}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      {/* Saga state machine visual */}
      <section>
        <h2 className="text-2xl font-semibold text-gray-900 mb-4">Saga State Machine</h2>
        <SagaStateMachineDemo />
      </section>

      {/* Failure recovery */}
      <section>
        <h2 className="text-2xl font-semibold text-gray-900 mb-4">Failure Recovery</h2>
        <div className="rounded-xl border border-gray-100 shadow-sm overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 border-b border-gray-100">
              <tr>
                {['Failure Point', 'Compensation Mechanism', 'Outcome'].map((h) => (
                  <th key={h} className="text-left px-4 py-3 text-gray-500 font-medium whitespace-nowrap">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50 bg-white">
              {FAILURE_TABLE.map((row, i) => (
                <tr key={i} className="hover:bg-gray-50">
                  <td className="px-4 py-3 text-red-600 font-medium">{row.failure}</td>
                  <td className="px-4 py-3 text-gray-600">{row.compensate}</td>
                  <td className="px-4 py-3">
                    <span className="text-green-600 bg-green-50 px-2 py-0.5 rounded text-xs font-medium">
                      {row.result}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  )
}
