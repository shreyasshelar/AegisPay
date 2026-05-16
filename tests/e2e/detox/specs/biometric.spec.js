/**
 * AegisPay iOS — Biometric authentication E2E test
 *
 * Flow:
 *   1. Log in with password
 *   2. Background the app (simulates app leaving foreground)
 *   3. Re-open app — biometric lock screen appears
 *   4. Approve Face ID — app unlocks and dashboard is visible
 *   5. Fail Face ID — error message shown, try again button visible
 */

const { device, element, by, expect, waitFor } = require('detox');

const TEST_EMAIL    = process.env.E2E_PAYER_EMAIL    || 'loadtest_payer@aegispay.io';
const TEST_PASSWORD = process.env.E2E_PAYER_PASSWORD || 'LoadTest@123';

describe('Biometric lock — Face ID approve & reject', () => {

    beforeAll(async () => {
        await device.launchApp({ newInstance: true });
        // Log in first
        await element(by.id('login-email-input')).typeText(TEST_EMAIL);
        await element(by.id('login-password-input')).typeText(TEST_PASSWORD);
        await element(by.id('login-submit-button')).tap();
        await waitFor(element(by.id('dashboard-screen')))
            .toBeVisible()
            .withTimeout(10_000);
    });

    afterAll(async () => {
        await device.terminateApp();
    });

    it('shows biometric lock after app returns from background', async () => {
        await device.sendToHome();
        await device.launchApp({ newInstance: false });  // resume, don't restart

        await waitFor(element(by.id('biometric-lock-screen')))
            .toBeVisible()
            .withTimeout(5_000);
    });

    it('unlocks with successful Face ID', async () => {
        // Detox simulator API to approve biometrics
        await device.matchFace();

        await waitFor(element(by.id('dashboard-screen')))
            .toBeVisible()
            .withTimeout(5_000);
    });

    it('shows error on failed Face ID', async () => {
        // Background and re-open to trigger lock again
        await device.sendToHome();
        await device.launchApp({ newInstance: false });
        await waitFor(element(by.id('biometric-lock-screen')))
            .toBeVisible()
            .withTimeout(5_000);

        // Reject biometrics
        await device.unmatchFace();

        await waitFor(element(by.id('biometric-error-message')))
            .toBeVisible()
            .withTimeout(3_000);

        await expect(element(by.id('biometric-try-again-button'))).toBeVisible();
    });
});
