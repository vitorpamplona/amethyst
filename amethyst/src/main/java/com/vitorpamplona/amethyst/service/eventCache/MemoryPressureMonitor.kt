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
package com.vitorpamplona.amethyst.service.eventCache

import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Watches the JVM heap and proactively triggers a cache trim before the process runs out of memory.
 *
 * The app already trims [LocalCache][com.vitorpamplona.amethyst.model.LocalCache] from
 * `Application.onTrimMemory`, but Android 15 (API 35) deprecated the foreground trim levels
 * (`TRIM_MEMORY_RUNNING_*`, `TRIM_MEMORY_UI_HIDDEN`). A foreground app that keeps scrolling its feed
 * therefore receives no system trim callback, and the in-memory event store grows until the heap hits
 * its hard cap and the process OOMs (often surfacing as a `CoroutinesInternalError` thrown from
 * whichever coroutine happens to allocate next). This monitor restores a foreground trigger that is
 * independent of the system callback.
 */
class MemoryPressureMonitor(
    private val checkIntervalMs: Long = DEFAULT_CHECK_INTERVAL_MS,
    private val highWatermark: Double = DEFAULT_HIGH_WATERMARK,
    private val usedHeapBytes: () -> Long = { Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() } },
    private val maxHeapBytes: () -> Long = { Runtime.getRuntime().maxMemory() },
    private val onPressure: suspend () -> Unit,
) {
    suspend fun run() {
        while (currentCoroutineContext().isActive) {
            delay(checkIntervalMs)

            val used = usedHeapBytes()
            val max = maxHeapBytes()
            if (isUnderPressure(used, max, highWatermark)) {
                Log.w("MemoryPressureMonitor") {
                    "Heap at ${used / (1024 * 1024)}/${max / (1024 * 1024)} MB " +
                        "(>= ${(highWatermark * 100).toInt()}%). Trimming the event cache."
                }
                onPressure()
            }
        }
    }

    companion object {
        // Checked frequently enough to react before a fast scroll fills the heap, but cheap: the
        // probe is a couple of Runtime reads and a trim only fires when actually over the watermark.
        const val DEFAULT_CHECK_INTERVAL_MS = 30_000L

        // Leaves headroom below the hard cap so the trim (and the GC it enables) has room to work
        // before any single allocation is forced to fail.
        const val DEFAULT_HIGH_WATERMARK = 0.85

        fun isUnderPressure(
            usedBytes: Long,
            maxBytes: Long,
            watermark: Double,
        ): Boolean {
            if (maxBytes <= 0L) return false
            return usedBytes.toDouble() / maxBytes.toDouble() >= watermark
        }
    }
}
