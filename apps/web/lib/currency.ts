/**
 * Approximate INR exchange rates: 1 unit of the key currency = N INR.
 *
 * These are used exclusively for client-side UX validation and display.
 * The backend is always authoritative; these rates give early feedback so
 * users see limits in their selected currency without a server round-trip.
 *
 * Update these values periodically to keep UX limits reasonable. The backend
 * will catch any edge cases that arise from rate drift.
 */
export const FX_RATES_TO_INR: Record<string, number> = {
  INR: 1,
  USD: 84,
  EUR: 91,
  GBP: 106,
}

/**
 * Correct locale per currency so Intl.NumberFormat produces the right number
 * grouping. INR uses lakh/crore grouping (₹1,00,000); all others use Western
 * thousands grouping ($1,000 / £1,000 / €1,000).
 */
export const CURRENCY_LOCALE: Record<string, string> = {
  INR: 'en-IN',
  USD: 'en-US',
  EUR: 'en-IE',
  GBP: 'en-GB',
}

/**
 * Convert an amount defined in INR to its equivalent in another currency.
 * Used to display INR-defined backend limits (e.g. ₹1,00,000 max balance)
 * in whatever currency the user has selected.
 */
export function inrToCurrency(amountInr: number, toCurrency: string): number {
  if (toCurrency === 'INR') return amountInr
  const rate = FX_RATES_TO_INR[toCurrency]
  if (!rate) return amountInr
  return amountInr / rate
}

/**
 * Convert an amount in a given currency to INR.
 * Used to validate user-entered amounts against INR-defined backend limits
 * before submitting (e.g. top-up balance cap, risk-engine thresholds).
 */
export function currencyToInr(amount: number, fromCurrency: string): number {
  if (fromCurrency === 'INR') return amount
  const rate = FX_RATES_TO_INR[fromCurrency]
  if (!rate) return amount
  return amount * rate
}
