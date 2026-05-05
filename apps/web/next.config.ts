import type { NextConfig } from 'next'

const nextConfig: NextConfig = {
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

  async headers() {
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
              "script-src 'self' 'unsafe-eval' 'unsafe-inline'", // next dev requires unsafe-eval
              "style-src 'self' 'unsafe-inline'",
              "img-src 'self' data: blob: https:",
              "font-src 'self'",
              "connect-src 'self' ws: wss: https:",
              "frame-ancestors 'none'",
            ].join('; '),
          },
        ],
      },
    ]
  },

  // Suppress hydration warnings from browser extensions
  reactStrictMode: true,

  experimental: {
    // Server Actions are stable in Next.js 14
    serverActions: { allowedOrigins: ['localhost:3000'] },
  },
}

export default nextConfig
