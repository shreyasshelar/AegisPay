import type { Metadata, Viewport } from 'next'
import { Inter } from 'next/font/google'
import { Providers } from '@/components/providers'
import './globals.css'

const inter = Inter({
  subsets:  ['latin'],
  variable: '--font-inter',
  display:  'swap',
})

export const metadata: Metadata = {
  title: {
    template: '%s | AegisPay',
    default:  'AegisPay — Secure Payments',
  },
  description:  'Production-grade event-driven payment platform',
  manifest:     '/manifest.json',
  // icon.svg lives in app/ — Next.js App Router auto-generates the <link rel="icon">
  // from that file, no explicit entry needed here.  The apple entry is kept for
  // iOS home-screen bookmarks (falls back to icon.svg on modern iOS versions).
  icons: {
    shortcut: '/icon.svg',
  },
  openGraph: {
    type:        'website',
    locale:      'en_US',
    url:         'https://pay.aegispay.io',
    siteName:    'AegisPay',
    title:       'AegisPay — Secure Payments',
    description: 'Production-grade event-driven payment platform',
  },
}

export const viewport: Viewport = {
  width:                'device-width',
  initialScale:         1,
  themeColor:           '#3b82f6',
}

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <html lang="en" className={inter.variable}>
      <body className="min-h-screen bg-slate-50 font-sans antialiased">
        <Providers>{children}</Providers>
      </body>
    </html>
  )
}
