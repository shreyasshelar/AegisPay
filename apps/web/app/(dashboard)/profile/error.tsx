'use client'

import { AlertTriangle } from 'lucide-react'

// Route-level error boundary for Profile.
// Catches errors thrown during rendering of this route segment and its children.
export default function ProfileError({
  error,
  reset,
}: {
  error: Error & { digest?: string }
  reset: () => void
}) {
  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center p-8">
      <div className="flex max-w-md flex-col items-center gap-6 text-center">
        <div className="flex h-14 w-14 items-center justify-center rounded-full bg-red-50">
          <AlertTriangle className="h-7 w-7 text-red-500" />
        </div>
        <div>
          <h2 className="text-lg font-semibold text-slate-900">Profile Error</h2>
          <p className="mt-1 text-sm text-slate-500">
            Unable to load your profile.
          </p>
          {error.digest && (
            <p className="mt-2 font-mono text-xs text-slate-400">
              Ref: {error.digest}
            </p>
          )}
        </div>
        <button
          onClick={reset}
          className="rounded-lg border border-slate-200 bg-white px-4 py-2 text-sm font-medium text-slate-700 shadow-sm hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2"
        >
          Try again
        </button>
      </div>
    </div>
  )
}
