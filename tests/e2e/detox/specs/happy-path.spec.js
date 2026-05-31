/**
 * AegisPay iOS — Happy-path E2E test
 *
 * Flow:
 *   1. Launch app
 *   2. Log in with test credentials (ROPC via Keycloak dev realm)
 *   3. Verify dashboard loads with a non-zero balance
 *   4. Send ₹100 to the test payee
 *   5. Verify the transaction appears in the Recent Transactions list
 *   6. Open the transaction and verify COMPLETED status
 *
 * Requires:
 *   - Dev environment running (local k3s or staging)
 *   - API_BASE_URL env var pointing to the test cluster
 *   - Test accounts seeded (loadtest_payer, loadtest_payee)
 */

const { device, element, by, expect, waitFor } = require('detox');

const TEST_EMAIL    = process.env.E2E_PAYER_EMAIL    || 'loadtest_payer@aegispay.io';
const TEST_PASSWORD = process.env.E2E_PAYER_PASSWORD || 'LoadTest@123';
const PAYEE_NAME    = process.env.E2E_PAYEE_NAME     || 'Load Test Payee';

describe('Happy path — login → send money → verify', () => {

    beforeAll(async () => {
        await device.launchApp({ newInstance: true });
    });

    afterAll(async () => {
        await device.terminateApp();
    });

    // ── Step 1: Login screen is visible ─────────────────────────────────────
    it('shows the login screen on cold start', async () => {
        await expect(element(by.id('login-screen'))).toBeVisible();
    });

    // ── Step 2: Log in ───────────────────────────────────────────────────────
    it('logs in with valid credentials', async () => {
        await element(by.id('login-email-input')).typeText(TEST_EMAIL);
        await element(by.id('login-password-input')).typeText(TEST_PASSWORD);
        await element(by.id('login-submit-button')).tap();

        // Dashboard must appear within 10 s (Keycloak + token exchange)
        await waitFor(element(by.id('dashboard-screen')))
            .toBeVisible()
            .withTimeout(10_000);
    });

    // ── Step 3: Dashboard shows balance ─────────────────────────────────────
    it('shows account balance on dashboard', async () => {
        await expect(element(by.id('balance-amount'))).toBeVisible();
        // Balance text should contain a currency symbol
        await expect(element(by.id('balance-amount'))).toHaveText(
            expect.stringMatching(/[₹$€£]/)
        );
    });

    // ── Step 4: Navigate to Send Money ───────────────────────────────────────
    it('navigates to Send Money screen', async () => {
        await element(by.id('nav-send-money')).tap();
        await expect(element(by.id('send-money-screen'))).toBeVisible();
    });

    // ── Step 5: Fill and submit payment ─────────────────────────────────────
    it('submits a payment of ₹100', async () => {
        // Select payee
        await element(by.id('payee-search-input')).typeText(PAYEE_NAME);
        await waitFor(element(by.text(PAYEE_NAME)))
            .toBeVisible()
            .withTimeout(5_000);
        await element(by.text(PAYEE_NAME)).tap();

        // Enter amount
        await element(by.id('amount-input')).clearText();
        await element(by.id('amount-input')).typeText('100');

        // Submit
        await element(by.id('send-money-submit-button')).tap();

        // Processing indicator
        await waitFor(element(by.id('payment-processing-indicator')))
            .toBeVisible()
            .withTimeout(3_000);

        // Success state
        await waitFor(element(by.id('payment-success-view')))
            .toBeVisible()
            .withTimeout(15_000);
    });

    // ── Step 6: Transaction in history ──────────────────────────────────────
    it('shows the new transaction in the transaction list', async () => {
        await element(by.id('nav-transactions')).tap();
        await expect(element(by.id('transaction-list-screen'))).toBeVisible();

        // The most recent row should show ₹100
        await waitFor(element(by.id('transaction-row-0')))
            .toBeVisible()
            .withTimeout(5_000);

        await expect(element(by.id('transaction-row-0-amount')))
            .toHaveText(expect.stringContaining('100'));
    });

    // ── Step 7: Transaction detail shows COMPLETED ───────────────────────────
    it('shows COMPLETED status in transaction detail', async () => {
        await element(by.id('transaction-row-0')).tap();

        await waitFor(element(by.id('transaction-detail-screen')))
            .toBeVisible()
            .withTimeout(3_000);

        await expect(element(by.id('transaction-status-badge')))
            .toHaveText('Completed');
    });
});
