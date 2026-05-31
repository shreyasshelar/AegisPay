#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# AegisPay — GCP VM Instance Schedule (Start / Stop)
#
# Creates a GCP Resource Policy that automatically:
#   Starts the VM at 02:30 UTC (8:00 AM IST)
#   Stops  the VM at 18:30 UTC (12:00 AM / midnight IST)
#
# This saves ~33% compute cost vs running 24/7:
#   16h/day × $0.134/hr × 90 days ≈ $193  (vs $289 for 24/7)
#
# Usage:
#   export GCP_PROJECT="your-project-id"
#   export GCP_REGION="us-central1"
#   export GCP_ZONE="us-central1-a"
#   bash infra/gcp/vm-schedule.sh
# ─────────────────────────────────────────────────────────────────────────────

set -euo pipefail

: "${GCP_PROJECT:?Set GCP_PROJECT}"
: "${GCP_REGION:=us-central1}"
: "${GCP_ZONE:=us-central1-a}"

VM_NAME="aegispay-k3s"
POLICY_NAME="aegispay-vm-schedule"

GREEN='\033[0;32m'; NC='\033[0m'
info() { echo -e "${GREEN}[INFO]${NC} $*"; }

gcloud config set project "$GCP_PROJECT"

# ── Create instance schedule resource policy ───────────────────────────────────
info "Creating VM schedule policy $POLICY_NAME..."
info "  Start: 02:30 UTC (08:00 IST)"
info "  Stop:  18:30 UTC (00:00 IST)"

gcloud compute resource-policies create instance-schedule "$POLICY_NAME" \
  --project="$GCP_PROJECT" \
  --region="$GCP_REGION" \
  --vm-start-schedule="30 2 * * *" \
  --vm-stop-schedule="30 18 * * *" \
  --timezone="UTC" \
  --description="AegisPay K3s VM — runs 8am to midnight IST to save cost"

# ── Attach the policy to the VM ────────────────────────────────────────────────
info "Attaching schedule to VM $VM_NAME..."
gcloud compute instances add-resource-policies "$VM_NAME" \
  --project="$GCP_PROJECT" \
  --zone="$GCP_ZONE" \
  --resource-policies="$POLICY_NAME"

info ""
info "✅ VM schedule active."
info "   The VM will stop automatically at midnight IST and start at 8am IST."
info "   Disk continues to accrue ~\$4/month cost while VM is stopped."
info ""
info "To start the VM manually right now:"
info "  gcloud compute instances start $VM_NAME --zone=$GCP_ZONE --project=$GCP_PROJECT"
info ""
info "To stop it manually:"
info "  gcloud compute instances stop $VM_NAME --zone=$GCP_ZONE --project=$GCP_PROJECT"
info ""
info "To SSH in (works even without public IP):"
info "  gcloud compute ssh $VM_NAME --tunnel-through-iap --zone=$GCP_ZONE --project=$GCP_PROJECT"
