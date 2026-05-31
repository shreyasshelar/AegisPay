/**
 * Shared configuration for AegisPay k6 load tests.
 *
 * Override any value via k6 --env flag:
 *   k6 run --env BASE_URL=https://api.aegispay.io happy-path.js
 */

export const BASE_URL   = __ENV.BASE_URL   || 'http://localhost:8080';
export const KC_URL     = __ENV.KC_URL     || 'http://localhost:9080';
export const KC_REALM   = __ENV.KC_REALM   || 'aegispay';
export const KC_CLIENT  = __ENV.KC_CLIENT  || 'aegispay-api';

// Test users – pre-seeded in Keycloak dev realm
export const TEST_PAYER = {
    username: __ENV.PAYER_USER || 'loadtest_payer@aegispay.io',
    password: __ENV.PAYER_PASS || 'LoadTest@123',
};
export const TEST_PAYEE_ID = __ENV.PAYEE_ID || '00000000-0000-0000-0000-000000000002';

export const THRESHOLDS = {
    // At 500 RPS, p95 ≤ 800 ms, p99 ≤ 1500 ms, error rate < 1 %
    http_req_duration: ['p(95)<800', 'p(99)<1500'],
    http_req_failed:   ['rate<0.01'],
};
