import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook } from '@testing-library/react'
import { useAuthGuard } from './useAuthGuard'

// ── Mock next-auth and next/navigation ────────────────────────────────────────

const mockReplace = vi.fn()

vi.mock('next-auth/react', () => ({
  useSession: vi.fn(),
}))

vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: mockReplace }),
}))

// Pull the mocked useSession so tests can control its return value
import { useSession } from 'next-auth/react'
const mockUseSession = vi.mocked(useSession)

describe('useAuthGuard', () => {
  beforeEach(() => {
    mockReplace.mockClear()
  })

  // ── Loading state ─────────────────────────────────────────────────────────

  it('returns false (not blocking) while session status is loading', () => {
    // The hook intentionally does NOT block on 'loading': DashboardLayout already
    // verified the session server-side, so blocking here causes a blank-page flash
    // on every SSO redirect / hard-refresh. The useEffect will redirect once the
    // revalidation resolves.
    mockUseSession.mockReturnValue({
      status: 'loading',
      data:   null,
      update: vi.fn(),
    })

    const { result } = renderHook(() => useAuthGuard())
    expect(result.current).toBe(false)
    expect(mockReplace).not.toHaveBeenCalled()
  })

  // ── Unauthenticated ───────────────────────────────────────────────────────

  it('redirects to /login when unauthenticated', () => {
    mockUseSession.mockReturnValue({
      status: 'unauthenticated',
      data:   null,
      update: vi.fn(),
    })

    renderHook(() => useAuthGuard())
    expect(mockReplace).toHaveBeenCalledWith('/login')
  })

  it('returns true (blocking) when unauthenticated', () => {
    mockUseSession.mockReturnValue({
      status: 'unauthenticated',
      data:   null,
      update: vi.fn(),
    })

    const { result } = renderHook(() => useAuthGuard())
    expect(result.current).toBe(true)
  })

  // ── Authenticated (clean) ─────────────────────────────────────────────────

  it('returns false (not loading) when authenticated with no error', () => {
    mockUseSession.mockReturnValue({
      status: 'authenticated',
      data:   { user: { id: 'user-1', role: 'CUSTOMER', email: 'a@b.com' }, accessToken: 'at', expires: '' },
      update: vi.fn(),
    })

    const { result } = renderHook(() => useAuthGuard())
    expect(result.current).toBe(false)
    expect(mockReplace).not.toHaveBeenCalled()
  })

  // ── Authenticated with RefreshAccessTokenError ────────────────────────────

  it('redirects to /login when authenticated but session has error', () => {
    mockUseSession.mockReturnValue({
      status: 'authenticated',
      data:   {
        user:        { id: 'user-1', role: 'CUSTOMER', email: 'a@b.com' },
        accessToken: 'expired-at',
        error:       'RefreshAccessTokenError',
        expires:     '',
      },
      update: vi.fn(),
    })

    renderHook(() => useAuthGuard())
    expect(mockReplace).toHaveBeenCalledWith('/login')
  })

  it('returns true (blocking) when authenticated but session has error', () => {
    mockUseSession.mockReturnValue({
      status: 'authenticated',
      data:   {
        user:        { id: 'user-1', role: 'CUSTOMER', email: 'a@b.com' },
        accessToken: 'expired-at',
        error:       'RefreshAccessTokenError',
        expires:     '',
      },
      update: vi.fn(),
    })

    const { result } = renderHook(() => useAuthGuard())
    expect(result.current).toBe(true)
  })

  // ── Edge case: session error clears → guard stops blocking ────────────────

  it('stops blocking after error is cleared on re-render', () => {
    // First render: broken session
    mockUseSession.mockReturnValue({
      status: 'authenticated',
      data:   { user: { id: 'u1', role: 'CUSTOMER', email: '' }, accessToken: 'at', error: 'RefreshAccessTokenError', expires: '' },
      update: vi.fn(),
    })

    const { result, rerender } = renderHook(() => useAuthGuard())
    expect(result.current).toBe(true)

    // Session fixed (user re-signed-in)
    mockUseSession.mockReturnValue({
      status: 'authenticated',
      data:   { user: { id: 'u1', role: 'CUSTOMER', email: 'u@e.com' }, accessToken: 'new-at', expires: '' },
      update: vi.fn(),
    })

    rerender()
    expect(result.current).toBe(false)
  })
})
