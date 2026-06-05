import { describe, it, expect, beforeAll, afterAll, vi } from 'vitest'
import { formatAmount, formatDate, timeAgo, maskId, cn } from './utils'

describe('cn (className merger)', () => {
  it('merges simple class strings', () => {
    expect(cn('foo', 'bar')).toBe('foo bar')
  })

  it('resolves Tailwind conflicts — last wins', () => {
    // tailwind-merge: text-sm vs text-lg → text-lg (last wins)
    const result = cn('text-sm', 'text-lg')
    expect(result).toBe('text-lg')
  })

  it('ignores falsy values', () => {
    expect(cn('foo', undefined, false, null as any, 'bar')).toBe('foo bar')
  })
})

describe('formatAmount', () => {
  it('formats INR with lakh grouping', () => {
    const result = formatAmount(100_000, 'INR')
    // en-IN locale: ₹1,00,000.00 (lakh grouping, not 100,000)
    expect(result).toContain('1,00,000')
    expect(result).toContain('₹')
  })

  it('formats USD with western grouping', () => {
    const result = formatAmount(1000, 'USD')
    // en-US locale: $1,000.00 (NOT $1,000 Indian-style)
    expect(result).toContain('$')
    expect(result).toContain('1,000')
  })

  it('formats GBP correctly — not Indian lakh grouping', () => {
    const result = formatAmount(1000, 'GBP')
    expect(result).toContain('£')
    expect(result).toContain('1,000')
    // Must NOT contain Indian-style 1,000 misread as lakh grouping artifact
    expect(result).not.toContain('1,00,')
  })

  it('formats EUR correctly', () => {
    const result = formatAmount(500.5, 'EUR')
    expect(result).toContain('500')
    expect(result).toContain('50')
  })

  it('defaults to INR currency', () => {
    const result = formatAmount(100)
    expect(result).toMatch(/100/)
  })

  it('handles zero amount', () => {
    const result = formatAmount(0)
    expect(result).toMatch(/0/)
  })
})

describe('formatDate', () => {
  it('returns a non-empty string for a valid ISO date', () => {
    const result = formatDate('2024-01-15T10:30:00Z')
    expect(result).toBeTruthy()
    expect(typeof result).toBe('string')
  })

  it('includes the year in the output', () => {
    const result = formatDate('2024-06-01T00:00:00Z', 'en-US')
    expect(result).toContain('2024')
  })
})

describe('timeAgo', () => {
  beforeAll(() => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2024-01-15T12:00:00Z'))
  })

  afterAll(() => {
    vi.useRealTimers()
  })

  it('returns "just now" or "seconds ago" for recent timestamps', () => {
    const result = timeAgo('2024-01-15T11:59:50Z')  // 10s ago
    expect(result).toMatch(/second|now/i)
  })

  it('returns minutes ago for a timestamp 5 minutes ago', () => {
    const result = timeAgo('2024-01-15T11:55:00Z')  // 5 min ago
    expect(result).toMatch(/minute/i)
  })

  it('returns hours ago for a timestamp 2 hours ago', () => {
    const result = timeAgo('2024-01-15T10:00:00Z')  // 2h ago
    expect(result).toMatch(/hour/i)
  })

  it('returns days ago for a timestamp yesterday', () => {
    const result = timeAgo('2024-01-14T12:00:00Z')  // 1 day ago
    expect(result).toMatch(/day|yesterday/i)
  })
})

describe('maskId', () => {
  it('shows first 8 characters followed by ellipsis', () => {
    const id     = '550e8400-e29b-41d4-a716-446655440000'
    const masked = maskId(id)
    expect(masked).toBe('550e8400…')
  })

  it('works for short UUIDs', () => {
    const result = maskId('abcdef12-rest-of-uuid')
    expect(result).toBe('abcdef12…')
  })
})
