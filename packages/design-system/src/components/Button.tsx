import { cva, type VariantProps } from 'class-variance-authority'
import { clsx } from 'clsx'
import { Loader2 } from 'lucide-react'
import { forwardRef, type ButtonHTMLAttributes } from 'react'

const buttonVariants = cva(
  // base
  'inline-flex items-center justify-center gap-2 rounded-lg font-medium transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 select-none',
  {
    variants: {
      variant: {
        primary:
          'bg-[#1A56DB] text-white hover:bg-[#1C64F2] active:bg-[#1E429F] focus-visible:ring-[#1A56DB]',
        secondary:
          'bg-white text-[#1A56DB] border border-[#1A56DB] hover:bg-blue-50 active:bg-blue-100 focus-visible:ring-[#1A56DB]',
        destructive:
          'bg-[#E02424] text-white hover:bg-[#C81E1E] active:bg-[#9B1C1C] focus-visible:ring-[#E02424]',
        ghost:
          'text-neutral-700 hover:bg-neutral-100 active:bg-neutral-200 focus-visible:ring-neutral-400',
        link: 'text-[#1A56DB] underline-offset-4 hover:underline p-0 h-auto',
      },
      size: {
        sm: 'h-8 px-3 text-sm',
        md: 'h-10 px-4 text-sm',
        lg: 'h-12 px-6 text-base',
        icon: 'h-10 w-10',
      },
    },
    defaultVariants: {
      variant: 'primary',
      size: 'md',
    },
  },
)

export interface ButtonProps
  extends ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {
  loading?: boolean
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, loading, disabled, children, ...props }, ref) => (
    <button
      ref={ref}
      className={clsx(buttonVariants({ variant, size }), className)}
      disabled={disabled ?? loading}
      aria-busy={loading}
      {...props}
    >
      {loading && <Loader2 className="h-4 w-4 animate-spin" aria-hidden />}
      {children}
    </button>
  ),
)
Button.displayName = 'Button'
