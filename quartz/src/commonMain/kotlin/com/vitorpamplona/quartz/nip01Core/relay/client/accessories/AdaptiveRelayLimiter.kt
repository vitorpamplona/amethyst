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

import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.NoticeMessage
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.concurrent.ConcurrentMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Adaptive per-relay back-pressure with TWO independent controls, because relays
 * push back for two different reasons that need two different responses:
 *
 *  1. **Subscription-count limit** — a max on how many subscriptions may be OPEN
 *     at once ("too many subscriptions", "maximum concurrent subscription count",
 *     "number of subscriptions exceeds limit"). The fix is fewer *concurrent*
 *     subs, so we demote the relay's concurrency cap down [subLadder]
 *     (100 → 20 → 10).
 *  2. **Rate limit** — too many subscription *changes per second* ("rate-limited:
 *     too many messages", "burst exhausted", "slow down"). Fewer concurrent subs
 *     wouldn't help; the fix is to *space the REQs out in time*, so we impose a
 *     minimum interval between opens to that relay, growing it up [rateLadder]
 *     (250ms → 500ms → 1s → 2s).
 *
 * Mixing the two mishandles the relay: capping concurrency does nothing for a
 * rate limit, and slowing the rate does nothing for a subscription-count cap. So
 * each complaint is routed to its own actuator by matching the notice text.
 *
 * A well-behaved relay starts at [startCap] concurrent subs with no rate delay,
 * and only the ones that push back get throttled — each only as far, and in the
 * dimension, they keep pushing.
 *
 * Registered as a [RelayConnectionListener] on the shared client, so both signals
 * are driven straight off the incoming NOTICE/CLOSED frames (which fire on the
 * per-relay socket threads — all state here is concurrent). Drains gate through
 * [withPermit]: the gated-drain path holds a relay's permit for the lifetime of
 * that relay's subscription, and passes the rate gate before it opens, so we
 * respect both limits at once.
 */
@OptIn(ExperimentalAtomicApi::class)
class AdaptiveRelayLimiter(
    private val startCap: Int = 100,
    private val subLadder: List<Int> = listOf(20, 10),
    private val rateLadder: List<Long> = listOf(250L, 500L, 1000L, 2000L),
) : RelayConnectionListener {
    private val gates = ConcurrentMap<NormalizedRelayUrl, Gate>()

    // Concurrency-cap demotions per relay (== index+1 into subLadder). Capped at
    // subLadder.size: past the floor we stop demoting.
    private val subDemotions = ConcurrentMap<NormalizedRelayUrl, Int>()

    // Rate-limit state per relay: how far down rateLadder we've stepped, the
    // current min interval between opens, and the next epoch-ms an open may fire.
    private val rateSteps = ConcurrentMap<NormalizedRelayUrl, Int>()
    private val rateDelayMs = ConcurrentMap<NormalizedRelayUrl, Long>()
    private val nextAllowedAtMs = ConcurrentMap<NormalizedRelayUrl, AtomicLong>()

    private fun gate(relay: NormalizedRelayUrl): Gate = gates.getOrPut(relay) { Gate(startCap) }

    /**
     * Run [block] against [relay] respecting both limits: first wait out any rate
     * delay (spacing opens in time), then hold one of the relay's concurrency
     * permits for the duration.
     */
    suspend fun <T> withPermit(
        relay: NormalizedRelayUrl,
        block: suspend () -> T,
    ): T {
        rateGate(relay)
        val g = gate(relay)
        g.acquire()
        try {
            return block()
        } finally {
            g.release()
        }
    }

    /** If [relay] is rate-limited, reserve and wait for its next allowed open slot. */
    private suspend fun rateGate(relay: NormalizedRelayUrl) {
        val delayMs = rateDelayMs[relay] ?: return
        if (delayMs <= 0L) return
        val now = TimeUtils.nowMillis()
        // Atomically claim the next slot: my turn is max(prevSlot, now); the next
        // caller can't fire until delayMs after me. Serializes opens to this relay
        // at one per delayMs, in arrival order.
        val slot = nextAllowedAtMs.getOrPut(relay) { AtomicLong(now) }
        var myTurn: Long
        while (true) {
            val prev = slot.load()
            myTurn = maxOf(prev, now)
            if (slot.compareAndSet(prev, myTurn + delayMs)) break
        }
        val wait = myTurn - now
        if (wait > 0) delay(wait)
    }

    override fun onIncomingMessage(
        relay: IRelayClient,
        msgStr: String,
        msg: Message,
    ) {
        val text =
            when (msg) {
                is ClosedMessage -> msg.message
                is NoticeMessage -> msg.message
                else -> return
            }
        val t = text.lowercase()
        // Route each complaint to the matching actuator. Not mutually exclusive:
        // if a relay somehow reports both, we act on both (they don't conflict).
        if (RATE_LIMIT_MARKERS.any { it in t }) throttleRate(relay.url)
        if (SUB_LIMIT_MARKERS.any { it in t }) demoteConcurrency(relay.url)
    }

    /** Step [relay] one rung down the concurrency-cap ladder, unless already at the floor. */
    private fun demoteConcurrency(relay: NormalizedRelayUrl) {
        if ((subDemotions[relay] ?: 0) >= subLadder.size) return
        val step = subDemotions.merge(relay, 1) { a, b -> a + b }
        val cap = subLadder[(step - 1).coerceIn(0, subLadder.size - 1)]
        gate(relay).lower(cap)
        if (step <= subLadder.size) {
            Log.d("AdaptiveRelayLimiter") { "${relay.url} concurrency capped at $cap subs (sub-limit #$step)" }
        }
    }

    /** Step [relay] one rung down the rate ladder, unless already at the slowest. */
    private fun throttleRate(relay: NormalizedRelayUrl) {
        if ((rateSteps[relay] ?: 0) >= rateLadder.size) return
        val step = rateSteps.merge(relay, 1) { a, b -> a + b }
        val d = rateLadder[(step - 1).coerceIn(0, rateLadder.size - 1)]
        rateDelayMs[relay] = d
        if (step <= rateLadder.size) {
            Log.d("AdaptiveRelayLimiter") { "${relay.url} rate-throttled to 1 REQ / ${d}ms (rate-limit #$step)" }
        }
    }

    /** JSON-friendly view of which relays we throttled, in which dimension, how far. */
    fun snapshot(): Map<String, Any?> {
        val capCounts = HashMap<Int, Int>()
        for ((_, step) in subDemotions.snapshot()) {
            val cap = subLadder[(step - 1).coerceIn(0, subLadder.size - 1)]
            capCounts[cap] = (capCounts[cap] ?: 0) + 1
        }
        val cappedAt = capCounts.toList().sortedBy { it.first }.toMap()
        val rateCounts = HashMap<Long, Int>()
        for ((_, step) in rateSteps.snapshot()) {
            val d = rateLadder[(step - 1).coerceIn(0, rateLadder.size - 1)]
            rateCounts[d] = (rateCounts[d] ?: 0) + 1
        }
        val rateAt = rateCounts.toList().sortedBy { it.first }.toMap()
        return mapOf(
            "start_cap" to startCap,
            "sub_ladder" to subLadder,
            "rate_ladder_ms" to rateLadder,
            "concurrency_capped_relays" to subDemotions.size(),
            "concurrency_capped_at" to cappedAt,
            "rate_limited_relays" to rateSteps.size(),
            "rate_limited_at_ms" to rateAt,
        )
    }

    fun hadThrottling(): Boolean = subDemotions.size() > 0 || rateSteps.size() > 0

    /**
     * A bounded-concurrency gate whose limit can only ever be *lowered* (relays
     * never earn their cap back within a run). Fair FIFO hand-off: a released
     * permit goes to the longest-waiting acquirer. Lowering the limit below the
     * in-use count doesn't cancel live holders — it just refuses to admit new
     * ones until enough release that `inUse < limit` again, so the concurrency
     * converges down to the new cap as the excess subscriptions finish.
     */
    private class Gate(
        initialLimit: Int,
    ) {
        private val limit = AtomicInt(initialLimit)
        private val mutex = Mutex()
        private var inUse = 0
        private val waiters = ArrayDeque<CompletableDeferred<Unit>>()

        suspend fun acquire() {
            val wait =
                mutex.withLock {
                    if (inUse < limit.load()) {
                        inUse++
                        null
                    } else {
                        CompletableDeferred<Unit>().also { waiters.addLast(it) }
                    }
                }
            wait?.await()
        }

        suspend fun release() {
            mutex.withLock {
                inUse--
                while (inUse < limit.load() && waiters.isNotEmpty()) {
                    waiters.removeFirst().complete(Unit)
                    inUse++
                }
            }
        }

        /** Monotonically shrink the cap. Safe to call from any thread. */
        fun lower(newLimit: Int) {
            while (true) {
                val cur = limit.load()
                if (newLimit >= cur) return
                if (limit.compareAndSet(cur, newLimit)) return
            }
        }
    }

    companion object {
        // A cap on how many subscriptions may be OPEN at once. Fix: fewer
        // concurrent subs (demote the concurrency cap).
        private val SUB_LIMIT_MARKERS =
            listOf(
                "too many concurrent",
                "concurrent req",
                "too many subscription",
                "number of subscriptions",
                "subscriptions exceeds",
                "subscription limit",
                "subscription count",
                "maximum concurrent subscription",
                "max subscription",
                "too many req",
            )

        // Too many subscription CHANGES per second. Fix: space the REQs out in
        // time (a per-relay min interval), not fewer concurrent subs.
        private val RATE_LIMIT_MARKERS =
            listOf(
                "rate-limit",
                "rate limit",
                "ratelimit",
                "too many messages",
                "too many requests",
                "burst exhausted",
                "throttl",
                "slow down",
            )
    }
}
