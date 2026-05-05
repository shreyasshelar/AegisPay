import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'

/** Merge Tailwind class names safely (handles conflicts). */
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

/** Format a monetary amount for display. */
export function formatAmount(
  amount:   number,
  currency: string = 'INR',
  locale:   string = 'en-IN',
): string {
  return new Intl.NumberFormat(locale, {
    style:                 'currency',
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(amount)
}

/** Format an ISO timestamp to a short readable string. */
export function formatDate(
  isoString: string,
  locale:    string = 'en-IN',
): string {
  return new Intl.DateTimeFormat(locale, {
    day:    'numeric',
    month:  'short',
    year:   'numeric',
    hour:   '2-digit',
    minute: '2-digit',
  }).format(new Date(isoString))
}

/** Relative time (e.g. "3 minutes ago"). */
export function timeAgo(isoString: string): string {
  const diff = Date.now() - new Date(isoString).getTime()
  const rtf  = new Intl.RelativeTimeFormat('en', { numeric: 'auto' })

  if (diff < 60_000)         return rtf.format(-Math.round(diff / 1000),       'second')
  if (diff < 3_600_000)      return rtf.format(-Math.round(diff / 60_000),     'minute')
  if (diff < 86_400_000)     return rtf.format(-Math.round(diff / 3_600_000),  'hour')
  return rtf.format(-Math.round(diff / 86_400_000), 'day')
}

/** Mask a UUID for display, showing first 8 chars only. */
export function maskId(id: string): string {
  return `${id.slice(0, 8)}…`
}

/** Copy text to clipboard — returns promise. */
export async function copyToClipboard(text: string): Promise<void> {
  if (navigator.clipboard) {
    await navigator.clipboard.writeText(text)
  } else {
    // Fallback for older browsers
    const el = document.createElement('textarea')
    el.value = text
    document.body.appendChild(el)
    el.select()
    document.execCommand('copy')
    document.body.removeChild(el)
  }
}
