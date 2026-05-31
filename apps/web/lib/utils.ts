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

/**
 * Resolve a WebSocket base URL for the current browser context.
 * NEXT_PUBLIC_WS_* vars are compiled with the value from .env at build time,
 * which is often ws://localhost:PORT. When the app is accessed from a LAN IP
 * (e.g. http://192.168.29.34:3000), replace localhost with the actual serving
 * hostname so WebSocket connections reach the correct host.
 */
export function resolveWsUrl(envUrl: string): string {
  if (typeof window === 'undefined') return envUrl
  return envUrl.replace('localhost', window.location.hostname)
}

/**
 * Convert a local date string (YYYY-MM-DD from a date input) to the UTC ISO string
 * that represents the **start** of that day in the browser's local timezone.
 *
 * Why this matters: `new Date("2026-05-31")` (date-only) is parsed as UTC midnight
 * per the spec, which is WRONG when the user means "May 31 in IST".
 * `new Date("2026-05-31T00:00:00")` (with T but no Z/offset) is parsed as LOCAL time
 * per the spec, which is correct — `.toISOString()` then gives the UTC equivalent.
 *
 * Example (browser in IST = UTC+5:30):
 *   localDateToUtcStart("2026-05-31") → "2026-05-30T18:30:00.000Z"  ✓
 *   "2026-05-31T00:00:00Z"            → wrong — misses 6 hrs of IST data
 */
export function localDateToUtcStart(dateStr: string): string {
  return new Date(`${dateStr}T00:00:00`).toISOString()
}

/**
 * Convert a local date string (YYYY-MM-DD) to the UTC ISO string representing the
 * **end** of that day (23:59:59) in the browser's local timezone.
 *
 * Example (IST = UTC+5:30):
 *   localDateToUtcEnd("2026-05-31") → "2026-05-31T18:29:59.000Z"  ✓
 */
export function localDateToUtcEnd(dateStr: string): string {
  return new Date(`${dateStr}T23:59:59`).toISOString()
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
