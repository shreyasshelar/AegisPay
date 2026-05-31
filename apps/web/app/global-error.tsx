'use client'

// Root-level error boundary — catches errors that escape all nested boundaries.
// Rendered with a minimal HTML shell (replaces <html>/<body>) because the
// root layout itself may have thrown.
export default function GlobalError({
  error,
  reset,
}: {
  error: Error & { digest?: string }
  reset: () => void
}) {
  return (
    <html lang="en">
      <body className="flex min-h-screen flex-col items-center justify-center bg-slate-50 p-8">
        <div className="flex max-w-md flex-col items-center gap-6 text-center">
          <div className="flex h-16 w-16 items-center justify-center rounded-full bg-red-100">
            <span className="text-3xl">⚠️</span>
          </div>
          <div>
            <h1 className="text-xl font-semibold text-slate-900">Something went wrong</h1>
            <p className="mt-2 text-sm text-slate-500">
              An unexpected error occurred. Please try again.
            </p>
            {error.digest && (
              <p className="mt-1 font-mono text-xs text-slate-400">
                Error ID: {error.digest}
              </p>
            )}
          </div>
          <button
            onClick={reset}
            className="rounded-lg bg-primary-600 px-5 py-2.5 text-sm font-medium text-white hover:bg-primary-700 focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2"
          >
            Try again
          </button>
        </div>
      </body>
    </html>
  )
}
