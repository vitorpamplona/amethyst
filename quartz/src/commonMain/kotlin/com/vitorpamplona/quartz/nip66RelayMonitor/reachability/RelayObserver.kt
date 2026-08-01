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
package com.vitorpamplona.quartz.nip66RelayMonitor.reachability

import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.AuthMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EoseMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.NoticeMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.concurrent.ConcurrentMap
import kotlin.concurrent.Volatile
import kotlin.time.TimeSource

/**
 * What a client learns about relays just by talking to them.
 *
 * A NIP-66 monitor normally probes: it opens connections for the sole purpose of
 * measuring, and then throws them away. A client that is already subscribing,
 * fetching and publishing has better data available for free — measured under
 * real load, against the relays it actually uses, at the concurrency it actually
 * runs. Attached to a client as a [RelayConnectionListener], this collects it.
 *
 * Everything here is **observed**. Nothing is copied from a relay's NIP-11
 * document, and that is deliberate: a relay's self-description is available to
 * anyone who asks for it, so republishing it under a monitor's signature adds
 * nothing but a chance to be stale. Where the two disagree — a relay that
 * advertises open reads and then sends AUTH — the observation is the half worth
 * having, and copying the claim would erase it.
 *
 * ## Threading
 *
 * Callbacks for one relay arrive on that relay's own socket thread, so a single
 * [Observation] is only ever written by one thread. The fields are `@Volatile`
 * for visibility to the reader that publishes them, not for mutual exclusion,
 * and the counters need no atomics. The client-wide tallies ARE shared and use
 * [ConcurrentMap.merge].
 *
 * ## Why nothing is removed
 *
 * Observations are marked reported rather than deleted. A long-lived connection
 * only fires `onConnected` once, so a measurement that vanished when it was
 * published would leave the relays we know best — an upstream whose socket has
 * been open for hours — with nothing to say about them ever again. The map is
 * bounded by the number of distinct relays the client has ever dialled.
 */
class RelayObserver : RelayConnectionListener {
    class Observation(
        val url: NormalizedRelayUrl,
    ) {
        @Volatile var rttOpenMs: Long? = null

        @Volatile var firstReqAt: TimeSource.Monotonic.ValueTimeMark? = null

        @Volatile var rttReadMs: Long? = null

        @Volatile var firstEventAt: TimeSource.Monotonic.ValueTimeMark? = null

        @Volatile var rttWriteMs: Long? = null

        /** It opened, or served something. Nothing more is claimed by this. */
        @Volatile var reachable: Boolean = false

        /** Why it did not open, verbatim from the transport. */
        @Volatile var error: String? = null

        /** It sent AUTH, or CLOSED a subscription demanding it. Measured, not read off NIP-11. */
        @Volatile var authRequired: Boolean = false

        /** The NIP-01 machine-readable prefix of the last CLOSED. */
        @Volatile var closedReason: String? = null

        /** The last NOTICE text, truncated — often the only explanation a relay gives. */
        @Volatile var notice: String? = null

        /** Set by every observation, cleared when published. See the class doc. */
        @Volatile var unreported: Boolean = false

        internal fun touch() {
            unreported = true
        }
    }

    private val seen = ConcurrentMap<NormalizedRelayUrl, Observation>()

    // Client-wide tallies, across every relay. Separate from the per-relay state
    // because they answer a different question — "how did this run go" rather
    // than "what shall I record about this relay" — and because a summary must
    // survive publishing, which clears the per-relay flags.
    private val closedByReason = ConcurrentMap<String, Long>()
    private val noticeSamples = ConcurrentMap<String, Long>()
    private val authChallenges = ConcurrentMap<String, Long>()

    private fun of(relay: IRelayClient) = seen.getOrPut(relay.url) { Observation(relay.url) }

    override fun onConnecting(relay: IRelayClient) {
        val o = of(relay)
        // Cleared, not kept: a reconnect is a fresh attempt, and carrying an old
        // error forward would report a working relay as broken for as long as the
        // process lives after one bad minute.
        o.error = null
        o.touch()
    }

