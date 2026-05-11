/** @type {import('next').NextConfig} */
const isDev = process.env.NODE_ENV === 'development'

// Server-side API base (not exposed to browser) — used by the rewrite proxy
const API_BASE_URL = process.env.API_BASE_URL ?? 'http://localhost:8080'

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
    const connectSrc = isDev
      // Dev: allow HTTP to localhost for API gateway, ws for notification WS
      ? "connect-src 'self' http://localhost:* ws://localhost:* wss: https:"
      // Prod: same-origin rewrites mean no cross-origin HTTP needed; HTTPS for third-parties
      : "connect-src 'self' wss: https:"

    return [
      {
        source: '/(.*)',
        headers: [
          { key: 'X-Frame-Options', value: 'DENY' },
          { key: 'X-Content-Type-Options', value: 'nosniff' },
          { key: 'Referrer-Policy', value: 'strict-origin-when-cross-origin' },
          {
            key: 'Permissions-Policy',
            value: 'camera=(), microphone=(), geolocation=()',
          },
          {
            key: 'Content-Security-Policy',
            value: [
              "default-src 'self'",
              "script-src 'self' 'unsafe-eval' 'unsafe-inline'",
              "style-src 'self' 'unsafe-inline'",
              "img-src 'self' data: blob: https:",
              "font-src 'self' data:",
              connectSrc,
              "frame-ancestors 'none'",
            ].join('; '),
          },
        ],
      },
    ]
  },

  reactStrictMode: true,

  experimental: {
    serverActions: { allowedOrigins: ['localhost:3000'] },
  },
}

module.exports = nextConfig
