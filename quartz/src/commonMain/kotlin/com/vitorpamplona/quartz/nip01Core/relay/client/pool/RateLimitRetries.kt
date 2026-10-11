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
package com.vitorpamplona.quartz.nip01Core.relay.client.pool

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.concurrent.PlatformLock
import com.vitorpamplona.quartz.utils.concurrent.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Holds what a relay refused as `rate-limited:` WITH a back-off (NIP-01's `params`, see
 * [com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.RetryAfter]) until that back-off
 * has passed, then hands it to [onDue] to send again.
 *
 * Without this the pool parks a rate-limited subscription until its filters change or the
 * relay reconnects, and a rate-limited publish until the next reconnect: the relay told us when
 * it would take the command, and nothing asked again.
 *
 * Keys queue per relay: the back-off covers every command of that type the client sends the
 * relay, so nothing goes out before the LATEST deadline any of its refusals named. Then the
 * queue drains one key at a time, oldest first. Releasing them all at once would meet a bucket
 * that refilled one token and be refused again, all but one, until the retries ran out.
 *
 * The spacing is the LONGEST back-off the relay stated for the batch, plus a tenth. A
 * token bucket's stated back-off is the time to its next token, at most its refill interval and
 * a little under it when stated just after a token was spent (measured: 486 ms on a 500 ms
 * bucket), so releasing at exactly that creeps ahead of the bucket until a key is refused again.
 * A key refused while the queue drains goes back to the head: it was next in line, and its new
 * back-off says exactly when the next token is due. One coroutine per relay with something
 * pending, not one per refusal — a bulk publish refused event by event costs one timer.
 *
 * A back-off longer than [maxDelayMs] is not scheduled ([schedule] returns false): the command
 * stays parked as it would without a back-off, and the next reconnect re-sends it.
 */
class RateLimitRetries(
    private val scope: CoroutineScope,
    private val maxDelayMs: Long = MAX_DELAY_MS,
    private val onDue: (relay: NormalizedRelayUrl, keys: Set<String>) -> Unit,
) {
    private class Batch(
        var dueAtMs: Long,
        /** The longest back-off the relay stated for this batch; see [spacing]. */
        var longestBackOffMs: Long,
    ) {
        /** In release order: keys go out in the order they were refused or deferred. */
        private val order = ArrayDeque<String>()

        /** [order] as a set, so a bulk deferral stays linear. */
        private val members = HashSet<String>()

        fun isEmpty() = order.isEmpty()

        fun removeFirst(): String = order.removeFirst().also { members.remove(it) }

        /** True once a key went out: a refusal from then on is a released key coming back. */
        var draining = false

        /** How far apart queued keys are released. */
        fun spacing() = longestBackOffMs + longestBackOffMs / SPACING_MARGIN_DIVISOR + 1

        fun enqueue(
            key: String,
            atHead: Boolean,
        ) {
            if (!members.add(key)) return
            if (atHead) order.addFirst(key) else order.addLast(key)
        }
    }

    private val lock = PlatformLock()
    private val batches = HashMap<NormalizedRelayUrl, Batch>()

    /**
     * Re-sends [key] to [relay] once [delayMs] has passed (and every other key pending on that
     * relay with it). Returns false, scheduling nothing, when [delayMs] is not positive or is
     * over [maxDelayMs].
     */
    fun schedule(
        relay: NormalizedRelayUrl,
        key: String,
        delayMs: Long,
    ): Boolean {
        if (delayMs <= 0 || delayMs > maxDelayMs) return false
        val dueAt = TimeUtils.nowMillis() + delayMs
        val newBatch =
            lock.withLock {
                val batch = batches[relay]
                if (batch == null) {
                    Batch(dueAt, delayMs).also {
                        it.enqueue(key, atHead = false)
                        batches[relay] = it
                    }
                } else {
                    batch.enqueue(key, atHead = batch.draining)
                    if (dueAt > batch.dueAtMs) batch.dueAtMs = dueAt
                    if (delayMs > batch.longestBackOffMs) batch.longestBackOffMs = delayMs
                    null
                }
            }
        if (newBatch != null) scope.launch { drain(relay, newBatch) }
        return true
    }

    /** True while [relay] has keys waiting out a back-off. */
    fun isPending(relay: NormalizedRelayUrl): Boolean = lock.withLock { relay in batches }

    /**
     * Adds [key] to [relay]'s pending batch without moving its deadline, so a command the
     * client wants to send mid-back-off goes out with the refused ones instead of reaching
     * the relay early. Returns false, adding nothing, when [relay] has no back-off pending:
     * the caller sends now.
     */
    fun defer(
        relay: NormalizedRelayUrl,
        key: String,
    ): Boolean =
        lock.withLock {
            val batch = batches[relay] ?: return false
            batch.enqueue(key, atHead = false)
            true
        }

    /** Forgets everything pending; their timers fire into nothing. */
    fun clear() = lock.withLock { batches.clear() }

    /** Releases [batch]'s keys on its schedule. Stops once the batch is gone or replaced: one timer per batch. */
    private suspend fun drain(
        relay: NormalizedRelayUrl,
        batch: Batch,
    ) {
        while (true) {
            // The deadline can move later while we sleep (a newer refusal named a longer
            // back-off), so re-read it after every wait and only release once it truly passed.
            var due: String? = null
            val waitMs =
                lock.withLock {
                    if (batches[relay] !== batch) return
                    val now = TimeUtils.nowMillis()
                    val left = batch.dueAtMs - now
                    if (left <= 0) {
                        due = batch.removeFirst()
                        batch.draining = true
                        if (batch.isEmpty()) {
                            batches.remove(relay)
                        } else {
                            batch.dueAtMs = now + batch.spacing()
                        }
                    }
                    left
                }
            val key = due
            if (key != null) {
                onDue(relay, setOf(key))
            } else {
                delay(waitMs)
            }
        }
    }

    companion object {
        /** Ten minutes: a relay asking for longer is better served by the next reconnect. */
        const val MAX_DELAY_MS = 10 * 60 * 1000L

        /** The spacing adds 1/10 of the longest back-off: see the class notes. */
        private const val SPACING_MARGIN_DIVISOR = 10
    }
}
