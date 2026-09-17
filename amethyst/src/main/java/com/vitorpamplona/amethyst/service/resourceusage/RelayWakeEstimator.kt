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
 * Estimates how often inbound relay traffic pulls the device out of idle: a wake
 * is counted whenever a frame arrives after more than [WAKE_GAP_MS] of silence
 * across **every** relay at once.
 *
 * **The gap this fills.** `relay.connms` says how long connections were open and
 * `relay.msg.*` says how many bytes crossed them, but neither distinguishes a
 * pool that is quiet from one that chatters — and chatter is what costs battery.
 * The 2026-07-12 ping study found ~90% of production relays server-ping every
 * 30-70s, which OkHttp must pong; across a pool of ~190 connections that is
 * several wake-ups a second before a single event arrives. Those wake-ups cost
 * almost no CPU and almost no bytes, so they are invisible in every counter the
 * ledger had.
 *
 * **Across all relays, not per relay.** The device wakes once no matter how many
 * sockets had something to say, so per-relay gaps would multiply one wake-up by
 * the pool size. That is also why this is cheaper than it looks: one shared
 * atomic, no per-relay map.
 *
 * [WAKE_GAP_MS] matches [RadioBurstEstimator.BURST_GAP_MS] so the two numbers
 * are comparable — the same 10s that approximates a cellular inactivity timer is
 * also a reasonable floor for "the device had a chance to go back to idle".
 *
 * Counted on inbound frames only. An outbound frame is something the app chose to
 * send while it was already running; an inbound one arrives on the relay's
 * schedule, which is the thing we cannot control and are trying to size.
 */
class RelayWakeEstimator(
    private val accountant: ResourceUsageAccountant,
    private val isMobile: () -> Boolean,
    private val isForeground: () -> Boolean,
    private val nowMs: () -> Long = { SystemClock.elapsedRealtime() },
) {
    /**
     * Last inbound frame time, shared across relays.
     *
     * An AtomicLong with getAndSet rather than a @Volatile read-then-write:
     * frames arrive concurrently on every relay's own OkHttp thread, and the
     * check-then-store would let a burst of simultaneous frames each see the old
     * value and all count a wake-up. getAndSet gives exactly one of them the
     * stale timestamp, which is the one that reports.
     */
    private val lastFrameMs = AtomicLong(Long.MIN_VALUE)

    fun onInboundFrame() {
        val now = nowMs()
        val last = lastFrameMs.getAndSet(now)
        if (last == Long.MIN_VALUE || now - last > WAKE_GAP_MS) {
            accountant.add(UsageKeys.relayWakes(isMobile(), isForeground()), 1)
        }
    }

    companion object {
        /** Deliberately equal to [RadioBurstEstimator.BURST_GAP_MS]. */
        const val WAKE_GAP_MS = 10_000L
    }
}
