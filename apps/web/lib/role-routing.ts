/**
 * Role → landing page mapping — single source of truth.
 *
 * Used by:
 *   - app/page.tsx          (server-side root redirect)
 *   - app/(auth)/login/page.tsx  (client-side post-login redirect)
 *   - middleware.ts          (edge redirect from customer pages)
 *
 * When a new role is added, update this map only.
 */
export const ROLE_LANDING: Record<string, string> = {
  ADMIN:       '/triage',
  BACK_OFFICE: '/incidents',
  // MERCHANT_OPS and PARTNER use the customer dashboard until
  // dedicated back-office pages are built for those roles.
  // When added, those pages must also be included in (back-office)/layout.tsx
  // ALLOWED_ROLES — otherwise ROLE_LANDING sends them there and the
  // layout bounces them back, creating a redirect loop.
  //
  // CUSTOMER and any unknown role fall back to /dashboard (caller applies ?? '/dashboard')
}

/** Returns true if the role should be redirected away from customer-facing pages. */
export function isStaffRole(role: string | undefined): boolean {
  return role !== undefined && role in ROLE_LANDING
}
