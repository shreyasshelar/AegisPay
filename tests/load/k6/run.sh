#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# AegisPay k6 load test runner
#
# Usage:
#   ./tests/load/k6/run.sh [scenario] [options]
#
# Scenarios:
#   happy-path   — 500 RPS sustained (default)
#   idempotency  — concurrent idempotency key replay
#   saga-timeout — saga compensation under downstream delay
#   all          — run all scenarios sequentially
#
# Options:
#   --env BASE_URL=https://api.aegispay.io
#   --out influxdb=http://localhost:8086/k6
#
# Examples:
#   ./run.sh happy-path --env BASE_URL=http://localhost:8080
#   ./run.sh all --env BASE_URL=https://staging.aegispay.io
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCENARIO="${1:-happy-path}"
shift || true  # remaining args forwarded to k6

run() {
    local script="$DIR/$1.js"
    echo "━━━ Running: $1 ━━━"
    k6 run "$script" "$@"
}

case "$SCENARIO" in
    happy-path)   run happy-path   "$@" ;;
    idempotency)  run idempotency  "$@" ;;
    saga-timeout) run saga-timeout "$@" ;;
    all)
        run happy-path   "$@"
        run idempotency  "$@"
        run saga-timeout "$@"
        ;;
    *)
        echo "Unknown scenario: $SCENARIO"
        echo "Valid: happy-path | idempotency | saga-timeout | all"
        exit 1
        ;;
esac
