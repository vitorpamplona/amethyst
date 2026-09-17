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
package com.vitorpamplona.amethyst.service.resourceusage

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicLong

/**
 * Measures how long the app stays busy each time something wakes it, and how
 * that duration is distributed.
 *
 * [RelayWakeEstimator] answers "how often"; this answers "and then what". The
 * pair is what decides the energy, because a wake is not a fixed price: the
 * device cannot suspend again until everything the wake set off has finished.
 * Three hundred wakes that settle in 20ms are cheap; three hundred that each
 * keep the app busy for two seconds are a dead battery, and the count alone
 * cannot tell them apart.
 *
 * **What counts as activity.** Inbound relay frames and completed feed passes.
 * A frame is what wakes us, but the work it sets off — parse, verify, cache
 * write, then the fan-out to every feed — outlives the frame, and that tail is
 * precisely the "until things come down again" being measured. Anything else on
 * the same wake (image decodes, HTTP) generally follows from one of these two,
 * so they bound the window without needing a hook on every subsystem.
 *
 * **What it cannot see.** Work that leaves no instrumented trace still keeps the
 * device awake, so a window is a lower bound on the busy time, never an upper
 * one. And the window is the app's own busy time, not the device's — pair it
 * with [DeviceSleepSampler] to ask whether the device actually got to sleep in
 * the gaps this says were idle.
 *
 * **Cost.** One `getAndSet` per activity on a shared atomic, and an emit only
 * when a window closes (at most once per [UsageKeys.WAKE_SETTLE_GAP_MS]).
 * Windows also close from the accountant's pre-flush hook, so a quiet period
 * does not strand an open window until the next frame arrives — without that, a
 * device that went to sleep right after a burst would never book the burst.
 */
class WakeWorkTracker(
    private val accountant: ResourceUsageAccountant,
    private val isMobile: () -> Boolean,
    private val isForeground: () -> Boolean,
    private val nowMs: () -> Long = { SystemClock.elapsedRealtime() },
) {
    /**
     * Start and last-activity stamps of the open window, packed behind one lock.
     *
     * Activity arrives concurrently from every relay's OkHttp thread and from
     * the feed workers, and the two stamps must move together: a torn update
     * could close a window against another window's start and book a span that
     * never happened.
     */
    private val lock = Any()
    private var windowStartMs = NONE
    private var lastActivityMs = NONE

    /** Windows closed so far — read by tests to prove pre-flush closing works. */
    private val closed = AtomicLong(0)

    val closedWindows: Long get() = closed.get()

    fun register() {
        accountant.addPreFlushHook(::closeIfSettled)
    }

    fun onActivity() {
        val now = nowMs()
        var spanToEmit = NONE
        synchronized(lock) {
            if (windowStartMs == NONE) {
                windowStartMs = now
            } else if (now - lastActivityMs > UsageKeys.WAKE_SETTLE_GAP_MS) {
                // The previous window settled before this arrived: close it and
                // start a new one at `now`.
                spanToEmit = lastActivityMs - windowStartMs
                windowStartMs = now
            }
            lastActivityMs = now
        }
        if (spanToEmit != NONE) emit(spanToEmit)
    }

    /**
     * Closes the open window if it has been quiet for a full settle gap.
     *
     * Deliberately does NOT close a window that is still active: a flush landing
     * mid-burst would otherwise cut one busy period into two, halving the spans
     * the histogram sees and doubling the window count.
     */
    fun closeIfSettled() {
        val now = nowMs()
        var spanToEmit = NONE
        synchronized(lock) {
            if (windowStartMs != NONE && now - lastActivityMs > UsageKeys.WAKE_SETTLE_GAP_MS) {
                spanToEmit = lastActivityMs - windowStartMs
                windowStartMs = NONE
                lastActivityMs = NONE
            }
        }
        if (spanToEmit != NONE) emit(spanToEmit)
    }

    private fun emit(spanMs: Long) {
        val span = spanMs.coerceAtLeast(0)
        val foreground = isForeground()
        val visibility = if (foreground) UsageKeys.FG else UsageKeys.BG
        accountant.add(UsageKeys.wakeWorkWindows(visibility), 1)
        // A single-frame wake has a zero-length span but is still a window; the
        // count is what carries it, and the histogram's lowest bucket records it.
        if (span > 0) accountant.add(UsageKeys.wakeWorkMs(visibility), span)
        accountant.add(UsageKeys.wakeWorkSpan(span, isMobile(), foreground), 1)
        closed.incrementAndGet()
    }

    companion object {
        private const val NONE = -1L
    }
}
