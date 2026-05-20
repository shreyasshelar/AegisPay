'use client'

import { useState, useEffect, useRef } from 'react'
import { useMutation } from '@tanstack/react-query'
import { toast } from 'sonner'
import {
  Stethoscope,
  Loader2,
  Sparkles,
  Terminal,
  History,
  Trash2,
  AlertTriangle,
  ChevronDown,
  ChevronUp,
  Clock,
} from 'lucide-react'
import { useApiClient } from '@aegispay/api-client'
import { Header } from '@/components/header'

// ── Session history ──────────────────────────────────────────────────────────

interface TriageSession {
  id:          string
  serviceName: string
  description: string
  analysis:    string
  degraded:    boolean
  timestamp:   Date
}

// ── Component ─────────────────────────────────────────────────────────────────

export function TriageClient({
  prefillTxId,
  prefillService,
}: {
  prefillTxId?:    string
  prefillService?: string
}) {
  const { ai } = useApiClient()

  // Form state
  const [serviceName,  setServiceName]  = useState(prefillService ?? '')
  const [description,  setDescription]  = useState(
    prefillTxId
      ? `Transaction ${prefillTxId} failed. Investigate root cause and recommend mitigation.`
      : '',
  )

  // Session history (in-memory; persists for the tab session)
  const [sessions,        setSessions]        = useState<TriageSession[]>([])
  const [expandedSession, setExpandedSession] = useState<string | null>(null)

  const reportRef = useRef<HTMLDivElement>(null)

  // Prefill from query params once on mount
  useEffect(() => {
    if (prefillTxId && !description) {
      setDescription(
        `Transaction ${prefillTxId} failed. Investigate root cause and recommend mitigation.`,
      )
    }
    if (prefillService && !serviceName) {
      setServiceName(prefillService)
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const { mutate: runTriage, isPending } = useMutation({
    mutationFn: () => ai.triageIncident(serviceName, description),
    onSuccess: (analysis: string) => {
      const session: TriageSession = {
        id:          crypto.randomUUID(),
        serviceName,
        description,
        analysis,
        degraded:    analysis.startsWith('⚠'),
        timestamp:   new Date(),
      }
      setSessions(prev => [session, ...prev])
      setExpandedSession(session.id)
      toast.success(session.degraded ? 'Triage completed (degraded mode)' : 'Triage complete')
      // Scroll to report
      setTimeout(() => reportRef.current?.scrollIntoView({ behavior: 'smooth' }), 100)
    },
    onError: (err) => {
      toast.error('Triage failed', {
        description: err instanceof Error ? err.message : 'Unknown error',
      })
    },
  })

  const canTriage = serviceName.trim().length > 0 && description.trim().length > 0

  return (
    <>
      <Header
        title="AI Triage Agent"
        subtitle="ADMIN-only — agentic AI reads logs, metrics & deployment history to root-cause incidents"
      />

      <div className="p-6 max-w-4xl space-y-5 animate-fade-in">

        {/* ── Input form ──────────────────────────────────────────────── */}
        <div className="rounded-xl bg-white p-6 shadow-sm ring-1 ring-slate-200 space-y-4">
          <h3 className="text-sm font-semibold text-slate-900 flex items-center gap-2">
            <Terminal className="h-4 w-4 text-slate-500" />
            New Triage Request
          </h3>

          {prefillTxId && (
            <div className="flex items-start gap-2 rounded-lg bg-amber-50 border border-amber-200 px-3 py-2 text-sm text-amber-800">
              <AlertTriangle className="h-4 w-4 mt-0.5 shrink-0" />
              <span>
                Pre-filled from transaction <code className="font-mono">{prefillTxId}</code>.
                Adjust the description as needed.
              </span>
            </div>
          )}

          <div>
            <label className="mb-1.5 block text-sm font-medium text-slate-700">
              Affected Service
            </label>
            <input
              value={serviceName}
              onChange={(e) => setServiceName(e.target.value)}
              placeholder="e.g. transaction-service, payment-orchestrator"
              className="block w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-primary-500 focus:outline-none focus:ring-2 focus:ring-primary-200"
            />
          </div>

          <div>
            <label className="mb-1.5 block text-sm font-medium text-slate-700">
              Incident Description
            </label>
            <textarea
              rows={4}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Describe the symptoms, error messages, timeline, or paste the transaction ID…"
              className="block w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-primary-500 focus:outline-none focus:ring-2 focus:ring-primary-200 resize-none"
            />
          </div>

          <button
            onClick={() => runTriage()}
            disabled={isPending || !canTriage}
            className="flex items-center gap-2 rounded-xl bg-primary-600 px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-primary-700 disabled:opacity-50"
          >
            {isPending ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              <Sparkles className="h-4 w-4" />
            )}
            {isPending ? 'Agent is investigating…' : 'Run AI Triage'}
          </button>
        </div>

        {/* ── Session history ──────────────────────────────────────────── */}
        {sessions.length > 0 && (
          <div ref={reportRef} className="space-y-3">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-semibold text-slate-900 flex items-center gap-2">
                <History className="h-4 w-4 text-slate-500" />
                Session History ({sessions.length})
              </h3>
              <button
                onClick={() => setSessions([])}
                className="flex items-center gap-1 text-xs text-slate-400 hover:text-red-500 transition"
              >
                <Trash2 className="h-3 w-3" />
                Clear all
              </button>
            </div>

            {sessions.map((session) => (
              <div
                key={session.id}
                className="rounded-xl bg-white shadow-sm ring-1 ring-slate-200 overflow-hidden"
              >
                {/* Session header */}
                <button
                  onClick={() =>
                    setExpandedSession(prev => prev === session.id ? null : session.id)
                  }
                  className="w-full flex items-center justify-between px-5 py-3.5 text-left hover:bg-slate-50 transition"
                >
                  <div className="flex items-center gap-3 min-w-0">
                    <Stethoscope
                      className={`h-4 w-4 shrink-0 ${
                        session.degraded ? 'text-amber-500' : 'text-primary-500'
                      }`}
                    />
                    <div className="min-w-0">
                      <p className="text-sm font-semibold text-slate-900 truncate">
                        {session.serviceName}
                      </p>
                      <p className="text-xs text-slate-400 truncate max-w-[400px]">
                        {session.description}
                      </p>
                    </div>
                  </div>
                  <div className="flex items-center gap-2 shrink-0 ml-4">
                    {session.degraded && (
                      <span className="rounded-full bg-amber-100 px-2 py-0.5 text-[10px] font-semibold text-amber-700">
                        DEGRADED
                      </span>
                    )}
                    <span className="flex items-center gap-1 text-[11px] text-slate-400">
                      <Clock className="h-3 w-3" />
                      {session.timestamp.toLocaleTimeString()}
                    </span>
                    {expandedSession === session.id ? (
                      <ChevronUp className="h-4 w-4 text-slate-400" />
                    ) : (
                      <ChevronDown className="h-4 w-4 text-slate-400" />
                    )}
                  </div>
                </button>

                {/* Session report */}
                {expandedSession === session.id && (
                  <div className="border-t border-slate-100 px-5 py-4">
                    <pre className="overflow-x-auto rounded-lg bg-slate-900 p-5 text-sm text-green-300 whitespace-pre-wrap leading-relaxed">
                      {session.analysis}
                    </pre>
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
    </>
  )
}
