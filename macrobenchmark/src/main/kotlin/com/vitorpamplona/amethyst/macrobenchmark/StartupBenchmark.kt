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
@file:OptIn(androidx.benchmark.macro.ExperimentalMetricApi::class)

package com.vitorpamplona.amethyst.macrobenchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measures Amethyst's cold-start path, including the long tail after first frame
 * (relay connect storm + LocalCache event flood). The 6 `Boot:` markers are
 * emitted by `com.vitorpamplona.amethyst.debug.BootTrace` and observers wired in
 * `Amethyst.onCreate`.
 *
 * Run:
 *   ./gradlew :macrobenchmark:connectedBenchmarkAndroidTest
 *
 * On a logged-out device the 4 async markers (FirstAccountLoaded, RelaysConnected,
 * FirstHomeFeedFrame, HomeFeedSteady) will not fire. The 2 sync markers
 * (AppModulesCtor, Initiate) always fire. See README.md for test-account setup.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartup() = startup(StartupMode.COLD)

    @Test
    fun warmStartup() = startup(StartupMode.WARM)

    private fun startup(mode: StartupMode) {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics =
                listOf(
                    StartupTimingMetric(),
                    FrameTimingMetric(),
                    // Use Mode.First for one-shot async markers (each fires at most
                    // once per launch); Sum/Min/Max work for sync sections too.
                    TraceSectionMetric("Boot:AppModulesCtor", TraceSectionMetric.Mode.Sum),
                    TraceSectionMetric("Boot:Initiate", TraceSectionMetric.Mode.Sum),
                    TraceSectionMetric("Boot:FirstAccountLoaded", TraceSectionMetric.Mode.First),
                    TraceSectionMetric("Boot:RelaysConnected", TraceSectionMetric.Mode.First),
                    TraceSectionMetric("Boot:FirstHomeFeedFrame", TraceSectionMetric.Mode.First),
                    TraceSectionMetric("Boot:HomeFeedSteady", TraceSectionMetric.Mode.First),
                ),
            iterations = 5,
            startupMode = mode,
        ) {
            pressHome()
            startActivityAndWait()

            // Wait long enough for HomeFeedSteady to fire on a logged-in device.
            // On a logged-out device this just sleeps until the timeout and the
            // async markers report as missing.
            device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE).depth(0)), 30_000)
        }
    }

    companion object {
        // :amethyst sets applicationIdSuffix = ".benchmark" on the benchmark build
        // type, so the installed app id is com.vitorpamplona.amethyst.benchmark.
        const val TARGET_PACKAGE = "com.vitorpamplona.amethyst.benchmark"
    }
}
