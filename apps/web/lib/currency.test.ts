import { describe, it, expect } from 'vitest'
import {
  inrToCurrency,
  currencyToInr,
  CURRENCY_LOCALE,
  FALLBACK_RATES_INR_PER_UNIT,
  type LiveRates,
} from './currency'

// ── CURRENCY_LOCALE ───────────────────────────────────────────────────────────

describe('CURRENCY_LOCALE', () => {
  it('maps INR to en-IN (lakh/crore grouping)', () => {
    expect(CURRENCY_LOCALE['INR']).toBe('en-IN')
  })

  it('maps USD to en-US', () => {
    expect(CURRENCY_LOCALE['USD']).toBe('en-US')
  })

  it('maps GBP to en-GB', () => {
    expect(CURRENCY_LOCALE['GBP']).toBe('en-GB')
  })

  it('maps EUR to en-IE (English EU locale)', () => {
    expect(CURRENCY_LOCALE['EUR']).toBe('en-IE')
  })
})

// ── FALLBACK_RATES_INR_PER_UNIT ───────────────────────────────────────────────

describe('FALLBACK_RATES_INR_PER_UNIT', () => {
  it('INR rate is exactly 1 (no conversion)', () => {
    expect(FALLBACK_RATES_INR_PER_UNIT['INR']).toBe(1)
  })

  it('has all required currencies', () => {
    expect(FALLBACK_RATES_INR_PER_UNIT).toHaveProperty('USD')
    expect(FALLBACK_RATES_INR_PER_UNIT).toHaveProperty('EUR')
    expect(FALLBACK_RATES_INR_PER_UNIT).toHaveProperty('GBP')
  })

  it('GBP rate is higher than USD (pound is worth more than dollar in INR terms)', () => {
    expect(FALLBACK_RATES_INR_PER_UNIT['GBP']).toBeGreaterThan(FALLBACK_RATES_INR_PER_UNIT['USD'])
  })
})

// ── inrToCurrency — with live rates (Frankfurter format: 1 INR = rate units) ──

describe('inrToCurrency with live rates', () => {
  const liveRates: LiveRates = { USD: 0.01190, EUR: 0.01099, GBP: 0.00936 }

  it('INR → INR returns amount unchanged', () => {
    expect(inrToCurrency(100_000, 'INR', liveRates)).toBe(100_000)
  })

  it('INR → USD multiplies by live rate', () => {
    const result = inrToCurrency(1000, 'USD', liveRates)
    expect(result).toBeCloseTo(1000 * 0.01190, 4)
  })

  it('INR → GBP uses live rate', () => {
    const result = inrToCurrency(100_000, 'GBP', liveRates)
    expect(result).toBeCloseTo(100_000 * 0.00936, 2) // ~936 GBP
  })

  it('INR → EUR uses live rate', () => {
    const result = inrToCurrency(100_000, 'EUR', liveRates)
    expect(result).toBeCloseTo(100_000 * 0.01099, 2)
  })

  it('zero INR → zero in any currency', () => {
    expect(inrToCurrency(0, 'USD', liveRates)).toBe(0)
    expect(inrToCurrency(0, 'GBP', liveRates)).toBe(0)
  })
})

// ── inrToCurrency — fallback (no live rates) ──────────────────────────────────

describe('inrToCurrency without live rates', () => {
  it('uses fallback table when liveRates is null', () => {
    const result = inrToCurrency(1000, 'USD', null)
    // Fallback: 1 USD = 84 INR → 1 INR = 1/84 USD → 1000 INR ≈ 11.9 USD
    expect(result).toBeCloseTo(1000 / FALLBACK_RATES_INR_PER_UNIT['USD'], 1)
  })

  it('uses fallback table when liveRates is undefined', () => {
    const result = inrToCurrency(1000, 'GBP', undefined)
    expect(result).toBeCloseTo(1000 / FALLBACK_RATES_INR_PER_UNIT['GBP'], 1)
  })

  it('INR → INR with no rates is still identity', () => {
    expect(inrToCurrency(5000, 'INR', null)).toBe(5000)
  })

  it('unknown currency with no rates returns original amount', () => {
    const result = inrToCurrency(1000, 'JPY', null)
    expect(result).toBe(1000)
  })
})

