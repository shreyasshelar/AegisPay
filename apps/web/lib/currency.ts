/**
 * Live exchange rates format (Frankfurter / ECB):
 *   1 INR = liveRates[currency] units of that currency
 *   e.g. { USD: 0.011905, EUR: 0.010989, GBP: 0.009434 }
 *
 * Use `useFxRates()` to obtain live rates. Pass them to `inrToCurrency` and
 * `currencyToInr` so every calculation uses the current ECB rate instead of a
 * stale hardcoded table.
 */
export type LiveRates = Record<string, number>

/**
 * Fallback rates used ONLY when the Frankfurter API is unreachable (offline /
 * first render before the query resolves). Documented as fallback — never use
 * these for hard validation decisions; always prefer live rates.
 *
 * Format: 1 unit of the key currency = FALLBACK_RATES[key] INR.
 * (Inverse of Frankfurter's format so fallback maths stays readable.)
 */
export const FALLBACK_RATES_INR_PER_UNIT: Record<string, number> = {
  INR: 1,
  USD: 84,
  EUR: 91,
  GBP: 106,
}

/**
 * Correct locale per currency for Intl.NumberFormat grouping.
 * INR → en-IN (lakh/crore: ₹1,00,000)
 * All others → Western thousands (£1,000 / €1,000 / $1,000)
 */
export const CURRENCY_LOCALE: Record<string, string> = {
  INR: 'en-IN',
  USD: 'en-US',
  EUR: 'en-IE',
  GBP: 'en-GB',
}

/**
 * Convert an INR amount to another currency.
 *
 * Prefers `liveRates` (Frankfurter format: 1 INR = liveRates[currency]).
 * Falls back to the static table when live rates are unavailable.
 */
export function inrToCurrency(
  amountInr: number,
  toCurrency: string,
  liveRates?: LiveRates | null,
): number {
  if (toCurrency === 'INR') return amountInr

  if (liveRates?.[toCurrency] != null) {
    // Frankfurter: 1 INR = liveRates[toCurrency] units
    return amountInr * liveRates[toCurrency]
  }

  // Fallback: table stores INR-per-unit → divide
  const rate = FALLBACK_RATES_INR_PER_UNIT[toCurrency]
  return rate ? amountInr / rate : amountInr
}

/**
 * Convert a foreign-currency amount to INR.
 *
 * Prefers `liveRates` (Frankfurter format: 1 INR = liveRates[currency]).
 * Falls back to the static table when live rates are unavailable.
 */
export function currencyToInr(
  amount: number,
  fromCurrency: string,
  liveRates?: LiveRates | null,
): number {
  if (fromCurrency === 'INR') return amount

  if (liveRates?.[fromCurrency] != null) {
    // Frankfurter: 1 INR = liveRates[fromCurrency] units → invert
    return amount / liveRates[fromCurrency]
  }

  // Fallback: table stores INR-per-unit → multiply
  const rate = FALLBACK_RATES_INR_PER_UNIT[fromCurrency]
  return rate ? amount * rate : amount
}
