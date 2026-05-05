import { clsx } from 'clsx'
import type { TransactionStatus } from '@aegispay/shared-types'
import { statusColors } from '../tokens/index.js'

interface BadgeProps {
  status: TransactionStatus
  className?: string
}

const statusLabels: Record<TransactionStatus, string> = {
  INITIATED: 'Initiated',
  RESERVED: 'Reserved',
  RISK_CLEARED: 'Risk Cleared',
  PROCESSING: 'Processing',
  COMPLETED: 'Completed',
  FAILED: 'Failed',
  ROLLED_BACK: 'Rolled Back',
}

export function AegisBadge({ status, className }: BadgeProps) {
  const colors = statusColors[status]
  return (
    <span
      className={clsx(
        'inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium',
        colors.bg,
        colors.text,
        className,
      )}
      aria-label={`Status: ${statusLabels[status]}`}
    >
      <span className={clsx('h-1.5 w-1.5 rounded-full', colors.dot)} aria-hidden />
      {statusLabels[status]}
    </span>
  )
}
