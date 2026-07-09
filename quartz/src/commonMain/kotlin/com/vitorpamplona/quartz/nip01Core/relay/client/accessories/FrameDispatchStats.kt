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

import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Diagnostic: the lag between a raw relay frame arriving on the socket (the OkHttp
 * reader thread's `onMessage`) and our per-connection consumer coroutine actually
 * pulling it off the channel to decode + dispatch. Because that consumer runs on the
 * shared `Dispatchers.IO`, this lag is precisely OUR-side pipeline delay — channel
 * queue wait + coroutine reschedule + time spent decoding earlier frames — with the
 * relay's own send timing excluded (the reader thread enqueues the instant bytes land).
 *
 * It exists to answer one question the [GrapeRankCrawler]'s EOSE-wait metric cannot on
 * its own: when a drain shows a 5-second gap between the relay's last event and its
 * EOSE, is the relay slow to send EOSE (low dispatch lag) or is our IO pipeline backed
 * up so the already-arrived EOSE frame sits queued (high dispatch lag)? Process-global
 * and opt-in: a caller [reset]s before a run and reads [snapshot] after. Off unless
 * something records into it, so zero cost on normal paths.
 */
@OptIn(ExperimentalAtomicApi::class)
object FrameDispatchStats {
    private val count = AtomicLong(0)
    private val sumMs = AtomicLong(0)
    private val maxMs = AtomicLong(0)
    private val over1s = AtomicLong(0)

    fun record(lagMs: Long) {
        count.addAndFetch(1)
        sumMs.addAndFetch(lagMs)
        if (lagMs >= 1000) over1s.addAndFetch(1)
        // Lock-free running max.
        while (true) {
            val cur = maxMs.load()
            if (lagMs <= cur || maxMs.compareAndSet(cur, lagMs)) break
        }
    }

    fun reset() {
        count.store(0)
        sumMs.store(0)
        maxMs.store(0)
        over1s.store(0)
    }

    class Snapshot(
        val frames: Long,
        val meanMs: Long,
        val maxMs: Long,
        val over1s: Long,
    ) {
        override fun toString() = "frames=$frames, mean dispatch-lag=${meanMs}ms, max=${maxMs}ms, $over1s frames waited >1s in our pipeline"
    }

    fun snapshot(): Snapshot {
        val n = count.load()
        return Snapshot(
            frames = n,
            meanMs = if (n > 0) sumMs.load() / n else 0,
            maxMs = maxMs.load(),
            over1s = over1s.load(),
        )
    }
}
