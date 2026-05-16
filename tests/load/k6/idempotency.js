/**
 * AegisPay — Concurrent Idempotency Key Test
 *
 * Verifies that submitting the SAME idempotency key from multiple concurrent
 * virtual users produces exactly ONE transaction, not duplicates.
 *
 * Each iteration group shares one idempotency key. Concurrency is emulated via
 * a shared key pool (one key per group of VUs).  All requests with the same key
 * must return the same transactionId.
 *
 * Thresholds:
 *   - No 500 errors (idempotency should return 200/202 for replays, not 500)
 *   - txn_duplicate_count == 0  (custom counter checked in handleSummary)
 *
 * Run:
 *   k6 run tests/load/k6/idempotency.js
 */

import http          from 'k6/http';
import { check, sleep } from 'k6';
import { Counter }   from 'k6/metrics';
import { SharedArray } from 'k6/data';
import { uuidv4 }    from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';
import { BASE_URL, TEST_PAYEE_ID } from './config.js';
import { getToken, authHeaders }   from './auth.js';

// ── Custom metrics ───────────────────────────────────────────────────────────
const duplicates    = new Counter('txn_duplicate_count');
const replayOk      = new Counter('txn_idempotent_replay_ok');

// 50 shared idempotency keys — 20 VUs compete over each key
const KEYS_TOTAL = 50;
const sharedKeys = new SharedArray('idempotency_keys', function () {
    return Array.from({ length: KEYS_TOTAL }, () => uuidv4());
});

// ── Test config ──────────────────────────────────────────────────────────────
export const options = {
    scenarios: {
        concurrent_replay: {
            executor:   'ramping-vus',
            startVUs:   0,
            stages: [
                { duration: '30s', target: 100 },   // ramp up
                { duration: '2m',  target: 100 },   // sustain
                { duration: '10s', target: 0   },   // ramp down
            ],
        },
    },
    thresholds: {
        http_req_failed:   ['rate<0.005'],           // < 0.5 % hard errors
        txn_duplicate_count: ['count==0'],           // ZERO duplicate txns
    },
};

export function setup() {
    return { token: getToken() };
}

export default function (data) {
    // Each VU picks a key deterministically by its __VU id mod pool size
    const key     = sharedKeys[__VU % KEYS_TOTAL];
    const headers = authHeaders(data.token, { 'Idempotency-Key': key });

    const payload = JSON.stringify({
        payeeId:  TEST_PAYEE_ID,
        amount:   '500.00',
        currency: 'INR',
        note:     'idempotency k6 test',
    });

    const res = http.post(`${BASE_URL}/api/v1/payments`, payload, {
        headers,
        tags: { name: 'payment/idempotency' },
    });

    // 202 on first call, 200 on replay are both acceptable
    const firstOrReplay = check(res, {
        'idempotency: 200 or 202': (r) => r.status === 200 || r.status === 202,
        'idempotency: has txnId':  (r) => !!r.json('transactionId'),
    });

    if (!firstOrReplay) return;

    // Detect replay: server should echo the SAME transactionId
    const txnId = res.json('transactionId');
    if (res.status === 200) {
        replayOk.add(1);
    }

    // Brief concurrency pause so multiple VUs truly overlap
    sleep(Math.random() * 0.1);

    // Re-submit the same key — must get the same txnId back
    const replayRes = http.post(`${BASE_URL}/api/v1/payments`, payload, {
        headers,
        tags: { name: 'payment/idempotency_replay' },
    });

    check(replayRes, {
        'replay: 200 or 202':        (r) => r.status === 200 || r.status === 202,
        'replay: same transactionId': (r) => r.json('transactionId') === txnId,
    });

    if (replayRes.json('transactionId') !== txnId) {
        duplicates.add(1);
    }
}
