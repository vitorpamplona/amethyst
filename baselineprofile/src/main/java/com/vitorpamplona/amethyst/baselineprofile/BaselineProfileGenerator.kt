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

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test

/**
 * Records the methods worth AOT-compiling, replacing the hand-authored
 * `amethyst/src/main/baseline-prof.txt`.
 *
 * What this captures and why: a symbol profile of a release build during cold-start
 * ingest found **42.9% of the DefaultDispatcher workers' CPU inside ART's nterp
 * interpreter** and only 10.5% in compiled app code — the app was spending 36x more
 * CPU interpreting bytecode than verifying signatures, because the shipped profile
 * covered only androidx/Compose and none of the relay client, decoder, LocalCache or
 * Jackson paths.
 *
 * So the journey is deliberately NOT just a startup trace. It launches cold and then
 * sits on the feed while events stream in, because the expensive path is ingest, and
 * ingest only runs once relays start delivering. Scrolling is included so the note
 * rendering path is captured too.
 *
 * Run with:
 *   ./gradlew :amethyst:generateBaselineProfile
 *
 * Requires a connected device on API 33+ (no root needed with Macrobenchmark
 * 1.2.0-alpha06 and higher), or an `aosp` Gradle Managed Device below that.
 */
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startupAndIngest() =
        rule.collect(packageName = PACKAGE) {
            pressHome()
            startActivityAndWait()

            // Let the relay pool connect and start delivering. The ingest burst is the
            // whole point of this profile; a startup-only journey would miss it.
            device.waitForIdle()
            Thread.sleep(INGEST_SETTLE_MS)

            // Exercise the feed rendering path over whatever arrived.
            val feed = device.findObject(By.scrollable(true))
            if (feed != null) {
                repeat(3) {
                    feed.scroll(Direction.DOWN, 1.0f)
                    device.waitForIdle()
                }
                feed.scroll(Direction.UP, 1.0f)
            }
            device.wait(Until.hasObject(By.pkg(PACKAGE).depth(0)), 5_000)
        }

    companion object {
        const val PACKAGE = "com.vitorpamplona.amethyst"

        /**
         * Long enough for relays to connect and deliver, short enough to stay inside the
         * memory ceiling of low-RAM test devices — an SM-T220 (3 GB) gets lmkd-killed
         * around 20 s into a release-build ingest.
         */
        const val INGEST_SETTLE_MS = 12_000L
    }
}
