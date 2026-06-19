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
package com.vitorpamplona.amethyst.desktop.benchmark

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Phase 3.2: single-JVM warm benchmark for the cold-boot scenario.
 *
 * Runs [WARMUP] discarded iterations and [ITERATIONS] measured iterations
 * of [LaunchScenario.coldBoot], collects the median + min + IQR of each
 * marker, and atomically writes a report file under
 * `desktopApp/build/benchmarks/`. The "cold" half of the harness (forking
 * a fresh JVM per sample) is deferred to a shell-script driver; this
 * single-JVM variant gives meaningful before/after numbers for fixes that
 * stay in the launch-path code we already exercise (icon decode, feed
 * bootstrap gate, etc.).
 *
 * Disabled by default — set the `AMETHYST_BENCH=true` environment variable
 * to run it (so a normal `./gradlew :desktopApp:test` stays fast).
 * Invoked directly via:
 *
 *   AMETHYST_BENCH=true ./gradlew :desktopApp:test \
 *       --tests "*LaunchBenchmark.run" --rerun-tasks
 *
 * See desktopApp/plans/2026-06-17-feat-app-launch-optimization-plan.md
 * § Phase 3.2.
 */
class LaunchBenchmark {
    @Test
    fun run() {
        if (System.getenv("AMETHYST_BENCH") != "true" && System.getProperty("amethyst.bench") != "true") {
            // Skip silently. Set AMETHYST_BENCH=true (or pass
            // -Damethyst.bench=true to the test JVM) to run it.
            println("LaunchBenchmark: skipped — set AMETHYST_BENCH=true to run")
            return
        }

        // Drop the warmup samples on the floor so the measured set isn't
        // biased by classloader cost or JIT C1 compilation.
        repeat(WARMUP) {
            LaunchScenario.coldBoot()
        }

        val samples = (1..ITERATIONS).map { LaunchScenario.coldBoot() }

        val report = buildReport(samples)
        writeReport(report)
        println(report)

        val nEventsSamples = samples.mapNotNull { it.markers[LaunchMarkers.T_N_EVENTS] }
        assertTrue(
            nEventsSamples.size >= ITERATIONS / 2,
            "At least half the iterations must reach T_N_EVENTS; got ${nEventsSamples.size}/$ITERATIONS",
        )
    }

    private fun buildReport(samples: List<LaunchScenario.Result>): String {
        val header =
            buildString {
                appendLine("# LaunchBenchmark report")
                appendLine("# date            ${java.time.Instant.now()}")
                appendLine("# jvm             ${System.getProperty("java.version")} ${System.getProperty("java.vendor")}")
                appendLine("# os              ${System.getProperty("os.name")} ${System.getProperty("os.version")} ${System.getProperty("os.arch")}")
                appendLine("# cpus            ${Runtime.getRuntime().availableProcessors()}")
                appendLine("# max-heap-mb     ${Runtime.getRuntime().maxMemory() / 1024 / 1024}")
                appendLine("# git-sha         ${gitSha()}")
                appendLine("# iterations      $ITERATIONS (after $WARMUP warmup, discarded)")
                appendLine("# fork-mode       single-JVM (cold-fork driver deferred)")
                appendLine()
            }

        val markerNames =
            listOf(
                LaunchMarkers.T_ACCOUNT_LOGGED_IN,
                LaunchMarkers.T_FIRST_EVENT,
                LaunchMarkers.T_N_EVENTS,
            )

        val rows =
            markerNames.map { name ->
                val vals = samples.mapNotNull { it.markers[name]?.inWholeMicroseconds }
                val md = vals.median()
                val mn = vals.minOrNull() ?: 0
                val mx = vals.maxOrNull() ?: 0
                val q1 = vals.percentile(25.0)
                val q3 = vals.percentile(75.0)
                "%-30s n=%d  min=%6.2fms  q1=%6.2fms  median=%6.2fms  q3=%6.2fms  max=%6.2fms".format(
                    name,
                    vals.size,
                    mn / 1000.0,
                    q1 / 1000.0,
                    md / 1000.0,
                    q3 / 1000.0,
                    mx / 1000.0,
                )
            }

        val eventsCounts = samples.map { it.eventsConsumed }
        val tail =
            buildString {
                appendLine()
                appendLine("# events-consumed per iteration: $eventsCounts")
            }

        return header + rows.joinToString("\n") + tail
    }

    private fun writeReport(report: String) {
        val sha = gitSha().take(10)
        val dir = File("build/benchmarks").also { it.mkdirs() }
        val target = File(dir, "launch-$sha.txt")
        val tmp = File(dir, "launch-$sha.tmp")
        tmp.writeText(report)
        Files.move(
            tmp.toPath(),
            target.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
        println("LaunchBenchmark: report at ${target.absolutePath}")
    }

    private fun gitSha(): String =
        runCatching {
            val proc =
                ProcessBuilder("git", "rev-parse", "HEAD")
                    .redirectErrorStream(true)
                    .start()
            proc.waitFor()
            proc.inputStream
                .bufferedReader()
                .readText()
                .trim()
        }.getOrDefault("unknown")

    companion object {
        private const val WARMUP = 2
        private const val ITERATIONS = 5
    }
}

private fun List<Long>.median(): Long {
    if (isEmpty()) return 0
    val sorted = sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 0) {
        (sorted[mid - 1] + sorted[mid]) / 2
    } else {
        sorted[mid]
    }
}

private fun List<Long>.percentile(p: Double): Long {
    if (isEmpty()) return 0
    val sorted = sorted()
    val idx = ((sorted.size - 1) * p / 100.0).toInt().coerceIn(0, sorted.lastIndex)
    return sorted[idx]
}
