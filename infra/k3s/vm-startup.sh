#!/bin/bash
# ─────────────────────────────────────────────────────────────────────────────
# AegisPay — GCP VM startup script
#
# Applied as GCP instance metadata (startup-script). Runs as root on every
# VM boot — including scheduled starts from the resource schedule.
#
# What it does:
#   1. Ensures k3s.service is enabled and running (idempotent — safe to run
#      even when everything is already up).
#   2. Waits for the k3s node to reach Ready state (max 5 min).
#   3. Logs pod status per namespace so Cloud Logging shows cluster health.
#
# All AegisPay workloads (ArgoCD, ESO, Cloudflare tunnel, app services) are
# Kubernetes Deployments — they restart automatically once k3s is Ready.
# No manual intervention is needed.
#
# Apply once (run from workstation or the VM itself):
#   gcloud compute instances add-metadata aegispay-k3s \
#     --zone=us-central1-a \
#     --metadata-from-file=startup-script=infra/k3s/vm-startup.sh
#
# View logs on the VM:
#   sudo journalctl -u google-startup-scripts -f
#   sudo cat /var/log/aegispay-startup.log
# ─────────────────────────────────────────────────────────────────────────────
set -uo pipefail

LOG=/var/log/aegispay-startup.log
KUBECONFIG=/etc/rancher/k3s/k3s.yaml
export KUBECONFIG

log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" | tee -a "$LOG"; }

log "========================================================"
log "AegisPay VM startup — $(hostname)"
log "========================================================"

# ── 1. Enable k3s for future boots (idempotent) ───────────────────────────────
if ! systemctl is-enabled --quiet k3s 2>/dev/null; then
    log "Enabling k3s.service for autostart..."
    systemctl enable k3s
fi

# ── 2. Start k3s if not already running ───────────────────────────────────────
if systemctl is-active --quiet k3s; then
    log "k3s is already running — skipping start"
else
    log "Starting k3s.service..."
    systemctl start k3s
    sleep 5   # brief pause before polling
fi

# ── 3. Wait for k3s node to be Ready (60 × 5s = 5 min max) ──────────────────
log "Waiting for k3s node to be Ready..."
MAX_ATTEMPTS=60
for i in $(seq 1 $MAX_ATTEMPTS); do
    if kubectl get nodes 2>/dev/null | grep -q " Ready"; then
        ELAPSED=$((i * 5))
        log "Node Ready after ~${ELAPSED}s"
        break
    fi
    if [ "$i" -eq "$MAX_ATTEMPTS" ]; then
        log "ERROR: k3s node not Ready after $((MAX_ATTEMPTS * 5))s"
        log "Diagnose with: journalctl -u k3s --since '5 minutes ago'"
        # Don't exit 1 — let other startup tasks proceed; GCE logs will show this
        break
    fi
    sleep 5
done

# ── 4. Log pod status (informational — visible in Cloud Logging) ──────────────
log "Pod status by namespace:"
kubectl get pods -A --no-headers 2>/dev/null \
    | awk '{ns=$1; st=$4; print ns, st}' \
    | sort | uniq -c \
    | while read count ns status; do
        log "  ${count}x  ${status}  [${ns}]"
    done || log "  (kubectl not yet available)"

log "========================================================"
log "Startup complete — Kubernetes will schedule all pods"
log "ArgoCD syncs automatically. Full readiness: ~3-5 min."
log "========================================================"
