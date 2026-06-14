import { useQuery } from '@tanstack/react-query'
import type { LiveRates } from './currency'

/**
 * Response shape from api.frankfurter.app/latest
 * Example: GET /latest?base=INR&symbols=USD,EUR,GBP
 * {
 *   "amount": 1,
 *   "base": "INR",
 *   "date": "2026-06-06",
 *   "rates": { "EUR": 0.010989, "GBP": 0.009434, "USD": 0.011905 }
 * }
 * Meaning: 1 INR = rates[currency] units of that currency.
 */
interface FrankfurterResponse {
  amount: number
  base: string
  date: string
  rates: LiveRates
}

const SYMBOLS      = ['USD', 'EUR', 'GBP'] as const
const FRANKFURTER  = 'https://api.frankfurter.app'

/**
 * Fetches live INR exchange rates from Frankfurter (ECB data, no API key).
 *
 * Returns `rates` where **1 INR = rates[currency]**:
 *   - inrToCurrency(100_000, 'GBP', rates) → 100_000 × rates.GBP
 *   - currencyToInr(940, 'GBP', rates)    → 940 / rates.GBP
 *
 * Stale time: 1 h (ECB publishes once per business day; hourly is more than fresh).
 * Retries: 2 (transient network errors). Falls back to static FALLBACK_RATES_INR_PER_UNIT
 * in `currency.ts` when the query has not yet resolved or has errored.
 */
export function useFxRates() {
  return useQuery<LiveRates>({
    queryKey:  ['fxRates', 'INR', SYMBOLS.join(',')],
    queryFn:   async () => {
      const url = `${FRANKFURTER}/latest?base=INR&symbols=${SYMBOLS.join(',')}`
      const res = await fetch(url, { cache: 'no-store' })
      if (!res.ok) throw new Error(`Frankfurter responded ${res.status}`)
      const body: FrankfurterResponse = await res.json()
      return body.rates
    },
    staleTime: 60 * 60 * 1000,        // 1 hour — re-fetch in background after this
    gcTime:    2  * 60 * 60 * 1000,   // 2 hours — keep in cache
    refetchOnWindowFocus: false,       // rates don't change on tab-switch
  })
}
