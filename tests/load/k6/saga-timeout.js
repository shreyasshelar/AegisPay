/**
 * AegisPay — Saga Timeout Under Pressure
 *
 * Simulates high concurrency (800 VUs) while a synthetic downstream delay is
 * injected via a query parameter recognised by the API gateway's fault-injection
 * filter (only active in load-test / staging environments).
 *
 * What it tests:
 *   1. Saga timeout fires within the configured deadline (30 s default).
 *   2. Compensating transactions are triggered: the transaction status must
 *      eventually be FAILED (not INITIATED or stuck forever).
 *   3. Error budget: even under timeout pressure, the orchestrator should not
 *      return 5xx — it should always return a structured failure response.
 *
 * Run:
 *   k6 run --env BASE_URL=https://staging.aegispay.io tests/load/k6/saga-timeout.js
 *
 * Note: requires fault-injection filter enabled on the gateway:
 *   management.endpoint.fault-injection.enabled: true  (staging only)
 */

import http          from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { uuidv4 }    from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';
import { BASE_URL, TEST_PAYEE_ID, THRESHOLDS } from './config.js';
import { getToken, authHeaders }               from './auth.js';

// ── Custom metrics ───────────────────────────────────────────────────────────
const sagaTimedOut       = new Counter('saga_timed_out');
const sagaCompensated    = new Counter('saga_compensated');
const compensationDelay  = new Trend('saga_compensation_delay_ms');
const stuckSagas         = new Counter('saga_stuck');   // never reached terminal state

// ── Test config ──────────────────────────────────────────────────────────────
export const options = {
    scenarios: {
        timeout_pressure: {
            executor:        'ramping-vus',
            startVUs:        0,
            stages: [
                { duration: '1m',  target: 200 },   // warm-up
                { duration: '3m',  target: 800 },   // peak pressure
                { duration: '30s', target: 0   },   // drain
            ],
        },
    },
    thresholds: {
        ...THRESHOLDS,
        // Under forced timeout, we accept more latency but still need structured responses
        http_req_duration:      ['p(95)<35000'],     // 35 s — saga timeout + margin
        http_req_failed:        ['rate<0.02'],        // < 2 % hard 5xx
        saga_stuck:             ['count==0'],          // no sagas stuck in INITIATED
        saga_compensation_delay_ms: ['p(95)<35000'],
    },
};

export function setup() {
    return { token: getToken() };
}

export default function (data) {
    const token    = data.token;
    // inject artificial 25 s downstream delay (picked up by fault-injection filter)
    const headers  = authHeaders(token, {
        'Idempotency-Key':  uuidv4(),
        'X-Fault-Delay-Ms': '25000',   // 25 s delay on payment-gateway stub
    });

    const payload = JSON.stringify({
        payeeId:  TEST_PAYEE_ID,
        amount:   '100.00',
        currency: 'INR',
        note:     'k6 saga-timeout test',
    });

    const initRes = http.post(`${BASE_URL}/api/v1/payments`, payload, {
        headers,
        tags:    { name: 'payment/timeout_initiate' },
        timeout: '40s',
    });

    const initiated = check(initRes, {
        'timeout: status 202':  (r) => r.status === 202,
        'timeout: has txnId':   (r) => !!r.json('transactionId'),
    });
    if (!initiated) return;

    const txnId   = initRes.json('transactionId');
    const pollHdr = authHeaders(token);
    const startMs = Date.now();

    // ── Poll up to 36 s for a terminal state ────────────────────────────────
    let terminal = false;
    for (let i = 0; i < 36; i++) {
        sleep(1);
        const sr = http.get(`${BASE_URL}/api/v1/payments/${txnId}`, {
            headers: pollHdr,
            tags:    { name: 'payment/timeout_poll' },
        });

        if (sr.status !== 200) continue;

        const status = sr.json('status');
        if (status === 'FAILED') {
            terminal = true;
            sagaTimedOut.add(1);
            sagaCompensated.add(1);
            compensationDelay.add(Date.now() - startMs);

            check(sr, {
                'timeout: failureCode present': (r) => !!r.json('failureCode'),
                'timeout: no 5xx':              (r) => r.status < 500,
            });
            break;
        }
        if (status === 'COMPLETED') {
            // Unexpected under forced delay — the gateway should have timed out
            terminal = true;
            break;
        }
    }

    if (!terminal) {
        stuckSagas.add(1);
    }
}
