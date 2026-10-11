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
package com.vitorpamplona.quartz.nip01Core.relay.server.policies

import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.concurrent.ConcurrentMap
import com.vitorpamplona.quartz.utils.concurrent.PlatformLock
import com.vitorpamplona.quartz.utils.concurrent.withLock
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.math.ceil

/**
 * A token bucket per client: each client may spend [burst] commands at once, refilled at
 * [perMinute] a minute. One limiter is shared by every connection it guards, so a client
 * that opens a second socket does not get a second allowance.
 *
 * [acquire] answers how long the client must wait when it is out of tokens: that is the
 * back-off a `rate-limited:` refusal states in NIP-01's `params`, so a client that honors it
 * is refused at most once per wait.
 *
 * A bucket idle long enough to have refilled is the same as no bucket, so those are swept
 * every [SWEEP_EVERY] acquisitions; memory follows the clients active in the last refill
 * window, not every address ever seen.
 */
@OptIn(ExperimentalAtomicApi::class)
class RateLimiter(
    val perMinute: Int,
    val burst: Int = perMinute,
    private val clock: () -> Long = TimeUtils::nowMillis,
) {
    init {
        require(perMinute > 0) { "perMinute must be > 0, got $perMinute" }
        require(burst > 0) { "burst must be > 0, got $burst" }
    }

    private class Bucket(
        var tokens: Double,
        var updatedAtMs: Long,
    ) {
        val lock = PlatformLock()
    }

    /** Tokens gained per millisecond. */
    private val refillPerMs = perMinute / 60_000.0

    /** How long an empty bucket takes to fill: past it, a bucket is indistinguishable from a new one. */
    private val fullAfterMs = ceil(burst / refillPerMs).toLong()

    private val buckets = ConcurrentMap<String, Bucket>()
    private val sinceSweep = AtomicInt(0)

    /**
     * Spends one of [client]'s tokens and returns 0, or, when it has none, spends nothing and
     * returns the milliseconds until it will have one (always at least 1).
     */
    fun acquire(client: String): Long {
        // One sweeper at a time: whoever resets the counter does it.
        val count = sinceSweep.incrementAndFetch()
        if (count >= SWEEP_EVERY && sinceSweep.compareAndSet(count, 0)) sweep()
        val now = clock()
        val bucket = buckets.getOrPut(client) { Bucket(burst.toDouble(), now) }
        return bucket.lock.withLock {
            val elapsed = now - bucket.updatedAtMs
            if (elapsed > 0) {
                bucket.tokens = minOf(burst.toDouble(), bucket.tokens + elapsed * refillPerMs)
                bucket.updatedAtMs = now
            }
            if (bucket.tokens >= 1.0) {
                bucket.tokens -= 1.0
                0L
            } else {
                ceil((1.0 - bucket.tokens) / refillPerMs).toLong().coerceAtLeast(1L)
            }
        }
    }

    /** Clients with a bucket right now; the ones idle past a full refill are swept away. */
    fun trackedClients(): Int = buckets.size()

    /** Drops every bucket that has been idle long enough to be full again. */
    fun sweep() {
        val now = clock()
        buckets.snapshot().forEach { (client, bucket) ->
            val idle = bucket.lock.withLock { now - bucket.updatedAtMs >= fullAfterMs }
            if (idle) buckets.remove(client, bucket)
        }
    }

    companion object {
        const val SWEEP_EVERY = 4096
    }
}
