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
package com.vitorpamplona.amethyst.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import org.junit.Rule
import org.junit.Test

/**
 * Frame timing of the first scroll down the home feed after a cold start: the pass that composes
 * every card for the first time, and the one a feed's cache window (`FeedCacheWindow`, composing a
 * screen of notes ahead in idle time) is meant to speed up.
 *
 * Needs the device logged in to an account whose home feed has notes (it measures whatever that feed
 * shows, so compare builds on the same device and account, back to back). Compare two builds by
 * running it on each:
 *
 *   ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.vitorpamplona.amethyst.baselineprofile.FeedScrollBenchmark
 *
 * The results (frameDurationCpuMs and frameOverrunMs, P50 to P99) are printed and written to
 * `baselineprofile/build/outputs/connected_android_test_additional_output/`.
 */
class FeedScrollBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun firstScrollDownHome() =
        rule.measureRepeated(
            packageName = BaselineProfileGenerator.PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            // The baseline profile the release ships with, as users run it.
            compilationMode = CompilationMode.Partial(),
            startupMode = StartupMode.COLD,
            iterations = ITERATIONS,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                // Relays connect and the feed fills, as a user reads the top of it.
                device.waitForIdle()
                Thread.sleep(SETTLE_MS)
            },
        ) {
            val feed = device.findObject(By.scrollable(true)) ?: error("no scrollable feed: is the device logged in?")
            // Keep the gestures off the system navigation area.
            feed.setGestureMargin(device.displayWidth / 5)
            repeat(FLINGS) {
                feed.fling(Direction.DOWN)
                device.waitForIdle()
            }
        }

    companion object {
        const val ITERATIONS = 8
        const val FLINGS = 4

        /** As long as the profile journey waits, which low-RAM devices survive (BaselineProfileGenerator). */
        const val SETTLE_MS = BaselineProfileGenerator.INGEST_SETTLE_MS
    }
}
