/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.macrobenchmark

import android.os.SystemClock
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Macrobenchmark for the Notifications feed scroll.
 *
 * Measures real on-device frame timing ([FrameTimingMetric]) while scrolling the
 * notifications list, so we get true `frameDurationCpuMs` / `frameOverrunMs`
 * percentiles instead of the GPU-bound emulator numbers that `gfxinfo`/`atrace`
 * report.
 *
 * KNOWN ISSUE: on some environments [FrameTimingMetric] fails with "Observed no
 * renderthread slices in trace" on a random iteration. This was reproduced on both
 * an emulator and a Samsung SM-T220 (MediaTek), via both `am instrument` and
 * `connectedBenchmarkAndroidTest`, and is independent of the gesture/startup mode —
 * so it is NOT this test. Suspected to be the alpha `androidx.benchmark`
 * (1.5.0-alpha06) trace processing or hardware that doesn't emit the RenderThread
 * slices the library's trace query expects. Try a stable benchmark version and/or a
 * Pixel-class device.
 *
 * Prerequisites:
 *  - A device/emulator with a **logged-in account that has notifications**
 *    (the benchmark scrolls whatever the live Notifications tab shows; an empty
 *    account produces a trivial, meaningless trace).
 *  - The app is installed as the `playBenchmark` variant (handled automatically
 *    when you run this module's `connectedBenchmarkAndroidTest`).
 *
 * Run:
 *   ./gradlew :macrobenchmark:connectedBenchmarkAndroidTest
 *
 * Results (per-iteration frame metrics + percentiles) are printed to the test
 * output and written to a JSON under the module's build/outputs.
 */
@RunWith(AndroidJUnit4::class)
class NotificationScrollBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test
    fun scrollNotificationsFeed() =
        rule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            iterations = ITERATIONS,
            // No startupMode: do NOT kill/restart the app between iterations. This is a
            // steady-state scroll benchmark, not a startup one — keeping the process
            // alive means its RenderThread stays up and registered across every
            // iteration's trace. (With WARM restart, each fresh process races the
            // RenderThread coming up and the first trace can land with no RenderThread
            // slices, failing the whole run.)
            setupBlock = {
                startActivityAndWait()
                // The bottom-bar Notifications icon exposes the localized label as
                // its content description (AppBottomBar.NotifiableIcon), so we can
                // target it directly via UiAutomator.
                val notifTab = device.wait(Until.findObject(By.desc(NOTIFICATIONS_LABEL)), UI_TIMEOUT_MS)
                notifTab?.click()
                // Wait for the notifications list itself — targeted by its testTag
                // (exposed as a resource-id), NOT By.scrollable(true): a card can
                // embed its own scrollable widget (e.g. a Road event's map) that
                // would otherwise be grabbed and panned instead of the feed.
                device.wait(Until.hasObject(By.res(TARGET_PACKAGE, FEED_TAG)), UI_TIMEOUT_MS)
                device.waitForIdle()
                // WARM mode kills the app each iteration, so the feed re-aggregates
                // from cache/relays on every pass — async, and slow on older devices.
                // Wait until the list actually has cards before measuring; scrolling a
                // still-empty feed draws nothing and Macrobenchmark then fails with
                // "Observed no renderthread slices". A fixed sleep is not enough on a
                // slow tablet, so poll the live child count instead.
                val deadline = SystemClock.uptimeMillis() + FEED_LOAD_TIMEOUT_MS
                while (SystemClock.uptimeMillis() < deadline) {
                    val feed = device.findObject(By.res(TARGET_PACKAGE, FEED_TAG))
                    if (feed != null && feed.childCount >= MIN_FEED_CHILDREN) break
                    Thread.sleep(250)
                }
                device.waitForIdle()
                // Get OFF the top once (un-measured) with a single down-fling. Critical:
                // never drag the finger downward while the list is at the top — Android
                // reads that as pull-to-refresh, not a scroll, which reloads the feed and
                // starves the measured trace of real scroll frames. Landing one fling deep
                // means the measured oscillation below never touches the top again.
                swipeFeed(1, down = true)
            },
        ) {
            // Confirm we're on the loaded notifications feed before gesturing.
            device.findObject(By.res(TARGET_PACKAGE, FEED_TAG)) ?: return@measureRepeated
            // Oscillate inside the feed body: scroll down N, then back up N. Net drift is
            // ~zero, so across iterations (the app is kept alive) we never reach the
            // bottom (down-swipes that can't move = no frames) nor the top (up-swipes
            // that hit pull-to-refresh). Both directions are real scrolls that draw.
            swipeFeed(SCROLLS_PER_ITERATION, down = true)
            swipeFeed(SCROLLS_PER_ITERATION, down = false)
        }

    /**
     * Drags the notifications feed [times] times, all within the **left gutter** — the
     * ~50dp reaction-icon column every card reserves before its body. That strip has no
     * interactive children, so the vertical drag always reaches the parent LazyColumn
     * and never an embedded scrollable widget inside a card (e.g. a Road event's map).
     * A controlled coordinate swipe (not a velocity fling) also registers reliably as a
     * scroll across devices. [down] = true reveals older items; false scrolls back up.
     */
    private fun MacrobenchmarkScope.swipeFeed(
        times: Int,
        down: Boolean,
    ) {
        val density =
            InstrumentationRegistry
                .getInstrumentation()
                .context.resources.displayMetrics.density
        val gutterX = (GUTTER_DP * density).toInt()
        val hi = (device.displayHeight * 0.15).toInt()
        val lo = (device.displayHeight * 0.85).toInt()

        repeat(times) {
            if (down) {
                device.swipe(gutterX, lo, gutterX, hi, SWIPE_STEPS)
            } else {
                device.swipe(gutterX, hi, gutterX, lo, SWIPE_STEPS)
            }
            device.waitForIdle()
        }
    }

    companion object {
        private const val TARGET_PACKAGE = "com.vitorpamplona.amethyst.benchmark"
        private const val NOTIFICATIONS_LABEL = "Notifications"

        // Must match NotificationFeedTestTag in :amethyst CardFeedView.kt.
        private const val FEED_TAG = "notificationFeedList"
        private const val ITERATIONS = 8
        private const val SCROLLS_PER_ITERATION = 4
        private const val UI_TIMEOUT_MS = 5_000L

        // Wait (up to this long) for the feed to re-aggregate after each WARM restart,
        // until it shows at least MIN_FEED_CHILDREN laid-out rows (header + donation
        // card + a few notification cards) so we never measure a scroll of an empty list.
        private const val FEED_LOAD_TIMEOUT_MS = 12_000L
        private const val MIN_FEED_CHILDREN = 5

        // Left gutter (reaction-icon column) X offset — far enough in to clear the
        // system back-gesture edge zone, before any card body / interactive child.
        private const val GUTTER_DP = 50

        // Drag step count controls gesture duration (~5ms/step). Too FAST (≈8 steps /
        // 40ms) and a real device's touch sampler under-registers the gesture — it barely
        // scrolls. Too SLOW (≈40 steps) and it's a controlled drag with no fling momentum
        // — also short travel. ~24 steps (≈120ms) matches a brisk human flick that both
        // registers cleanly and imparts fling momentum, so the list travels ~a full
        // screen and plays a long deceleration animation (a rich stream of frames).
        private const val SWIPE_STEPS = 24
    }
}
