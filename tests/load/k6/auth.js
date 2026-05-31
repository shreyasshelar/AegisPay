/**
 * Keycloak OIDC token helper.
 *
 * Calls the Resource Owner Password Credentials flow to obtain a bearer token.
 * For load tests only — ROPC is disabled in production Keycloak realms.
 *
 * Usage:
 *   import { getToken } from './auth.js';
 *   const token = getToken();
 *   const headers = { Authorization: `Bearer ${token}`, ... };
 */

import http  from 'k6/http';
import { check } from 'k6';
import { KC_URL, KC_REALM, KC_CLIENT, TEST_PAYER } from './config.js';

const TOKEN_URL = `${KC_URL}/realms/${KC_REALM}/protocol/openid-connect/token`;

/**
 * Fetch a fresh access token.  Call once per VU in the setup() or init phase,
 * then reuse within the iteration (tokens are valid for 5 minutes by default).
 */
export function getToken(username = TEST_PAYER.username, password = TEST_PAYER.password) {
    const res = http.post(TOKEN_URL, {
        grant_type:    'password',
        client_id:     KC_CLIENT,
        username:      username,
        password:      password,
    }, { tags: { name: 'auth/token' } });

    check(res, { 'auth: got 200': (r) => r.status === 200 });
    return res.json('access_token');
}

export function authHeaders(token, extraHeaders = {}) {
    return Object.assign({
        'Authorization': `Bearer ${token}`,
        'Content-Type':  'application/json',
        'X-Tenant-Id':   'loadtest',
    }, extraHeaders);
}
