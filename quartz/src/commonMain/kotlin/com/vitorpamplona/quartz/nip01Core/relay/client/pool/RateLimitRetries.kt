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
 * Keys are batched per relay: the back-off covers every command of that type the client sends
 * the relay, so all of a relay's refused keys come due together, at the LATEST deadline any of
 * its refusals named. One coroutine per relay with something pending, not one per refusal — a
 * bulk publish refused event by event costs one timer.
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
    ) {
        val keys = mutableSetOf<String>()
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
        val startTimer =
            lock.withLock {
                val batch = batches[relay]
                if (batch == null) {
                    batches[relay] = Batch(dueAt).also { it.keys.add(key) }
                    true
                } else {
                    batch.keys.add(key)
                    if (dueAt > batch.dueAtMs) batch.dueAtMs = dueAt
                    false
                }
            }
        if (startTimer) scope.launch { awaitAndFire(relay) }
        return true
    }

    /** True while [relay] has keys waiting out a back-off. */
    fun isPending(relay: NormalizedRelayUrl): Boolean = lock.withLock { relay in batches }

    /** Forgets everything pending; their timers fire into nothing. */
    fun clear() = lock.withLock { batches.clear() }

    private suspend fun awaitAndFire(relay: NormalizedRelayUrl) {
        while (true) {
            // The deadline can move later while we sleep (a newer refusal named a longer
            // back-off), so re-read it after every wait and only fire once it truly passed.
            var due: Set<String>? = null
            val waitMs =
                lock.withLock {
                    val batch = batches[relay] ?: return
                    val left = batch.dueAtMs - TimeUtils.nowMillis()
                    if (left <= 0) {
                        batches.remove(relay)
                        due = batch.keys.toSet()
                    }
                    left
                }
            val keys = due
            if (keys != null) {
                onDue(relay, keys)
                return
            }
            delay(waitMs)
        }
    }

    companion object {
        /** Ten minutes: a relay asking for longer is better served by the next reconnect. */
        const val MAX_DELAY_MS = 10 * 60 * 1000L
    }
}
