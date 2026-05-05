# Cloudflare Tunnel Setup

Cloudflare Tunnel creates an encrypted outbound connection from your server to
Cloudflare's edge. No static IP required. No open firewall ports. Free.

## One-time Setup (15 minutes)

### 1. Create the tunnel in Cloudflare dashboard

1. Go to [dash.cloudflare.com](https://dash.cloudflare.com)
2. Select your domain → **Zero Trust** (left sidebar)
3. **Networks → Tunnels → Create a tunnel**
4. Choose **Cloudflared** → name it `aegispay-onprem`
5. Copy the **tunnel token** shown on screen (long string starting with `eyJ...`)

### 2. Store the token in Vault

```bash
vault kv put secret/aegispay/cloudflare \
  tunnel_token="<paste your tunnel token here>"
```

### 3. Add the ESO ExternalSecret for the tunnel token

```yaml
# Apply this once to your cluster:
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: cloudflare-tunnel-secret
  namespace: kube-system
spec:
  refreshInterval: 1h
  secretStoreRef:
    name: aegispay-secret-store
    kind: SecretStore
  target:
    name: cloudflare-tunnel-secret
  data:
    - secretKey: tunnel-token
      remoteRef:
        key: secret/data/aegispay/cloudflare
        property: tunnel_token
```

### 4. Deploy the tunnel

```bash
kubectl apply -f infra/cloudflare/tunnel-deployment.yaml
```

### 5. Add public hostnames in the Cloudflare dashboard

In the tunnel's **Public Hostname** tab, add:

| Subdomain | Domain | Service |
|---|---|---|
| `aegispay` | `yourdomain.com` | `http://traefik.kube-system.svc.cluster.local:80` |
| `api.aegispay` | `yourdomain.com` | `http://traefik.kube-system.svc.cluster.local:80` |
| `ws.aegispay` | `yourdomain.com` | `http://traefik.kube-system.svc.cluster.local:80` |
| `grafana.aegispay` | `yourdomain.com` | `http://traefik.kube-system.svc.cluster.local:80` |
| `argocd.aegispay` | `yourdomain.com` | `http://traefik.kube-system.svc.cluster.local:80` |
| `vault.aegispay` | `yourdomain.com` | `http://traefik.kube-system.svc.cluster.local:80` |

Cloudflare handles TLS termination. Traefik receives plain HTTP internally.

### 6. Enable WebSocket support

For `ws.aegispay.yourdomain.com` (notification-service STOMP WebSocket):
- In the Cloudflare tunnel hostname settings, enable **HTTP/2 Connection Coalescing**
- In your Cloudflare domain's **Network** settings → enable **WebSockets**

## How It Works

```
Browser
  │ HTTPS (Cloudflare TLS)
  ▼
Cloudflare Edge
  │ Encrypted tunnel (outbound from your server)
  ▼
cloudflared pod (in kube-system namespace)
  │ HTTP (internal cluster traffic)
  ▼
Traefik Ingress Controller
  │ Routes by hostname
  ▼
Your AegisPay pods
```

## Verification

```bash
# Check tunnel pods are running
kubectl get pods -n kube-system -l app=cloudflared

# Check tunnel is connected in Cloudflare dashboard
# Zero Trust → Networks → Tunnels → aegispay-onprem → should show "Healthy"

# Test from outside
curl https://api.aegispay.yourdomain.com/actuator/health
```

## Cost

Cloudflare Tunnel free tier covers:
- Unlimited bandwidth
- All standard HTTP/HTTPS/WebSocket traffic
- DDoS protection
- TLS certificates (managed by Cloudflare, separate from your Let's Encrypt certs)

**Cost: $0**
