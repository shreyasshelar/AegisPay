/** @type {import('next').NextConfig} */
const isDev = process.env.NODE_ENV === 'development'

// Server-side API base (not exposed to browser) — used by the rewrite proxy
const API_BASE_URL = process.env.API_BASE_URL ?? 'http://localhost:8080'

// WebSocket host for CSP — derive from NEXT_PUBLIC_WS_BASE_URL so LAN-IP testing works.
// e.g. ws://192.168.29.34:8086  →  extract ws://192.168.29.34 (all ports on that host)
const wsBaseUrl = process.env.NEXT_PUBLIC_WS_BASE_URL ?? 'ws://localhost:8086'
const wsHost = (() => {
  try { const u = new URL(wsBaseUrl); return `${u.protocol}//${u.hostname}` } catch { return 'ws://localhost' }
})()

const nextConfig = {
  transpilePackages: [
    '@aegispay/design-system',
    '@aegispay/api-client',
    '@aegispay/shared-types',
  ],

  images: {
    remotePatterns: [
      { protocol: 'https', hostname: 'avatars.githubusercontent.com' },
      { protocol: 'https', hostname: '*.blob.core.windows.net' },
    ],
  },

  // Proxy /api/v1/* through Next.js server so the browser makes same-origin
  // requests (avoiding CSP and CORS issues with the API gateway).
  async rewrites() {
    return [
      {
        source: '/api/v1/:path*',
        destination: `${API_BASE_URL}/api/v1/:path*`,
      },
    ]
  },

  async headers() {
    // Allow ws://hostname:* so WebSocket to notification-service works from any IP
    const wsAllow = wsHost === 'ws://localhost' ? 'ws://localhost:*' : `ws://localhost:* ${wsHost}:*`
    const connectSrc = isDev
      ? `connect-src 'self' http://localhost:* ${wsAllow} wss: https:`
      : "connect-src 'self' wss: https:"

    // 'unsafe-eval' is required by Next.js hot-reload in development.
    // Strip it in production so script execution is locked to same-origin + nonces.
    // www.google.com + www.gstatic.com are required for Firebase Phone Auth reCAPTCHA.
    const scriptSrc = isDev
      ? "script-src 'self' 'unsafe-eval' 'unsafe-inline' https://www.google.com https://www.gstatic.com"
      : "script-src 'self' 'unsafe-inline' https://www.google.com https://www.gstatic.com"

    return [
      {
        source: '/(.*)',
        headers: [
          { key: 'X-Frame-Options', value: 'DENY' },
          { key: 'X-Content-Type-Options', value: 'nosniff' },
          { key: 'Referrer-Policy', value: 'strict-origin-when-cross-origin' },
          // HSTS — tell browsers to always use HTTPS for 2 years; include subdomains
          // and allow preload-list submission. Omit in dev to avoid locking localhost.
          ...(!isDev ? [{
            key: 'Strict-Transport-Security',
            value: 'max-age=63072000; includeSubDomains; preload',
          }] : []),
          {
            key: 'Permissions-Policy',
            value: 'camera=(), microphone=(), geolocation=()',
          },
          {
            key: 'Content-Security-Policy',
            value: [
              "default-src 'self'",
              scriptSrc,
              "style-src 'self' 'unsafe-inline'",
              "img-src 'self' data: blob: https:",
              "font-src 'self' data:",
              connectSrc,
              // reCAPTCHA (Firebase Phone Auth) renders inside a Google-hosted iframe.
              // *.firebaseapp.com covers the Firebase Auth popup/redirect flow.
              "frame-src https://www.google.com https://*.firebaseapp.com",
              "frame-ancestors 'none'",
            ].join('; '),
          },
        ],
      },
    ]
  },

  reactStrictMode: true,

  // Allow Next.js dev server to accept requests from LAN IP without
  // treating them as cross-origin (which blocks state cookie transmission).
  allowedDevOrigins: (() => {
    const origins = []
    const nextAuthUrl = process.env.NEXTAUTH_URL
    if (nextAuthUrl) {
      try { origins.push(new URL(nextAuthUrl).hostname) } catch {}
    }
    return origins.length ? origins : ['192.168.29.34']
  })(),

  experimental: {
    serverActions: {
      // Allow server actions from localhost and any LAN IP configured via NEXTAUTH_URL
      allowedOrigins: (() => {
        const origins = ['localhost:3000']
        const nextAuthUrl = process.env.NEXTAUTH_URL
        if (nextAuthUrl) {
          try { origins.push(new URL(nextAuthUrl).host) } catch {}
        }
        return [...new Set(origins)]
      })(),
    },
  },
}

module.exports = nextConfig
