package com.aegispay.android.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates a Baseline Profile for AegisPay.
 *
 * A Baseline Profile pre-compiles critical code paths (startup, navigation,
 * dashboard rendering) into native machine code so the app starts faster and
 * the first few frames are smooth — no JIT warm-up needed.
 *
 * Run with:
 *   ./gradlew :apps:android:macrobenchmark:generateBaselineProfile
 *
 * The output `baseline-prof.txt` should be committed to
 *   apps/android/app/src/main/baseline-prof.txt
 * and is picked up automatically by the Android Gradle Plugin at build time.
 *
 * Reference: https://developer.android.com/topic/performance/baselineprofiles
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generateBaselineProfile() = baselineProfileRule.collect(
        packageName = "com.aegispay.android",
    ) {
        // ── Startup ─────────────────────────────────────────────────────────
        // Press Home, then cold-start the app.
        pressHome()
        startActivityAndWait()

        // ── Dashboard ────────────────────────────────────────────────────────
        // Wait for the dashboard to settle (balance card + transaction list loaded).
        device.waitForIdle()

        // ── Navigation: Transactions ─────────────────────────────────────────
        // Navigate to the transaction list to warm up list rendering.
        device.findObject(
            androidx.test.uiautomator.By.desc("History")
        )?.click()
        device.waitForIdle()

        // ── Navigation: Send Money ────────────────────────────────────────────
        device.pressBack()
        device.waitForIdle()
        device.findObject(
            androidx.test.uiautomator.By.desc("Send")
        )?.click()
        device.waitForIdle()

        // ── Navigation: Profile ───────────────────────────────────────────────
        device.pressBack()
        device.waitForIdle()
        device.findObject(
            androidx.test.uiautomator.By.desc("Profile")
        )?.click()
        device.waitForIdle()

        // Return to home so the profile is clean for the next iteration.
        device.pressBack()
    }
}
