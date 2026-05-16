/**
 * AegisPay iOS — Offline queue + reconnect E2E test
 *
 * Flow:
 *   1. Log in
 *   2. Disable network on simulator
 *   3. Attempt to send ₹200 → payment is queued offline
 *   4. Verify offline-queued banner appears in the UI
 *   5. Re-enable network
 *   6. WorkManager/background sync fires → payment completes
 *   7. Verify transaction appears in history as COMPLETED
 *
 * Note: iOS offline queue uses URLSession background tasks; the test polls
 * for the transaction to appear in the transaction list.
 */

const { device, element, by, expect, waitFor } = require('detox');

const TEST_EMAIL    = process.env.E2E_PAYER_EMAIL    || 'loadtest_payer@aegispay.io';
const TEST_PASSWORD = process.env.E2E_PAYER_PASSWORD || 'LoadTest@123';
const PAYEE_NAME    = process.env.E2E_PAYEE_NAME     || 'Load Test Payee';

describe('Offline → reconnect → sync', () => {

    beforeAll(async () => {
        await device.launchApp({ newInstance: true });
        // Log in
        await element(by.id('login-email-input')).typeText(TEST_EMAIL);
        await element(by.id('login-password-input')).typeText(TEST_PASSWORD);
        await element(by.id('login-submit-button')).tap();
        await waitFor(element(by.id('dashboard-screen')))
            .toBeVisible()
            .withTimeout(10_000);
    });

    afterAll(async () => {
        // Always restore network before next test
        await device.setStatusBar({ connectivity: 'wifi' });
        await device.terminateApp();
    });

    it('queues a payment when device is offline', async () => {
        // Simulate offline
        await device.setStatusBar({ connectivity: 'none' });

        await element(by.id('nav-send-money')).tap();
        await element(by.id('payee-search-input')).typeText(PAYEE_NAME);
        await waitFor(element(by.text(PAYEE_NAME))).toBeVisible().withTimeout(3_000);
        await element(by.text(PAYEE_NAME)).tap();
        await element(by.id('amount-input')).typeText('200');
        await element(by.id('send-money-submit-button')).tap();

        // App should show offline-queued state (not an error)
        await waitFor(element(by.id('payment-queued-offline-banner')))
            .toBeVisible()
            .withTimeout(5_000);
    });

    it('syncs and completes the payment when network returns', async () => {
        // Re-enable network
        await device.setStatusBar({ connectivity: 'wifi' });

        // Background sync triggers — wait for success notification or transaction in list
        await element(by.id('nav-transactions')).tap();

        // Poll for up to 30 s for the transaction to appear
        await waitFor(element(by.id('transaction-row-0-amount')))
            .toHaveText(expect.stringContaining('200'))
            .withTimeout(30_000);

        await element(by.id('transaction-row-0')).tap();
        await expect(element(by.id('transaction-status-badge'))).toHaveText('Completed');
    });
});
