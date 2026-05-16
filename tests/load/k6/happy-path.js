/**
 * AegisPay — Happy Path Load Test (500 RPS sustained)
 *
 * Scenario: authenticated user initiates a payment, orchestrator creates the
 * saga, risk-engine approves, ledger debits / credits, notification sent.
 *
 * Target: 500 requests/second sustained for 5 minutes with ≤1% error rate.
 *
 * Run:
 *   k6 run --env BASE_URL=https://api.aegispay.io tests/load/k6/happy-path.js
 *
 * Output: a k6 summary + optional InfluxDB / Grafana push via K6_OUT env var:
 *   k6 run --out influxdb=http://localhost:8086/k6 ... happy-path.js
 */

import http         from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { uuidv4 }   from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';
import { BASE_URL, TEST_PAYEE_ID, THRESHOLDS } from './config.js';
import { getToken, authHeaders } from './auth.js';

// ── Custom metrics ───────────────────────────────────────────────────────────
const txnCreated   = new Counter('txn_created');
const txnCompleted = new Counter('txn_completed');
const pollLatency  = new Trend('txn_poll_latency_ms');

// ── Test config ──────────────────────────────────────────────────────────────
export const options = {
    scenarios: {
        steady_rps: {
            executor:           'constant-arrival-rate',
            rate:               500,        // 500 iterations/s
            timeUnit:           '1s',
            duration:           '5m',
            preAllocatedVUs:    200,
            maxVUs:             600,
        },
    },
    thresholds: {
        ...THRESHOLDS,
        txn_poll_latency_ms: ['p(95)<3000'],  // saga completes within 3 s at p95
    },
};

// ── VU setup — fetch token once per VU ──────────────────────────────────────
export function setup() {
    return { token: getToken() };
}

// ── Main iteration ───────────────────────────────────────────────────────────
export default function (data) {
    const token    = data.token;
    const headers  = authHeaders(token, { 'Idempotency-Key': uuidv4() });

    // ── Step 1: Initiate payment ─────────────────────────────────────────────
    const payload = JSON.stringify({
        payeeId:  TEST_PAYEE_ID,
        amount:   (Math.random() * 9900 + 100).toFixed(2), // ₹100 – ₹10,000
        currency: 'INR',
        note:     'k6 load test',
    });

    const initRes = http.post(`${BASE_URL}/api/v1/payments`, payload, {
        headers,
        tags: { name: 'payment/initiate' },
    });

    const ok = check(initRes, {
        'initiate: status 202': (r) => r.status === 202,
        'initiate: has txnId':  (r) => !!r.json('transactionId'),
    });
    if (!ok) return;

    txnCreated.add(1);
    const txnId    = initRes.json('transactionId');
    const pollHdr  = authHeaders(token);

    // ── Step 2: Poll for completion (max 10 × 300 ms = 3 s) ─────────────────
    const startMs = Date.now();
    let   done    = false;

    for (let i = 0; i < 10; i++) {
        sleep(0.3);

        const statusRes = http.get(`${BASE_URL}/api/v1/payments/${txnId}`, {
            headers: pollHdr,
            tags:    { name: 'payment/status' },
        });

        check(statusRes, { 'poll: status 200': (r) => r.status === 200 });

        const txnStatus = statusRes.json('status');
        if (txnStatus === 'COMPLETED' || txnStatus === 'FAILED') {
            done = true;
            break;
        }
    }

    pollLatency.add(Date.now() - startMs);
    if (done) txnCompleted.add(1);
}
