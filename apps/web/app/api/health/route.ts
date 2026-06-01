import { NextResponse } from 'next/server'

/**
 * Health check endpoint for Kubernetes readiness/liveness probes.
 * Returns 200 OK when the Next.js server is ready.
 */
export async function GET() {
  return NextResponse.json(
    { status: 'ok', service: 'aegispay-web', timestamp: new Date().toISOString() },
    { status: 200 }
  )
}