    override fun onConnected(
        relay: IRelayClient,
        pingMillis: Int,
        compressed: Boolean,
    ) {
        val o = of(relay)
        o.reachable = true
        o.error = null
        // The TRANSPORT's number, not ours. pingMillis is
        // receivedResponseAtMillis - sentRequestAtMillis, so it starts when the
        // upgrade request actually goes out and excludes everything before it.
        //
        // Timing onConnecting -> onConnected instead measures our own dispatcher
        // queue as if it were the relay's latency. Under a 16,507-relay fan-out
        // that queue dominates: published records showed a median rtt-open of
        // 33.5 SECONDS and a max of 90, against a true minimum of 140ms. That is
        // the field aggregators rank relays by, so it was worse than publishing
        // nothing — a slow-looking relay that is not slow.
        //
        // Zero or negative means the transport could not time it; no timing is
        // published rather than a fabricated one.
        o.rttOpenMs = pingMillis.toLong().takeIf { it > 0 }
        o.touch()
    }

    override fun onCannotConnect(
        relay: IRelayClient,
        errorMessage: String,
    ) {
        val o = of(relay)
        // NOT `reachable = false`. A relay that answered an hour ago and is down
        // now is a different thing from one that never answered at all, and only
        // the writer decides which record that becomes.
        o.error = errorMessage.take(MAX_TEXT)
        o.touch()
    }

    /**
     * Outgoing commands start the read and write clocks — the FIRST of each per
     * relay, since a later REQ on a warm socket measures nothing about the relay.
     */
    override fun onSent(
        relay: IRelayClient,
        cmdStr: String,
        cmd: Command,
        success: Boolean,
    ) {
        if (!success) return
        val o = of(relay)
        when (cmd) {
            is ReqCmd -> if (o.firstReqAt == null) o.firstReqAt = TimeSource.Monotonic.markNow()
            is EventCmd -> if (o.firstEventAt == null) o.firstEventAt = TimeSource.Monotonic.markNow()
            else -> Unit
        }
    }

    override fun onIncomingMessage(
        relay: IRelayClient,
        msgStr: String,
        msg: Message,
    ) {
        val o = of(relay)
        when (msg) {
            is EoseMessage -> {
                if (o.rttReadMs == null) {
                    o.firstReqAt?.let {
                        o.rttReadMs = it.elapsedNow().inWholeMilliseconds.coerceAtLeast(0)
                        o.touch()
                    }
                }
            }

            is OkMessage -> {
                if (o.rttWriteMs == null) {
                    o.firstEventAt?.let {
                        o.rttWriteMs = it.elapsedNow().inWholeMilliseconds.coerceAtLeast(0)
                        o.touch()
                    }
                }
            }

            // Serving an event is proof of life even from a relay that never
            // sends EOSE — some do not, and treating those as unresponsive would
            // shed relays that work perfectly well. Guarded because this fires
            // for EVERY event on every socket: an unconditional write here would
            // bounce a cache line between threads to say nothing new.
            is EventMessage -> {
                if (!o.reachable) {
                    o.reachable = true
                    o.touch()
                }
            }

            is AuthMessage -> {
                o.authRequired = true
                o.touch()
                authChallenges.merge(relay.url.url, 1L) { a, b -> a + b }
            }

            is NoticeMessage -> {
                val text = msg.message.trim().take(NOTICE_KEY)
                o.notice = text
                o.touch()
                if (noticeSamples.size() < MAX_DISTINCT_NOTICES) noticeSamples.merge(text, 1L) { a, b -> a + b }
            }

            is ClosedMessage -> {
                val reason = prefixOf(msg.message)
                o.closedReason = reason
                // NIP-42 refusal, in the shape relays use when the subscription
                // is what got rejected rather than the connection.
                if (reason == AUTH_REQUIRED) o.authRequired = true
                o.touch()
                closedByReason.merge(reason, 1L) { a, b -> a + b }
            }

            else -> Unit
        }
    }