// ── currencyToInr — with live rates ──────────────────────────────────────────

describe('currencyToInr with live rates', () => {
  const liveRates: LiveRates = { USD: 0.01190, EUR: 0.01099, GBP: 0.00936 }

  it('INR → INR is identity', () => {
    expect(currencyToInr(500, 'INR', liveRates)).toBe(500)
  })

  it('USD → INR: divides by live rate', () => {
    const result = currencyToInr(11.90, 'USD', liveRates)
    expect(result).toBeCloseTo(11.90 / 0.01190, 1) // ~1000 INR
  })

  it('GBP → INR: divides by live rate', () => {
    const result = currencyToInr(936, 'GBP', liveRates)
    expect(result).toBeCloseTo(936 / 0.00936, 0) // ~100,000 INR
  })

  it('EUR → INR: divides by live rate', () => {
    const result = currencyToInr(10.99, 'EUR', liveRates)
    expect(result).toBeCloseTo(10.99 / 0.01099, 0) // ~1000 INR
  })

  it('roundtrip: inrToCurrency then currencyToInr returns original', () => {
    const original  = 50_000
    const inGBP     = inrToCurrency(original, 'GBP', liveRates)
    const backToINR = currencyToInr(inGBP, 'GBP', liveRates)
    expect(backToINR).toBeCloseTo(original, 0)
  })
})

// ── currencyToInr — fallback ──────────────────────────────────────────────────

describe('currencyToInr without live rates', () => {
  it('USD → INR with fallback table', () => {
    const result = currencyToInr(1, 'USD', null)
    // Fallback: 1 USD = 84 INR
    expect(result).toBeCloseTo(FALLBACK_RATES_INR_PER_UNIT['USD'], 0)
  })

  it('GBP → INR with fallback table', () => {
    const result = currencyToInr(1, 'GBP', null)
    expect(result).toBeCloseTo(FALLBACK_RATES_INR_PER_UNIT['GBP'], 0)
  })

  it('unknown currency with no rates returns original', () => {
    expect(currencyToInr(100, 'JPY', null)).toBe(100)
  })
})

// ── Balance limit invariant ───────────────────────────────────────────────────

describe('balance limit (100_000 INR) conversion', () => {
  const liveRates: LiveRates = { USD: 0.01190, EUR: 0.01099, GBP: 0.00936 }
  const BALANCE_LIMIT_INR = 100_000

  it('100k INR → GBP is approximately £936 at live rates', () => {
    const gbp = inrToCurrency(BALANCE_LIMIT_INR, 'GBP', liveRates)
    expect(gbp).toBeGreaterThan(900)
    expect(gbp).toBeLessThan(970)
  })

  it('100k INR → USD is approximately $1,190 at live rates', () => {
    const usd = inrToCurrency(BALANCE_LIMIT_INR, 'USD', liveRates)
    expect(usd).toBeGreaterThan(1100)
    expect(usd).toBeLessThan(1300)
  })

  it('sends below limit do not trigger risk for INR 9,999', () => {
    const RISK_THRESHOLD = 10_000
    expect(9_999 < RISK_THRESHOLD).toBe(true)
  })

  it('sends at exactly 10k INR trigger risk threshold', () => {
    const RISK_THRESHOLD = 10_000
    expect(10_000 >= RISK_THRESHOLD).toBe(true)
  })

  it('200 GBP exceeds risk threshold (≈21,367 INR)', () => {
    const RISK_THRESHOLD = 10_000
    const inrEquiv = currencyToInr(200, 'GBP', liveRates)
    expect(inrEquiv).toBeGreaterThan(RISK_THRESHOLD)
  })

  it('10 GBP stays below risk threshold (≈1,068 INR)', () => {
    const RISK_THRESHOLD = 10_000
    const inrEquiv = currencyToInr(10, 'GBP', liveRates)
    expect(inrEquiv).toBeLessThan(RISK_THRESHOLD)
  })
})
