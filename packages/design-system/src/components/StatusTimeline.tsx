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

interface StatusTimelineProps {
  currentStatus: TransactionStatus
  className?: string
}

export function AegisStatusTimeline({ currentStatus, className }: StatusTimelineProps) {
  const currentIndex = STATUS_ORDER[currentStatus] ?? 0
  const isFailed = currentStatus === 'FAILED' || currentStatus === 'ROLLED_BACK'

  return (
    <ol className={clsx('space-y-0', className)} aria-label="Transaction progress">
      {STEPS.map((step, idx) => {
        const isCompleted = !isFailed && idx < currentIndex
        const isCurrent = !isFailed && idx === currentIndex
        const isPending = !isFailed && idx > currentIndex

        return (
          <li key={step.status} className="relative flex gap-4">
            {/* Connector line */}
            {idx < STEPS.length - 1 && (
              <span
                aria-hidden
                className={clsx(
                  'absolute left-5 top-10 h-full w-0.5',
                  isCompleted ? 'bg-[#0E9F6E]' : 'bg-neutral-200',
                )}
              />
            )}

            {/* Icon */}
            <div className="relative z-10 flex h-10 w-10 shrink-0 items-center justify-center rounded-full">
              {isCompleted && (
                <CheckCircle2 className="h-8 w-8 text-[#0E9F6E]" aria-hidden />
              )}
              {isCurrent && !isFailed && (
                <div className="flex h-8 w-8 items-center justify-center rounded-full bg-[#1A56DB]">
                  <Loader2 className="h-4 w-4 animate-spin text-white" aria-hidden />
                </div>
              )}
              {isPending && (
                <div className="flex h-8 w-8 items-center justify-center rounded-full border-2 border-neutral-200 bg-white">
                  <Clock className="h-4 w-4 text-neutral-300" aria-hidden />
                </div>
              )}
              {isFailed && idx === 0 && (
                <XCircle className="h-8 w-8 text-[#E02424]" aria-hidden />
              )}
            </div>

            {/* Text */}
            <div className="pb-8 pt-1.5">
              <p
                className={clsx(
                  'text-sm font-semibold',
                  isCompleted && 'text-[#046C4E]',
                  isCurrent && 'text-[#1A56DB]',
                  isPending && 'text-neutral-400',
                  isFailed && 'text-[#C81E1E]',
                )}
              >
                {step.label}
              </p>
              <p className="text-xs text-neutral-500">{step.description}</p>
            </div>
          </li>
        )
      })}

      {isFailed && (
        <li className="flex gap-4">
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full">
            <XCircle className="h-8 w-8 text-[#E02424]" aria-hidden />
          </div>
          <div className="pt-1.5">
            <p className="text-sm font-semibold text-[#C81E1E]">
              {currentStatus === 'ROLLED_BACK' ? 'Rolled Back' : 'Failed'}
            </p>
            <p className="text-xs text-neutral-500">
              {currentStatus === 'ROLLED_BACK'
                ? 'Payment reversed — funds returned to your account'
                : 'Payment could not be completed'}
            </p>
          </div>
        </li>
      )}
    </ol>
  )
}
