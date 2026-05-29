import { clsx } from 'clsx'
import { ArrowUpRight, ArrowDownLeft, XCircle } from 'lucide-react'
import type { TransactionSummary } from '@aegispay/shared-types'
import { AegisBadge } from './Badge'

interface TransactionRowProps {
  transaction: TransactionSummary
  direction?: 'sent' | 'received'
  payeeName?: string
  onClick?: () => void
  className?: string
}

export function AegisTransactionRow({
  transaction,
  direction,
  payeeName,
  onClick,
  className,
}: TransactionRowProps) {
  // Prefer the backend-provided direction field; fall back to the prop; default to 'sent'.
  const resolvedDirection: 'sent' | 'received' =
    transaction.direction === 'RECEIVED' ? 'received' :
    transaction.direction === 'SENT'     ? 'sent'     :
    (direction ?? 'sent')

  const isFailed = transaction.status === 'FAILED' || transaction.status === 'ROLLED_BACK'

  // Always format as absolute value — the arrow / X icon already conveys direction & outcome.
  const formattedAmount = new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: transaction.currency,
    minimumFractionDigits: 2,
  }).format(Math.abs(parseFloat(transaction.amount)))

  const formattedDate = new Intl.DateTimeFormat('en-IN', {
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(transaction.initiatedAt))

  return (
    <li
      className={clsx(
        'flex cursor-pointer items-center gap-4 rounded-lg px-4 py-3 transition-colors hover:bg-neutral-50 active:bg-neutral-100',
        className,
      )}
      onClick={onClick}
      role={onClick ? 'button' : undefined}
      tabIndex={onClick ? 0 : undefined}
      onKeyDown={onClick ? (e) => e.key === 'Enter' && onClick() : undefined}
      aria-label={`${resolvedDirection === 'sent' ? 'Sent' : 'Received'} ${formattedAmount} — ${transaction.status}`}
    >
      {/* Direction / outcome icon */}
      <div
        className={clsx(
          'flex h-10 w-10 shrink-0 items-center justify-center rounded-full',
          isFailed
            ? 'bg-neutral-100'
            : resolvedDirection === 'sent' ? 'bg-red-50' : 'bg-green-50',
        )}
        aria-hidden
      >
        {isFailed ? (
          <XCircle className="h-5 w-5 text-neutral-400" />
        ) : resolvedDirection === 'sent' ? (
          <ArrowUpRight className="h-5 w-5 text-[#E02424]" />
        ) : (
          <ArrowDownLeft className="h-5 w-5 text-[#0E9F6E]" />
        )}
      </div>

      {/* Info */}
      <div className="min-w-0 flex-1">
        <p className={clsx(
          'truncate text-sm font-medium',
          isFailed ? 'text-neutral-400' : 'text-neutral-900',
        )}>
          {payeeName ?? `ID: ${transaction.payeeId.slice(0, 8)}…`}
        </p>
        <p className="text-xs text-neutral-500">{formattedDate}</p>
      </div>

      {/* Amount + status */}
      <div className="flex flex-col items-end gap-1">
        <span
          className={clsx(
            'font-mono text-sm font-semibold',
            isFailed
              ? 'text-neutral-400 line-through'
              : resolvedDirection === 'sent' ? 'text-neutral-900' : 'text-[#046C4E]',
          )}
        >
          {formattedAmount}
        </span>
        <AegisBadge status={transaction.status} />
      </div>
    </li>
  )
}
