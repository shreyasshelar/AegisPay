'use client'

import { clsx } from 'clsx'
import { ChevronDown } from 'lucide-react'
import { forwardRef, useState, type InputHTMLAttributes } from 'react'

const SYMBOLS: Record<string, string> = {
  INR: '₹',
  USD: '$',
  EUR: '€',
  GBP: '£',
}

interface AmountInputProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'onChange'> {
  /** Currently selected currency code (controlled). */
  currency?: string
  /** Available currencies shown in the embedded dropdown.
   *  When omitted (or single entry) the dropdown is hidden. */
  currencies?: readonly string[]
  /** Called when the user picks a different currency. */
  onCurrencyChange?: (currency: string) => void
  /** Called with the sanitised numeric string on every keystroke. */
  onChange?: (value: string) => void
  /** Validation error message. Space is always reserved so layout never shifts. */
  error?: string
}

export const AmountInput = forwardRef<HTMLInputElement, AmountInputProps>(
  (
    {
      currency = 'INR',
      currencies,
      onCurrencyChange,
      onChange,
      error,
      className,
      value,
      ...props
    },
    ref,
  ) => {
    const [focused, setFocused] = useState(false)

    const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
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

    const showDropdown = currencies && currencies.length > 1 && !!onCurrencyChange

    return (
      <div className="w-full">
        {/* ── Unified amount + currency container ── */}
        <div
          className={clsx(
            'relative flex items-center rounded-lg border bg-white transition-all',
            focused
              ? 'border-[#1A56DB] ring-2 ring-[#1A56DB]/20'
              : 'border-neutral-300 hover:border-neutral-400',
            error && 'border-[#E02424] ring-2 ring-[#E02424]/20',
          )}
        >
          {/* Currency symbol — always in sync with selected currency */}
          <span
            className="select-none pl-4 text-xl font-semibold text-neutral-400"
            aria-hidden
          >
            {SYMBOLS[currency] ?? currency}
          </span>

          {/* Numeric input */}
          <input
            ref={ref}
            type="text"
            inputMode="decimal"
            pattern="[0-9]*\.?[0-9]{0,2}"
            className={clsx(
              'h-14 min-w-0 flex-1 bg-transparent px-2 font-mono text-2xl font-bold text-neutral-900 outline-none placeholder:text-neutral-300',
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

          {/* ── Embedded currency selector ── */}
          {showDropdown ? (
            <div className="relative flex shrink-0 items-center border-l border-neutral-200">
              <select
                value={currency}
                onChange={(e) => onCurrencyChange!(e.target.value)}
                onFocus={() => setFocused(true)}
                onBlur={() => setFocused(false)}
                aria-label="Currency"
                className="h-14 cursor-pointer appearance-none bg-transparent pl-3 pr-8 text-sm font-semibold text-neutral-700 outline-none"
              >
                {currencies!.map((c) => (
                  <option key={c} value={c}>
                    {c}
                  </option>
                ))}
              </select>
              <ChevronDown
                className="pointer-events-none absolute right-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-neutral-400"
                aria-hidden
              />
            </div>
          ) : (
            /* Static currency badge when no dropdown needed */
            <span className="select-none pr-4 text-sm font-semibold text-neutral-400">
              {currency}
            </span>
          )}
        </div>

        {/* ── Error slot — always rendered, height reserved ── */}
        <p
          className={clsx(
            'mt-1.5 h-5 text-sm leading-tight',
            error ? 'text-[#E02424]' : 'invisible',
          )}
          role={error ? 'alert' : undefined}
        >
          {error ?? ' '}
        </p>
      </div>
    )
  },
)
AmountInput.displayName = 'AmountInput'