    /**
     * Record a measurement taken OUTSIDE the websocket client — a TCP probe, a
     * DNS failure, a host struck out after repeated silence.
     *
     * This class is a [RelayConnectionListener], so on its own it can only report
     * on relays something opened a websocket to. On a large fan-out that is a
     * small minority, and it is the wrong minority: the cheap checks that decide
     * NOT to dial are precisely the ones that learn a relay is gone, and their
     * findings had nowhere to go. Measured on a 16,507-relay list — 104 records
     * published, because everything else was ruled out before the client saw it.
     *
     * A monitor that only reports what it happened to connect to is not a census.
     *
     * [rttOpenMs] is whatever was actually measured; null means reachable with no
     * timing, and no timing is ever invented.
     */
    fun record(
        relay: NormalizedRelayUrl,
        reachable: Boolean,
        rttOpenMs: Long? = null,
        error: String? = null,
    ) {
        val o = seen.getOrPut(relay) { Observation(relay) }
        if (reachable) {
            o.reachable = true
            o.error = null
            // Kept on the Observation, which is never removed — only marked
            // reported — so a measurement survives every later flush.
            rttOpenMs?.let { o.rttOpenMs = it }
        } else {
            // Same rule as onCannotConnect: a relay that answered earlier is not
            // demoted by one failed probe. The writer decides what record that
            // becomes, and "answered, then a probe failed" is not "dead".
            o.error = (error ?: "unreachable").take(MAX_TEXT)
        }
        o.touch()
    }

    /**
     * Everything observed since the last call, marked reported as it is read.
     *
     * A relay whose state has not changed is left out: writing its record again
     * would refresh a freshness window that nothing re-measured.
     */
    fun collectUnreported(): List<Observation> =
        seen
            .snapshot()
            .values
            .filter { it.unreported }
            .onEach { it.unreported = false }

    /** Every relay ever observed, whether or not it has changed. */
    fun all(): Collection<Observation> = seen.snapshot().values

    fun observationOf(relay: NormalizedRelayUrl): Observation? = seen[relay]

    fun hadFeedback(): Boolean = authChallenges.size() > 0 || closedByReason.size() > 0 || noticeSamples.size() > 0

    /**
     * A run-level summary of the feedback relays gave — the frames a client
     * otherwise never surfaces, so a failed REQ can be explained instead of
     * guessed at.
     */
    fun summary(): Map<String, Any?> {
        val notices = noticeSamples.snapshot()
        return mapOf(
            "auth_challenges" to authChallenges.snapshot().values.sum(),
            "auth_required_relays" to authChallenges.size(),
            // Sorted into a LinkedHashMap rather than toSortedMap(): that one is
            // java.util and this file is commonMain, so it built on JVM and broke
            // the native targets.
            "closed_by_reason" to
                closedByReason
                    .snapshot()
                    .entries
                    .sortedBy { it.key }
                    .associate { it.key to it.value },
            "notices" to notices.values.sum(),
            "notice_top" to
                notices.entries
                    .sortedByDescending { it.value }
                    .take(TOP_NOTICES)
                    .map { "${it.key} (${it.value})" },
        )
    }

    companion object {
        private const val MAX_TEXT = 200
        private const val NOTICE_KEY = 80
        private const val MAX_DISTINCT_NOTICES = 500
        private const val TOP_NOTICES = 8
        private const val AUTH_REQUIRED = "auth-required"
        private const val OTHER = "other"
        private const val MAX_PREFIX = 24

        /**
         * The NIP-01 machine-readable prefix — the word before `:` — or `other`.
         *
         * The colon is required. `substringBefore` returns the WHOLE string when
         * the separator is absent, so without this check a relay's free-form
         * CLOSED prose became its own tally key and the map's cardinality grew
         * with the number of distinct sentences relays happened to write.
         */
        fun prefixOf(message: String): String {
            if (!message.contains(':')) return OTHER
            val head = message.substringBefore(':').trim().lowercase()
            return head.ifEmpty { OTHER }.take(MAX_PREFIX)
        }
    }
}
