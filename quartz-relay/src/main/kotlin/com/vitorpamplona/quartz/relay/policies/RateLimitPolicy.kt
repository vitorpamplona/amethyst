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
package com.vitorpamplona.quartz.relay.policies

import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CountCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.server.PolicyResult

/**
 * Per-session token-bucket rate limiter. Mirrors nostr-rs-relay's
 * `[limits].messages_per_sec` and `[limits].subscriptions_per_min`.
 *
 *  - [messagesPerSec] caps EVERY incoming command (EVENT/REQ/COUNT/AUTH)
 *    over a 1-second window. `null` disables.
 *  - [subscriptionsPerMin] caps REQ + COUNT (subscriptions opened) over
 *    a 60-second window. `null` disables.
 *
 * Each session gets its own buckets — instances of this policy must be
 * created per-connection via the relay's `policyBuilder` factory.
 *
 * Time source defaults to monotonic [System.nanoTime] so wall-clock
 * jumps don't reset the buckets. Tests inject a deterministic clock.
 */
class RateLimitPolicy(
    val messagesPerSec: Int? = null,
    val subscriptionsPerMin: Int? = null,
    private val nowNanos: () -> Long = System::nanoTime,
) : PassThroughPolicy() {
    private val msgBucket =
        messagesPerSec?.let {
            require(it > 0) { "messagesPerSec must be > 0" }
            TokenBucket(capacity = it, refillIntervalNanos = 1_000_000_000L / it, nowNanos)
        }

    private val subBucket =
        subscriptionsPerMin?.let {
            require(it > 0) { "subscriptionsPerMin must be > 0" }
            TokenBucket(capacity = it, refillIntervalNanos = 60_000_000_000L / it, nowNanos)
        }

    private fun checkMsgBucket(): String? = if (msgBucket?.tryTake() == false) "blocked: too many messages per second" else null

    private fun checkSubBucket(): String? = if (subBucket?.tryTake() == false) "blocked: too many subscriptions per minute" else null

    override fun accept(cmd: EventCmd): PolicyResult<EventCmd> {
        checkMsgBucket()?.let { return PolicyResult.Rejected(it) }
        return PolicyResult.Accepted(cmd)
    }

    override fun accept(cmd: ReqCmd): PolicyResult<ReqCmd> {
        checkMsgBucket()?.let { return PolicyResult.Rejected(it) }
        checkSubBucket()?.let { return PolicyResult.Rejected(it) }
        return PolicyResult.Accepted(cmd)
    }

    override fun accept(cmd: CountCmd): PolicyResult<CountCmd> {
        checkMsgBucket()?.let { return PolicyResult.Rejected(it) }
        checkSubBucket()?.let { return PolicyResult.Rejected(it) }
        return PolicyResult.Accepted(cmd)
    }
}

/**
 * Token bucket with monotonic refill. Single-threaded by contract —
 * RelaySession.receive runs serially within a session, so no locking
 * is needed.
 */
private class TokenBucket(
    val capacity: Int,
    val refillIntervalNanos: Long,
    val now: () -> Long,
) {
    private var tokens: Long = capacity.toLong()
    private var lastRefill: Long = now()

    fun tryTake(): Boolean {
        refill()
        return if (tokens > 0) {
            tokens -= 1
            true
        } else {
            false
        }
    }

    private fun refill() {
        val n = now()
        val elapsed = n - lastRefill
        if (elapsed <= 0) return
        val newTokens = elapsed / refillIntervalNanos
        if (newTokens > 0) {
            tokens = (tokens + newTokens).coerceAtMost(capacity.toLong())
            lastRefill += newTokens * refillIntervalNanos
        }
    }
}
