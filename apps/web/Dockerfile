# ─────────────────────────────────────────────────────────────────────────────
# AegisPay Web — Multi-stage Docker build
#
# Monorepo structure:
#   root/
#     package.json          (workspaces config)
#     packages/
#       shared-types/
#       design-system/
#       api-client/
#     apps/web/             ← this Dockerfile is here, but context is repo root
#
# Build context must be the repo root (docker build -f apps/web/Dockerfile .)
# ─────────────────────────────────────────────────────────────────────────────

FROM node:22-alpine AS base
RUN apk add --no-cache libc6-compat
WORKDIR /app

# ── Stage 1: Install all dependencies ─────────────────────────────────────────
FROM base AS deps
# Copy workspace manifests first for better layer caching
COPY package.json package-lock.json* ./
COPY packages/shared-types/package.json ./packages/shared-types/
COPY packages/design-system/package.json ./packages/design-system/
COPY packages/api-client/package.json ./packages/api-client/
COPY apps/web/package.json ./apps/web/

RUN npm ci --prefer-offline

# ── Stage 2: Build all packages + web app ─────────────────────────────────────
FROM base AS builder
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY --from=deps /app/packages/shared-types/node_modules ./packages/shared-types/node_modules 2>/dev/null || true
COPY --from=deps /app/packages/design-system/node_modules ./packages/design-system/node_modules 2>/dev/null || true
COPY --from=deps /app/packages/api-client/node_modules ./packages/api-client/node_modules 2>/dev/null || true

# Copy all source files
COPY . .

# Build shared packages first (turbo handles dependency order)
ENV NODE_ENV=production
ARG NEXT_PUBLIC_API_BASE_URL=https://aegispay-api.shreyasshelar.uk
ARG NEXT_PUBLIC_WS_BASE_URL=wss://aegispay-ws.shreyasshelar.uk
ARG NEXTAUTH_URL=https://aegispay.shreyasshelar.uk
ARG KEYCLOAK_ISSUER=https://aegispay-keycloak.shreyasshelar.uk/realms/aegispay

ENV NEXT_PUBLIC_API_BASE_URL=${NEXT_PUBLIC_API_BASE_URL}
ENV NEXT_PUBLIC_WS_BASE_URL=${NEXT_PUBLIC_WS_BASE_URL}
ENV NEXTAUTH_URL=${NEXTAUTH_URL}
ENV KEYCLOAK_ISSUER=${KEYCLOAK_ISSUER}
# Dummy build-time secrets (real values injected at runtime via K8s secrets)
ENV NEXTAUTH_SECRET=build-time-placeholder
ENV KEYCLOAK_ID=aegispay-web
ENV KEYCLOAK_SECRET=build-time-placeholder

# Build packages then web app
RUN npx turbo run build --filter=@aegispay/shared-types --filter=@aegispay/design-system --filter=@aegispay/api-client 2>/dev/null || true
RUN npx turbo run build --filter=@aegispay/web

# ── Stage 3: Production runner ─────────────────────────────────────────────────
FROM base AS runner
WORKDIR /app

ENV NODE_ENV=production
# Disable Next.js telemetry
ENV NEXT_TELEMETRY_DISABLED=1

# Create non-root user
RUN addgroup --system --gid 1001 nodejs \
 && adduser  --system --uid 1001 nextjs

# Copy standalone output (requires output: 'standalone' in next.config.js)
COPY --from=builder /app/apps/web/.next/standalone ./
COPY --from=builder /app/apps/web/.next/static ./apps/web/.next/static
COPY --from=builder /app/apps/web/public ./apps/web/public

USER nextjs

EXPOSE 3000
ENV PORT=3000
ENV HOSTNAME="0.0.0.0"

# standalone output creates server.js at the workspace root level
CMD ["node", "apps/web/server.js"]
