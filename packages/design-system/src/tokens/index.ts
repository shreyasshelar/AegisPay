// AegisPay Design Tokens — single source of truth
// Consumed by Tailwind config (web) and exported for reference by iOS/Android

export const colors = {
  primary: {
    50: '#EBF5FF',
    100: '#C3DAFE',
    200: '#A4CAFE',
    300: '#76A9FA',
    400: '#3F83F8',
    500: '#1A56DB', // brand primary
    600: '#1C64F2',
    700: '#1A56DB',
    800: '#1E429F',
    900: '#233876',
  },
  success: {
    50: '#F3FAF7',
    100: '#DEF7EC',
    500: '#0E9F6E', // brand green
    600: '#057A55',
    700: '#046C4E',
  },
  danger: {
    50: '#FDF2F2',
    100: '#FDE8E8',
    500: '#E02424', // brand red
    600: '#C81E1E',
    700: '#9B1C1C',
  },
  warning: {
    50: '#FFFBEB',
    100: '#FEF3C7',
    500: '#FF8A00', // brand amber
    600: '#D97706',
    700: '#B45309',
  },
  neutral: {
    50: '#F9FAFB',
    100: '#F3F4F6',
    200: '#E5E7EB',
    300: '#D1D5DB',
    400: '#9CA3AF',
    500: '#6B7280',
    600: '#4B5563',
    700: '#374151',
    800: '#1F2937',
    900: '#111827',
  },
} as const

export const typography = {
  fontFamily: {
    sans: ['Inter', 'ui-sans-serif', 'system-ui', 'sans-serif'],
    mono: ['JetBrains Mono', 'ui-monospace', 'monospace'],
  },
  fontSize: {
    xs: '0.75rem',    // 12px - caption
    sm: '0.875rem',   // 14px - body small
    base: '1rem',     // 16px - body
    lg: '1.125rem',   // 18px - heading sm
    xl: '1.25rem',    // 20px - heading md
    '2xl': '1.5rem',  // 24px - heading lg
    '3xl': '2rem',    // 32px - display
  },
  fontWeight: {
    normal: '400',
    medium: '500',
    semibold: '600',
    bold: '700',
  },
} as const

export const spacing = {
  0: '0',
  1: '0.25rem',
  2: '0.5rem',
  3: '0.75rem',
  4: '1rem',
  5: '1.25rem',
  6: '1.5rem',
  8: '2rem',
  10: '2.5rem',
  12: '3rem',
  16: '4rem',
  20: '5rem',
  24: '6rem',
} as const

export const borderRadius = {
  none: '0',
  sm: '0.375rem',
  md: '0.5rem',
  lg: '0.75rem',
  xl: '1rem',
  '2xl': '1.5rem',
  full: '9999px',
} as const

export const shadows = {
  sm: '0 1px 2px 0 rgb(0 0 0 / 0.05)',
  DEFAULT: '0 1px 3px 0 rgb(0 0 0 / 0.1), 0 1px 2px -1px rgb(0 0 0 / 0.1)',
  md: '0 4px 6px -1px rgb(0 0 0 / 0.1), 0 2px 4px -2px rgb(0 0 0 / 0.1)',
  lg: '0 10px 15px -3px rgb(0 0 0 / 0.1), 0 4px 6px -4px rgb(0 0 0 / 0.1)',
} as const

// Status color map — used by AegisBadge and AegisStatusTimeline
export const statusColors = {
  INITIATED:    { bg: 'bg-blue-100',   text: 'text-blue-800',   dot: 'bg-blue-500' },
  RESERVED:     { bg: 'bg-purple-100', text: 'text-purple-800', dot: 'bg-purple-500' },
  RISK_CLEARED: { bg: 'bg-indigo-100', text: 'text-indigo-800', dot: 'bg-indigo-500' },
  PROCESSING:   { bg: 'bg-amber-100',  text: 'text-amber-800',  dot: 'bg-amber-500' },
  COMPLETED:    { bg: 'bg-green-100',  text: 'text-green-800',  dot: 'bg-green-500' },
  FAILED:       { bg: 'bg-red-100',    text: 'text-red-800',    dot: 'bg-red-500' },
  ROLLED_BACK:  { bg: 'bg-gray-100',   text: 'text-gray-600',   dot: 'bg-gray-400' },
} as const
