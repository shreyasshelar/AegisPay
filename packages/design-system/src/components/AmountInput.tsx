'use client'

import { clsx } from 'clsx'
import { forwardRef, useState, type InputHTMLAttributes } from 'react'

interface AmountInputProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'onChange'> {
  currency?: string
  onChange?: (value: string) => void
  error?: string
}

export const AmountInput = forwardRef<HTMLInputElement, AmountInputProps>(
  ({ currency = 'INR', onChange, error, className, value, ...props }, ref) => {
    const [focused, setFocused] = useState(false)

    const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
      // Allow only digits and a single decimal point with up to 2 decimal places
      const raw = e.target.value.replace(/[^\d.]/g, '')
      const parts = raw.split('.')
      const sanitised =
        parts.length > 2
          ? `${parts[0]}.${parts.slice(1).join('')}`
          : parts[1] !== undefined && parts[1].length > 2
            ? `${parts[0]}.${parts[1].slice(0, 2)}`
            : raw
      onChange?.(sanitised)
    }

    const currencySymbol: Record<string, string> = {
      INR: '₹',
      USD: '$',
      EUR: '€',
      GBP: '£',
    }

    return (
      <div className="w-full">
        <div
          className={clsx(
            'relative flex items-center rounded-lg border bg-white transition-all',
            focused
              ? 'border-[#1A56DB] ring-2 ring-[#1A56DB]/20'
              : 'border-neutral-300 hover:border-neutral-400',
            error && 'border-[#E02424] ring-2 ring-[#E02424]/20',
          )}
        >
          <span className="select-none pl-4 text-xl font-semibold text-neutral-500">
            {currencySymbol[currency] ?? currency}
          </span>
          <input
            ref={ref}
            type="text"
            inputMode="decimal"
            pattern="[0-9]*\.?[0-9]{0,2}"
            className={clsx(
              'h-14 flex-1 bg-transparent px-2 font-mono text-2xl font-bold text-neutral-900 outline-none placeholder:text-neutral-300',
              className,
            )}
            placeholder="0.00"
            value={value}
            onChange={handleChange}
            onFocus={() => setFocused(true)}
            onBlur={() => setFocused(false)}
            aria-label={`Amount in ${currency}`}
            aria-invalid={!!error}
            {...props}
          />
          <span className="select-none pr-4 text-sm font-medium text-neutral-400">{currency}</span>
        </div>
        {error && (
          <p className="mt-1.5 text-sm text-[#E02424]" role="alert">
            {error}
          </p>
        )}
      </div>
    )
  },
)
AmountInput.displayName = 'AmountInput'
