#!/bin/bash
# aegispay-pod-cleanup.sh
#
# Runs at VM boot (after k3s starts) to force-delete pods stuck in Unknown state.
#
# Unknown pods accumulate when kubelet loses contact with the API server:
#   - VM restart (most common)
#   - containerd crash / OOM kill
#   - Temporary network partition between kubelet and API server
#
# Kubernetes does NOT auto-delete Unknown pods on a single-node cluster —
# it waits for the node to report back.  Controllers (Deployment, StatefulSet)
# will NOT spawn replacement pods until the stuck pod is removed.
#
# Install:
#   sudo cp infra/k3s/aegispay-pod-cleanup.sh /usr/local/bin/aegispay-pod-cleanup.sh
#   sudo chmod +x /usr/local/bin/aegispay-pod-cleanup.sh
#   sudo cp infra/k3s/aegispay-pod-cleanup.service /etc/systemd/system/
#   sudo systemctl daemon-reload
#   sudo systemctl enable aegispay-pod-cleanup.service
#
set -euo pipefail
LOG=/var/log/aegispay-pod-cleanup.log

log() { echo "$(date -u '+%Y-%m-%dT%H:%M:%SZ') $*" | tee -a "$LOG"; }

log "=== aegispay-pod-cleanup: starting ==="

# Step 1 — wait for k3s API server to be responsive
log "Waiting for k3s API server..."
for i in $(seq 1 60); do
  if kubectl get nodes --request-timeout=5s >/dev/null 2>&1; then
    log "API server ready after ${i}×5s"
    break
  fi
  sleep 5
done

# Step 2 — give pods time to settle (Pending→Running transition window)
# Without this, we might delete pods that are legitimately starting up.
log "Waiting 120s for pods to stabilise..."
sleep 120

# Step 3 — force-delete all Unknown pods across all namespaces
log "Scanning for Unknown pods..."
DELETED=0
while IFS= read -r line; do
  NS=$(echo "$line"  | awk '{print $1}')
  POD=$(echo "$line" | awk '{print $2}')
  if [ -n "$POD" ]; then
    log "Force-deleting Unknown pod $POD in $NS"
    kubectl delete pod "$POD" -n "$NS" --force --grace-period=0 2>>"$LOG" || true
    DELETED=$((DELETED + 1))
  fi
done < <(kubectl get pods --all-namespaces \
             --field-selector='status.phase=Unknown' \
             --no-headers 2>/dev/null || true)

log "Done. Force-deleted $DELETED Unknown pod(s)."
