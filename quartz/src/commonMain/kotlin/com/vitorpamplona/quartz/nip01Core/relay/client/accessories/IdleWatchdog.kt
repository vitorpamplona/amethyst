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
package com.vitorpamplona.quartz.nip01Core.relay.client.accessories

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.Volatile
import kotlin.time.TimeSource

/**
 * Monotonic "last activity" marker for the idle watchdogs shared by the accessory
 * fetch/sync loops. [bump] on every sign of life from the relay; [elapsedMs] reports
 * the silence since the last bump (or since construction, before the first bump).
 *
 * This is the timeout convention for every accessory in this package: an
 * `idleTimeoutMs` is an **idle window measured from the relay's most recent
 * progress**, not a wall-clock deadline — an actively streaming relay is never cut
 * off mid-delivery, only one that goes silent. The name is the contract: a
 * parameter here is called `idleTimeoutMs` precisely because it is not a deadline.
 *
 * [bump] is on the per-event hot path (a connection listener may bump for every
 * message the relay sends — millions during a large download), so it must not
 * allocate: a single [start] mark is taken once (unboxed field) and each bump only
 * writes a `Long` of nanos-since-start into a `@Volatile` field. Reader threads
 * write, the driver coroutine reads — visibility is all we need, so a plain volatile
 * Long beats boxing a `ValueTimeMark` into an `AtomicReference` on every event.
 */
internal class IdleClock {
    private val start = TimeSource.Monotonic.markNow()

    @Volatile
    private var lastNanos = 0L

    fun bump() {
        lastNanos = start.elapsedNow().inWholeNanoseconds
    }

    fun elapsedMs(): Long = (start.elapsedNow().inWholeNanoseconds - lastNanos) / 1_000_000
}

/**
 * Receives the next item, giving up (returning `null`) only after [idleMs] elapse with
 * no activity on [clock]. Because [clock] can be bumped by *any* relay message — not
 * just items on this channel — unrelated progress (e.g. download events arriving during
 * a reconcile wait) keeps pushing the deadline out. [idleMs] `<= 0` disables the
 * watchdog: it waits until an item arrives (a disconnect is delivered as an item, so
 * a dead socket still unblocks it).
 */
internal suspend fun <T> Channel<T>.receiveWithinIdle(
    clock: IdleClock,
    idleMs: Long,
): T? {
    if (idleMs <= 0) return receive()
    while (true) {
        val remaining = idleMs - clock.elapsedMs()
        if (remaining <= 0) return null
        val item = withTimeoutOrNull(remaining) { receive() }
        if (item != null) return item
        // Timed out with nothing on this channel. If other activity bumped the clock
        // meanwhile, the next `remaining` is positive and we wait again; otherwise it
        // is <= 0 on the next iteration and we give up.
    }
}
