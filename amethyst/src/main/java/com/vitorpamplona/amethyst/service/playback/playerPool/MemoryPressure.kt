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
package com.vitorpamplona.amethyst.service.playback.playerPool

import android.app.ActivityManager
import android.content.Context
import android.os.SystemClock
import androidx.core.content.getSystemService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Whether the device is close to the point where the system starts killing apps, so warm players
 * (paused, but holding a decoder and its buffers) should be let go.
 *
 * The answer comes from `ActivityManager.getMemoryInfo`, a Binder call into the system, and the
 * player pool asks on the main thread every time a card hands its player back. So [isTight] never
 * makes that call: it returns the last answer and, when that is older than [MAX_AGE_MS], asks for a
 * fresh one on [scope]. A stale answer only means one more or one fewer warm player for a moment.
 */
class MemoryPressure(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val activityManager: ActivityManager? = context.getSystemService()

    @Volatile private var tight = false

    @Volatile private var checkedAt = 0L

    private val refreshing = AtomicBoolean(false)

    init {
        refresh()
    }

    fun isTight(): Boolean {
        if (SystemClock.elapsedRealtime() - checkedAt > MAX_AGE_MS) refresh()
        return tight
    }

    private fun refresh() {
        if (activityManager == null || !refreshing.compareAndSet(false, true)) return
        scope.launch {
            try {
                val info = ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(info)
                tight = info.lowMemory || info.availMem < info.threshold + WARM_HEADROOM_BYTES
                checkedAt = SystemClock.elapsedRealtime()
            } finally {
                refreshing.set(false)
            }
        }
    }

    companion object {
        // Free memory to keep beyond the system's own low-memory threshold before a player may stay
        // warm: room for about eight warm players at the ~30 MB each measured on an SM-T220.
        private const val WARM_HEADROOM_BYTES = 256L * 1024 * 1024

        private const val MAX_AGE_MS = 1_000L
    }
}
