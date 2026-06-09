import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement } from 'react'
import { useFxRates } from './useFxRates'

// ── Helpers ───────────────────────────────────────────────────────────────────

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry:    false,   // don't retry on error in tests
        gcTime:   0,       // don't cache between tests
      },
    },
  })
  return ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children)
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('useFxRates', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    // Remove fetch mock so each test is isolated
    vi.unstubAllGlobals()
  })

  it('returns live rates on successful API call', async () => {
    const mockResponse = {
      amount: 1,
      base:   'INR',
      date:   '2026-06-09',
      rates:  { USD: 0.01190, EUR: 0.01099, GBP: 0.00936 },
    }

    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok:   true,
      json: async () => mockResponse,
    }))

    const { result } = renderHook(() => useFxRates(), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    expect(result.current.data).toEqual({ USD: 0.01190, EUR: 0.01099, GBP: 0.00936 })
  })

  it('returns undefined data while loading', () => {
    // Never-resolving fetch → query stays in loading state
    vi.stubGlobal('fetch', vi.fn().mockReturnValue(new Promise(() => {})))

    const { result } = renderHook(() => useFxRates(), { wrapper: createWrapper() })

    expect(result.current.isPending).toBe(true)
    expect(result.current.data).toBeUndefined()
  })

  it('exposes error when fetch throws', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('Network down')))

    const { result } = renderHook(() => useFxRates(), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(result.current.error).toBeDefined()
  })

  it('rates object has all three required currency keys', async () => {
    const mockRates = { USD: 0.012, EUR: 0.011, GBP: 0.0094 }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok:   true,
      json: async () => ({ amount: 1, base: 'INR', date: '2026-06-09', rates: mockRates }),
    }))

    const { result } = renderHook(() => useFxRates(), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    const rates = result.current.data!
    expect(Object.keys(rates)).toContain('USD')
    expect(Object.keys(rates)).toContain('EUR')
    expect(Object.keys(rates)).toContain('GBP')
  })

  it('rates are in Frankfurter format: 1 INR = rate[currency] units (i.e. < 1)', async () => {
    const mockRates = { USD: 0.01190, EUR: 0.01099, GBP: 0.00936 }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok:   true,
      json: async () => ({ amount: 1, base: 'INR', date: '2026-06-09', rates: mockRates }),
    }))

    const { result } = renderHook(() => useFxRates(), { wrapper: createWrapper() })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    // Since 1 INR << 1 USD/EUR/GBP, all rates must be well below 1
    const rates = result.current.data!
    expect(rates['USD']).toBeLessThan(1)
    expect(rates['EUR']).toBeLessThan(1)
    expect(rates['GBP']).toBeLessThan(1)
    expect(rates['USD']).toBeGreaterThan(0)
  })

  it('uses the correct Frankfurter endpoint with INR base', () => {
    const fetchMock = vi.fn().mockReturnValue(new Promise(() => {}))
    vi.stubGlobal('fetch', fetchMock)

    renderHook(() => useFxRates(), { wrapper: createWrapper() })

    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('frankfurter.app'),
    )
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('base=INR'),
    )
  })
})
