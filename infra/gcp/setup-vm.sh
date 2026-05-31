#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# AegisPay — GCP VM Provisioning
#
# Run this ONCE from your laptop (macOS/Linux) with gcloud CLI installed
# and authenticated to your GCP free-trial account.
#
# Prerequisites:
#   brew install google-cloud-sdk   (macOS) OR apt install google-cloud-cli
#   gcloud auth login
#   gcloud auth application-default login
#
# Usage:
#   export GCP_PROJECT="your-project-id"   # e.g. aegispay-gcp-123456
#   export GCP_ZONE="us-central1-a"         # cheapest zone for e2 VMs
#   bash infra/gcp/setup-vm.sh
# ─────────────────────────────────────────────────────────────────────────────

set -euo pipefail

: "${GCP_PROJECT:?Set GCP_PROJECT to your GCP project ID}"
: "${GCP_ZONE:=us-central1-a}"

VM_NAME="aegispay-k3s"
MACHINE_TYPE="e2-standard-4"   # 4 vCPU, 16 GB — minimum for all 10 services
DISK_SIZE="100GB"
DISK_TYPE="pd-standard"        # $0.04/GB/month — cheaper than pd-ssd
SA_NAME="aegispay-eso"         # Service account for ESO → GCP Secret Manager
IMAGE_FAMILY="ubuntu-2204-lts"
IMAGE_PROJECT="ubuntu-os-cloud"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
info() { echo -e "${GREEN}[INFO]${NC} $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }

# ── Step 1: Set active project ─────────────────────────────────────────────────
info "Setting active GCP project to $GCP_PROJECT..."
gcloud config set project "$GCP_PROJECT"

# ── Step 2: Enable required APIs ──────────────────────────────────────────────
info "Enabling GCP APIs (compute, secretmanager, iam)..."
gcloud services enable compute.googleapis.com \
                        secretmanager.googleapis.com \
                        iam.googleapis.com \
                        cloudresourcemanager.googleapis.com

# ── Step 3: Create service account for ESO ────────────────────────────────────
info "Creating service account $SA_NAME..."
gcloud iam service-accounts create "$SA_NAME" \
  --display-name="AegisPay ESO — GCP Secret Manager" \
  --project="$GCP_PROJECT" 2>/dev/null || true   # idempotent

# Grant Secret Manager accessor role
gcloud projects add-iam-policy-binding "$GCP_PROJECT" \
  --member="serviceAccount:${SA_NAME}@${GCP_PROJECT}.iam.gserviceaccount.com" \
  --role="roles/secretmanager.secretAccessor"

# Grant Compute Instance Admin to the default compute SA so Cloud Scheduler
# can start/stop the VM (the scheduler calls Compute Engine API)
DEFAULT_COMPUTE_SA="$(gcloud projects describe $GCP_PROJECT --format='value(projectNumber)')-compute@developer.gserviceaccount.com"
gcloud projects add-iam-policy-binding "$GCP_PROJECT" \
  --member="serviceAccount:${DEFAULT_COMPUTE_SA}" \
  --role="roles/compute.instanceAdmin.v1"

# ── Step 4: Create the VM ──────────────────────────────────────────────────────
info "Creating VM $VM_NAME ($MACHINE_TYPE, $DISK_SIZE)..."
gcloud compute instances create "$VM_NAME" \
  --project="$GCP_PROJECT" \
  --zone="$GCP_ZONE" \
  --machine-type="$MACHINE_TYPE" \
  --image-family="$IMAGE_FAMILY" \
  --image-project="$IMAGE_PROJECT" \
  --boot-disk-size="$DISK_SIZE" \
  --boot-disk-type="$DISK_TYPE" \
  --service-account="${SA_NAME}@${GCP_PROJECT}.iam.gserviceaccount.com" \
  --scopes="https://www.googleapis.com/auth/cloud-platform" \
  --tags="k3s-node,aegispay" \
  --metadata="enable-oslogin=TRUE" \
  --no-address   # No external IP — access only via Cloudflare tunnel

# ── Step 5: Create firewall rules ─────────────────────────────────────────────
# Only allow:
#   - SSH from IAP (Google's Identity-Aware Proxy) for maintenance
#   - Internal traffic between pods
# Cloudflare tunnel connects outbound — no inbound port needed.

info "Creating firewall rules..."
gcloud compute firewall-rules create "allow-iap-ssh-aegispay" \
  --project="$GCP_PROJECT" \
  --direction=INGRESS \
  --action=ALLOW \
  --rules=tcp:22 \
  --source-ranges=35.235.240.0/20 \
  --target-tags=k3s-node \
  --description="SSH via IAP for maintenance" 2>/dev/null || true

# Block all other inbound traffic to the k3s node
gcloud compute firewall-rules create "deny-all-aegispay" \
  --project="$GCP_PROJECT" \
  --direction=INGRESS \
  --action=DENY \
  --rules=all \
  --target-tags=k3s-node \
  --priority=65534 \
  --description="Deny all inbound — tunnel handles ingress" 2>/dev/null || true

info ""
info "✅ VM $VM_NAME created."
info ""
info "SSH into it via IAP (no public IP needed):"
info "  gcloud compute ssh $VM_NAME --tunnel-through-iap --zone=$GCP_ZONE --project=$GCP_PROJECT"
info ""
info "Next: run infra/gcp/vm-schedule.sh to set up start/stop schedule."
info "Then SSH into the VM and run infra/k3s/install.sh"
