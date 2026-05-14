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
package com.vitorpamplona.amethyst.service.images

import coil3.annotation.ExperimentalCoilApi
import coil3.disk.DiskCache
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.sync.Mutex

/**
 * Drains Coil's disk cache below [targetFraction] of `maxSize`, by calling
 * `DiskCache.remove(key)` for the oldest keys recorded in [keyLog]. Called
 * from a background coroutine on a fixed cadence (e.g. every 5 minutes) and
 * on `onTrimMemory`.
 *
 * The point is to keep `size < maxSize` so Coil's internal `trimToSize()`
 * never fires during `commit()`. Inline eviction is what stalls IO on a
 * saturated cache; moving it out of the write path is the whole reason this
 * class exists.
 *
 * On every run:
 * 1. Flush [keyLog]'s pending writes so the snapshot is current.
 * 2. If `size < targetFraction * maxSize`, exit (nothing to do).
 * 3. Walk the log oldest-first, calling `remove(key)` until
 *    `size < floorFraction * maxSize`. The hysteresis gap between target and
 *    floor gives bursty writes some headroom before the next run is needed.
 *
 * Keys that the trimmer doesn't know about (entries written before this code
 * was deployed, or after the in-memory map filled to capacity) stay on disk
 * until Coil's own internal trim notices them — that's the safety net.
 */
@OptIn(ExperimentalCoilApi::class)
class CoilDiskTrimmer(
    private val diskCache: () -> DiskCache,
    private val keyLog: KeyAccessLog,
    private val targetFraction: Double = 0.70,
    private val floorFraction: Double = 0.55,
) {
    companion object {
        private const val TAG = "CoilDiskTrimmer"
    }

    private val runMutex = Mutex()

    init {
        require(floorFraction < targetFraction) { "floor must be < target" }
        require(targetFraction in 0.0..1.0) { "target out of range" }
        require(floorFraction in 0.0..1.0) { "floor out of range" }
    }

    /**
     * Run one trim pass. No-op if another pass is already running.
     */
    suspend fun trim() {
        if (!runMutex.tryLock()) return
        try {
            keyLog.flushNow()
            val cache = diskCache()
            val maxSize = cache.maxSize
            if (maxSize <= 0L) return
            val target = (maxSize * targetFraction).toLong()
            val floor = (maxSize * floorFraction).toLong()
            val before = cache.size
            if (before < target) {
                Log.d(TAG) { "size=${before / KB} KB < target=${target / KB} KB; nothing to do" }
                return
            }

            val candidates = keyLog.snapshotOldestFirst()
            if (candidates.isEmpty()) {
                Log.d(TAG) { "size=${before / KB} KB ≥ target=${target / KB} KB but no known keys; falling back to Coil's inline trim" }
                return
            }

            val started = System.currentTimeMillis()
            var evicted = 0
            for (entry in candidates) {
                if (cache.size <= floor) break
                if (cache.remove(entry.key)) evicted++
            }
            val after = cache.size
            val elapsed = System.currentTimeMillis() - started
            Log.d(TAG) {
                "trimmed ${(before - after) / KB} KB in ${elapsed}ms " +
                    "(${before / KB} → ${after / KB} KB, target=${target / KB} KB, evicted=$evicted)"
            }
        } finally {
            runMutex.unlock()
        }
    }

    private val KB get() = 1024L
}
