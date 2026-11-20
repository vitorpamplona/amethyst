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
package com.vitorpamplona.amethyst.desktop.service.bench

import java.util.Locale
import kotlin.math.ceil

/**
 * What one scroll pass measured, from the start time of each frame it drew and the UI thread's
 * busy time between consecutive frames.
 *
 * [intervalsMs] are the gaps between frame starts: at 60 Hz a smooth scroll shows ~16.7 ms, and each
 * gap past [JANK_MS] is a frame the screen did not get on time (a stutter the user sees).
 * [busyMs] is how long the UI thread (AWT's event dispatch thread, where Compose composes, lays
 * out and draws) ran in each of those gaps: the desktop's counterpart of Android's
 * `frameDurationCpuMs`. [uiBusyPercent] is its sum over the pass's length. [images] is where the
 * images the pass asked for came from (see [ImageLoadStats]).
 */
class PassStats(
    val name: String,
    val frames: Int,
    val seconds: Double,
    val intervalsMs: DoubleArray,
    val busyMs: DoubleArray,
    val gcCount: Long,
    val gcMs: Long,
    val jitMs: Long,
    val images: ImageLoadStats.Counts = ImageLoadStats.Counts.NONE,
) {
    val fps: Double get() = if (seconds > 0) intervalsMs.size / seconds else 0.0

    fun intervalPercentile(p: Double) = percentile(intervalsMs, p)

    fun busyPercentile(p: Double) = percentile(busyMs, p)

    /** Percent of frames whose gap went past [thresholdMs]. */
    fun percentOver(thresholdMs: Double): Double = if (intervalsMs.isEmpty()) 0.0 else 100.0 * intervalsMs.count { it > thresholdMs } / intervalsMs.size

    val uiBusyPercent: Double get() = if (seconds > 0) 100.0 * busyMs.sum() / (seconds * 1000) else 0.0

    companion object {
        /** A frame that took more than one and a half 60 Hz periods: it missed its vsync. */
        const val JANK_MS = 25.0

        /** A frame long enough to read as a hitch, not a dropped frame: three periods. */
        const val HITCH_MS = 50.0

        val HEADER =
            listOf(
                "pass",
                "frames",
                "seconds",
                "fps",
                "interval_p50",
                "interval_p90",
                "interval_p95",
                "interval_p99",
                "interval_max",
                "jank_pct",
                "hitch_pct",
                "busy_p50",
                "busy_p90",
                "busy_p99",
                "ui_busy_pct",
                "gc_count",
                "gc_ms",
                "jit_ms",
                "img_loads",
                "img_mem_pct",
                "img_disk",
                "img_net",
                "img_failed",
                "img_load_p50",
                "img_load_p90",
            )

        /**
         * The stats of frames that started at [startNanos] (ascending), with [busyNanos][i] the UI
         * thread's busy time between frame i-1 and frame i. The first frame only sets the baseline.
         */
        fun of(
            name: String,
            startNanos: LongArray,
            busyNanos: LongArray,
            gcCount: Long,
            gcMs: Long,
            jitMs: Long,
            images: ImageLoadStats.Counts = ImageLoadStats.Counts.NONE,
        ): PassStats {
            require(startNanos.size == busyNanos.size) { "${startNanos.size} frames but ${busyNanos.size} busy times" }
            if (startNanos.size < 2) return PassStats(name, startNanos.size, 0.0, DoubleArray(0), DoubleArray(0), gcCount, gcMs, jitMs, images)
            val intervals = DoubleArray(startNanos.size - 1) { (startNanos[it + 1] - startNanos[it]) / 1e6 }
            val busy = DoubleArray(startNanos.size - 1) { busyNanos[it + 1] / 1e6 }
            val seconds = (startNanos.last() - startNanos.first()) / 1e9
            return PassStats(name, startNanos.size, seconds, intervals, busy, gcCount, gcMs, jitMs, images)
        }

        /** Nearest-rank percentile ([p] in 0..100) of [values]; 0 when there are none. */
        fun percentile(
            values: DoubleArray,
            p: Double,
        ): Double {
            if (values.isEmpty()) return 0.0
            val sorted = values.sortedArray()
            val rank = ceil(p / 100.0 * sorted.size).toInt().coerceIn(1, sorted.size)
            return sorted[rank - 1]
        }
    }

    fun row(): List<String> =
        listOf(
            name,
            frames.toString(),
            fmt(seconds, 2),
            fmt(fps, 1),
            fmt(intervalPercentile(50.0), 1),
            fmt(intervalPercentile(90.0), 1),
            fmt(intervalPercentile(95.0), 1),
            fmt(intervalPercentile(99.0), 1),
            fmt(intervalsMs.maxOrNull() ?: 0.0, 1),
            fmt(percentOver(JANK_MS), 1),
            fmt(percentOver(HITCH_MS), 1),
            fmt(busyPercentile(50.0), 1),
            fmt(busyPercentile(90.0), 1),
            fmt(busyPercentile(99.0), 1),
            fmt(uiBusyPercent, 1),
            gcCount.toString(),
            gcMs.toString(),
            jitMs.toString(),
            images.total.toString(),
            fmt(images.memoryPercent, 1),
            images.disk.toString(),
            images.network.toString(),
            images.failed.toString(),
            fmt(percentile(images.loadMs, 50.0), 1),
            fmt(percentile(images.loadMs, 90.0), 1),
        )

    // A dot for the decimal point whatever the machine's locale: the report is a TSV.
    private fun fmt(
        value: Double,
        decimals: Int,
    ): String = String.format(Locale.ROOT, "%.${decimals}f", value)
}
