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
package com.vitorpamplona.amethyst.cli

import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.NoticeMessage
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Adaptive per-relay concurrent-subscription cap.
 *
 * Every relay starts with a generous cap ([startCap], default 100) — we assume a
 * relay can take as many concurrent REQs as we throw at it until it tells us
 * otherwise. When a relay complains about concurrency (a `CLOSED rate-limited`,
 * or a `NOTICE` like "too many concurrent REQs" / "too many subscriptions" /
 * "burst exhausted"), we demote *that relay only* down the [ladder]
 * (100 → 20 → 10). A well-behaved relay keeps the full cap; only the ones that
 * push back get throttled, and only as far as they keep pushing.
 *
 * This replaces a single blunt global concurrency number with per-relay
 * back-pressure: the crawl can fan out widely across the many relays that don't
 * mind, while automatically easing off the few busy hubs that do — exactly the
 * signals [RelayDiagnostics] already observes, here turned into an actuator.
 *
 * Registered as a [RelayConnectionListener] on the shared client, so demotions
 * are driven straight off the incoming NOTICE/CLOSED frames (which fire on the
 * per-relay socket threads — all state here is concurrent). Drains gate through
 * [withPermit]; [Context.drain]'s `gatePerRelay` path holds a relay's permit for
 * the lifetime of that relay's subscription, so at most `cap` of our
 * subscriptions are ever open on it at once.
 */
class AdaptiveRelayLimiter(
    private val startCap: Int = 100,
    private val ladder: List<Int> = listOf(20, 10),
) : RelayConnectionListener {
    private val gates = ConcurrentHashMap<NormalizedRelayUrl, Gate>()

    // How many concurrency complaints we've acted on per relay (== index+1 into
    // the ladder). Capped at ladder.size: past the floor we stop demoting.
    private val demotions = ConcurrentHashMap<NormalizedRelayUrl, Int>()

    private fun gate(relay: NormalizedRelayUrl): Gate = gates.getOrPut(relay) { Gate(startCap) }

    /** Run [block] holding one of [relay]'s permits, respecting its current cap. */
    suspend fun <T> withPermit(
        relay: NormalizedRelayUrl,
        block: suspend () -> T,
    ): T {
        val g = gate(relay)
        g.acquire()
        try {
            return block()
        } finally {
            g.release()
        }
    }

    override fun onIncomingMessage(
        relay: IRelayClient,
        msgStr: String,
        msg: Message,
    ) {
        when (msg) {
            is ClosedMessage -> if (isConcurrencyComplaint(msg.message)) demote(relay.url)
            is NoticeMessage -> if (isConcurrencyComplaint(msg.message)) demote(relay.url)
            else -> Unit
        }
    }

    /** Step [relay] one rung down the cap ladder, unless it's already at the floor. */
    private fun demote(relay: NormalizedRelayUrl) {
        // Fast path: relays flood identical NOTICEs, so bail once at the floor
        // instead of counting them all (the demotion is monotonic and idempotent).
        if ((demotions[relay] ?: 0) >= ladder.size) return
        val step = demotions.merge(relay, 1, Int::plus)!!
        val cap = ladder[(step - 1).coerceIn(0, ladder.size - 1)]
        gate(relay).lower(cap)
        if (step <= ladder.size) {
            System.err.println("[limiter] ${relay.url} capped at $cap concurrent subs (complaint #$step)")
        }
    }

    private fun isConcurrencyComplaint(text: String): Boolean {
        val t = text.lowercase()
        return CONCURRENCY_MARKERS.any { it in t }
    }

    /** JSON-friendly view of which relays we throttled and how far. */
    fun snapshot(): Map<String, Any?> {
        val cappedAt = sortedMapOf<Int, Int>()
        for ((_, step) in demotions) {
            val cap = ladder[(step - 1).coerceIn(0, ladder.size - 1)]
            cappedAt.merge(cap, 1, Int::plus)
        }
        return mapOf(
            "start_cap" to startCap,
            "ladder" to ladder,
            "throttled_relays" to demotions.size,
            "capped_at" to cappedAt,
        )
    }

    fun hadThrottling(): Boolean = demotions.isNotEmpty()

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
        private val limit = AtomicInteger(initialLimit)
        private val mutex = Mutex()
        private var inUse = 0
        private val waiters = ArrayDeque<CompletableDeferred<Unit>>()

        suspend fun acquire() {
            val wait =
                mutex.withLock {
                    if (inUse < limit.get()) {
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
                while (inUse < limit.get() && waiters.isNotEmpty()) {
                    waiters.removeFirst().complete(Unit)
                    inUse++
                }
            }
        }

        /** Monotonically shrink the cap. Safe to call from any thread. */
        fun lower(newLimit: Int) {
            limit.updateAndGet { if (newLimit < it) newLimit else it }
        }
    }

    companion object {
        // Substrings (matched case-insensitively) that mean "you're opening too
        // many concurrent subscriptions / sending too fast" — the failure modes a
        // lower per-relay cap actually fixes. Auth/blocked/restricted/unsupported
        // are deliberately excluded: throttling wouldn't help those.
        private val CONCURRENCY_MARKERS =
            listOf(
                "too many concurrent",
                "concurrent req",
                "too many subscription",
                "number of subscriptions",
                "subscription limit",
                "too many req",
                "rate-limit",
                "rate limit",
                "ratelimit",
                "burst exhausted",
                "throttl",
                "too many messages",
                "slow down",
            )
    }
}
