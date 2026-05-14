'use client'

import { clsx } from 'clsx'
import { CheckCircle2, XCircle, Clock, Loader2 } from 'lucide-react'
import type { TransactionStatus } from '@aegispay/shared-types'

const STEPS: { status: TransactionStatus; label: string; description: string }[] = [
  { status: 'INITIATED',    label: 'Initiated',    description: 'Payment request received' },
  { status: 'RESERVED',     label: 'Reserved',     description: 'Funds reserved in your account' },
  { status: 'RISK_CLEARED', label: 'Risk Cleared', description: 'Security check passed' },
  { status: 'PROCESSING',   label: 'Processing',   description: 'Sending to recipient bank' },
  { status: 'COMPLETED',    label: 'Completed',    description: 'Payment delivered successfully' },
]

const STATUS_ORDER: Record<TransactionStatus, number> = {
  INITIATED:    0,
  RESERVED:     1,
  RISK_CLEARED: 2,
  PROCESSING:   3,
  COMPLETED:    4,
  FAILED:      -1,
  ROLLED_BACK: -1,
}

/**
 * Maps the failure/rollback reason string (set by the orchestrator) to the
 * pipeline step index where the failure occurred.
 *
 *  0 = INITIATED   (unknown / default)
 *  1 = RESERVED    (balance reserve failed)
 *  2 = RISK_CLEARED (risk rejected)
 *  3 = PROCESSING  (payment gateway failed)
 *  4 = COMPLETED   (commit failed — rare)
 */
function failedStepIndex(failureReason?: string | null): number {
  if (!failureReason) return 0
  const r = failureReason.toLowerCase()
  if (
    r.includes('balance reserve') ||
    r.includes('insufficient') ||
    r.includes('account_not_found') ||
    r.includes('reserve_balance')
  ) return 1
  if (r.includes('risk') || r.includes('assess_risk')) return 2
  if (
    r.includes('payment gateway') ||
    r.includes('stripe') ||
    r.includes('process_payment')
  ) return 3
  if (r.includes('commit')) return 4
  return 0
}

interface StatusTimelineProps {
  currentStatus: TransactionStatus
  failureReason?: string | null
  className?: string
}

export function AegisStatusTimeline({
  currentStatus,
  failureReason,
  className,
}: StatusTimelineProps) {
  const currentIndex = STATUS_ORDER[currentStatus] ?? 0
  const isFailed     = currentStatus === 'FAILED'
  const isTerminalOk = currentStatus === 'COMPLETED'
  const failIdx      = isFailed ? failedStepIndex(failureReason) : -1

  return (
    <ol className={clsx('space-y-0', className)} aria-label="Transaction progress">
      {STEPS.map((step, idx) => {
        // ── Derive per-step state ─────────────────────────────────────────────
        let isCompleted: boolean
        let isCurrent: boolean
        let isPending: boolean
        let isFailedStep: boolean

        if (isFailed) {
          isCompleted  = idx < failIdx
          isFailedStep = idx === failIdx
          isCurrent    = false
          isPending    = idx > failIdx
        } else if (isTerminalOk) {
          isCompleted  = true   // all 5 steps green on COMPLETED
          isFailedStep = false
          isCurrent    = false
          isPending    = false
        } else {
          isCompleted  = idx < currentIndex
          isFailedStep = false
          isCurrent    = idx === currentIndex
          isPending    = idx > currentIndex
        }

        return (
          <li key={step.status} className="relative flex gap-4">
            {/* Connector line */}
            {idx < STEPS.length - 1 && (
              <span
                aria-hidden
                className={clsx(
                  'absolute left-5 top-10 h-full w-0.5',
                  isCompleted                    ? 'bg-[#0E9F6E]'
                  : isFailedStep                 ? 'bg-[#E02424]'
                  :                                'bg-neutral-200',
                )}
              />
            )}

            {/* Icon */}
            <div className="relative z-10 flex h-10 w-10 shrink-0 items-center justify-center rounded-full">
              {isCompleted && (
                <CheckCircle2 className="h-8 w-8 text-[#0E9F6E]" aria-hidden />
              )}
              {isCurrent && (
                <div className="flex h-8 w-8 items-center justify-center rounded-full bg-[#1A56DB]">
                  <Loader2 className="h-4 w-4 animate-spin text-white" aria-hidden />
                </div>
              )}
              {isFailedStep && (
                <XCircle className="h-8 w-8 text-[#E02424]" aria-hidden />
              )}
              {isPending && (
                <div className="flex h-8 w-8 items-center justify-center rounded-full border-2 border-neutral-200 bg-white">
                  <Clock className="h-4 w-4 text-neutral-300" aria-hidden />
                </div>
              )}
            </div>

            {/* Text */}
            <div className="pb-8 pt-1.5">
              <p
                className={clsx(
                  'text-sm font-semibold',
                  isCompleted  && 'text-[#046C4E]',
                  isCurrent    && 'text-[#1A56DB]',
                  isFailedStep && 'text-[#C81E1E]',
                  isPending    && 'text-neutral-400',
                )}
              >
                {step.label}
              </p>
              <p className="text-xs text-neutral-500">
                {isFailedStep && isFailed ? 'Failed at this step' : step.description}
              </p>
            </div>
          </li>
        )
      })}
    </ol>
  )
}
