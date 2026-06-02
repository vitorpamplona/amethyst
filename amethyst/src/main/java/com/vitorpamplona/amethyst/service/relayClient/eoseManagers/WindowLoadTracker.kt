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
package com.vitorpamplona.amethyst.service.relayClient.eoseManagers

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Tracks when one relay-subscription "window" has finished loading, so callers (the rooms-screen
 * auto-fill loop) can wait for the WHOLE response instead of declaring victory on the first EOSE.
 *
 * A subscription fans a single REQ out to several relays. The first EOSE is a misleading "done"
 * signal: a fast but near-empty relay can EOSE in milliseconds while the relay that actually holds
 * the data is still connecting, stuck in an auth handshake, or busy streaming thousands of stored
 * events. An auto-fill loop driven by the first EOSE — or by a fixed wall-clock timeout — would
 * widen the window again mid-stream, before the events were even decrypted into rooms, re-issuing
 * an ever-wider REQ that re-downloads the whole history over and over.
 *
 * Completion is therefore **per-relay terminal-state** based, not wall-clock based. A relay is
 * *settled* once it answers with a terminal signal — an EOSE (stored backfill done), a CLOSED (it
 * rejected the REQ, e.g. `auth-required`), or a cannot-connect (it is unreachable). The load is done
 * when **every targeted relay has settled** ([settled] ⊇ [expected]). This is the only signal that
 * survives the real world: relays connect over a wide spread (tens of seconds on mobile), and some
 * answer only with CLOSED — a quiet-time heuristic fires in the gap between two relays connecting and
 * mistakes a half-loaded window for a finished one, which is exactly how a load reports "1 event"
 * when a hundred are still on the way.
 *
 * Three backstops cover misbehaving relays. If every relay we're still waiting on has at least been
 * *heard from* (any event, EOSE, CLOSED, or cannot-connect) but one streamed events without ever
 * sending EOSE, an [idleTimeout] of quiet completes the load — the "heard from" gate is what keeps
 * this from firing in a connection gap. A relay that *received our REQ* ([onReqSent]) but then went
 * completely silent — no event, no EOSE, no CLOSED — for [silenceTimeout] is given up on: an
 * auth-walled relay (ditto, paid relays) commonly accepts the REQ and answers nothing, and measuring
 * from REQ-delivery (not window start) means a slow connect doesn't count against it. Such relays are
 * reported to [onAbandoned] so the owner can drop them from its pager too. And an [absoluteCap] bounds
 * a relay that hangs in the connect itself, before any REQ is even sent.
 */
class WindowLoadTracker(
    // Short label for the DMPagination logs (e.g. "giftwrap", "rooms.nip04", "convo.nip04").
    private val name: String = "dm",
    private val idleTimeout: Duration = 3.seconds,
    private val silenceTimeout: Duration = 10.seconds,
    private val absoluteCap: Duration = 5.minutes,
    // Invoked with the relays that received a REQ but stayed silent past [silenceTimeout] when a load
    // finishes — the owner gives up on them in its pager so they stop blocking future rounds.
    private val onAbandoned: (Set<NormalizedRelayUrl>) -> Unit = {},
) {
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    // Relays the current REQ was sent to. Volatile: written on IO (updateFilter), read on the
    // listener threads and the watchdog.
    @Volatile
    private var expected: Set<NormalizedRelayUrl> = emptySet()

    // Relays that have produced any signal at all (event / EOSE / CLOSED / cannot-connect). The idle
    // backstop only arms once this covers [expected], so a still-connecting relay can't be skipped.
    private val heardFrom = ConcurrentHashMap.newKeySet<NormalizedRelayUrl>()

    // Relays that reached a terminal signal (EOSE / CLOSED / cannot-connect). When this covers
    // [expected] the stored backfill is complete on every relay and the load is done.
    private val settled = ConcurrentHashMap.newKeySet<NormalizedRelayUrl>()

    // When the REQ was actually delivered to each relay (post-connect). The silence backstop measures
    // from here, not window start, so a slow connect isn't mistaken for a dead relay.
    private val reqSentAt = ConcurrentHashMap<NormalizedRelayUrl, Long>()

    private var watchdog: Job? = null

    // Incremented on every (re)start so a stale watchdog that wakes right as a new load begins
    // recognizes it has been superseded and bows out instead of completing the new window.
    private var generation = 0

    // Wall-clock of the last signal for the current window; the idle backstop completes the window
    // once this stops advancing for [idleTimeout]. Volatile so the hot per-event path stays lock-free.
    @Volatile
    private var lastActivityMs = 0L

    /** Begins a fresh window load: clears the per-relay sets, raises [loading], and arms the watchdog. */
    @Synchronized
    fun startLoading(scope: CoroutineScope) {
        val gen = ++generation
        expected = emptySet()
        heardFrom.clear()
        settled.clear()
        reqSentAt.clear()
        lastActivityMs = System.currentTimeMillis()
        val wasLoading = _loading.value
        _loading.value = true
        Log.d(TAG) { "[$name] load start" + if (!wasLoading) "" else " (restart)" }
        watchdog?.cancel()
        watchdog =
            scope.launch {
                val deadline = System.currentTimeMillis() + absoluteCap.inWholeMilliseconds
                while (isActive) {
                    delay(IDLE_CHECK_MS)
                    if (!tick(gen, System.currentTimeMillis(), deadline)) break
                }
            }
    }

    // One watchdog poll. Returns false (stop polling) when this watchdog has been superseded by a
    // newer load, the window already finished, or a completion deadline is reached. Synchronized so
    // the generation/loading checks and the completion are atomic against startLoading/finish.
    @Synchronized
    private fun tick(
        gen: Int,
        now: Long,
        deadline: Long,
    ): Boolean {
        if (gen != generation || !_loading.value) return false
        if (expected.isNotEmpty()) {
            // A relay is accounted for once it reached a terminal signal, or it received our REQ and
            // then stayed completely silent past [silenceTimeout] (an auth-walled / dead relay). Once
            // every relay is accounted for, nothing more is coming.
            if (expected.all { settled.contains(it) || silencedOut(it, now) }) {
                finish("settled/silent")
                return false
            }
            // Idle backstop: every relay we're still waiting on has at least streamed something (so this
            // isn't a connection gap) and the stream has gone quiet. Silenced/settled relays don't count.
            val stillWaiting = expected.filterNot { settled.contains(it) || silencedOut(it, now) }
            if (stillWaiting.all { heardFrom.contains(it) } && now - lastActivityMs >= idleTimeout.inWholeMilliseconds) {
                finish("idle")
                return false
            }
        }
        if (now >= deadline) {
            finish("cap")
            return false
        }
        return true
    }

    // A relay that received its REQ but produced no signal at all for [silenceTimeout]. Measured from
    // REQ-delivery so a slow connect (or a still-connecting relay, which has no [reqSentAt]) is never
    // counted as silent.
    private fun silencedOut(
        relay: NormalizedRelayUrl,
        now: Long,
    ): Boolean = relay !in heardFrom && (reqSentAt[relay]?.let { now - it >= silenceTimeout.inWholeMilliseconds } ?: false)

    /** Records which relays the current REQ was sent to. Completes immediately if there are none. */
    @Synchronized
    fun setExpectedRelays(relays: Set<NormalizedRelayUrl>) {
        expected = relays
        if (relays.isEmpty()) {
            finish("no relays")
        } else if (settled.containsAll(relays)) {
            finish("all relays")
        }
    }

    /**
     * Records that the REQ was delivered to [relayUrl] (post-connect). Starts that relay's silence clock.
     * Ignored for relays outside the current [expected] set (or before it is known).
     */
    @Synchronized
    fun onReqSent(relayUrl: String) {
        val relay = expected.firstOrNull { it.url == relayUrl } ?: return
        reqSentAt.putIfAbsent(relay, System.currentTimeMillis())
    }

    /** A non-terminal sign of life from [relay] (a stored or live event). Keeps the idle timer alive. */
    fun onRelayEvent(relay: NormalizedRelayUrl) {
        heardFrom.add(relay)
        lastActivityMs = System.currentTimeMillis()
    }

    /**
     * A terminal signal from [relay] — EOSE, CLOSED, or cannot-connect. Once every expected relay has
     * settled the stored backfill is complete and the load finishes.
     */
    @Synchronized
    fun onRelaySettled(relay: NormalizedRelayUrl) {
        lastActivityMs = System.currentTimeMillis()
        heardFrom.add(relay)
        settled.add(relay)
        if (expected.isNotEmpty() && settled.containsAll(expected)) finish("all relays")
    }

    // Idempotent: only the first call after a load actually completes (and logs); later calls no-op.
    @Synchronized
    private fun finish(reason: String) {
        if (!_loading.value) return
        watchdog?.cancel()
        watchdog = null
        // Give up the silent relays BEFORE flipping [loading]: the owner's round collector reacts to
        // loading=false by recomputing exhaustion from its pager, so the give-up has to land first.
        val abandoned = expected.filterTo(mutableSetOf()) { silencedOut(it, System.currentTimeMillis()) }
        Log.d(TAG) { "[$name] load done: $reason" + if (abandoned.isEmpty()) "" else " (gave up on silent ${abandoned.map { it.url }})" }
        if (abandoned.isNotEmpty()) onAbandoned(abandoned)
        _loading.value = false
    }

    companion object {
        private const val TAG = "DMPagination"
        private const val IDLE_CHECK_MS = 500L
    }
}

/**
 * Builds the standard [SubscriptionListener] that feeds this tracker. Every event (stored backfill
 * included) is a non-terminal sign of life from its relay; an EOSE, CLOSED, or cannot-connect settles
 * that relay. [onEachEvent] is invoked for every event (stored or live) for optional instrumentation;
 * [forward] carries the EOSE / live-event signal so the owning EOSE manager can record the relay's
 * timestamp (its usual `newEose`).
 */
fun WindowLoadTracker.trackingListener(
    onEachEvent: (Event) -> Unit = {},
    forward: (NormalizedRelayUrl, List<Filter>?) -> Unit,
): SubscriptionListener =
    object : SubscriptionListener {
        override fun onEose(
            relay: NormalizedRelayUrl,
            forFilters: List<Filter>?,
        ) {
            onRelaySettled(relay)
            forward(relay, forFilters)
        }

        override fun onEvent(
            event: Event,
            isLive: Boolean,
            relay: NormalizedRelayUrl,
            forFilters: List<Filter>?,
        ) {
            onRelayEvent(relay)
            onEachEvent(event)
            if (isLive) {
                forward(relay, forFilters)
            }
        }

        override fun onClosed(
            message: String,
            relay: NormalizedRelayUrl,
            forFilters: List<Filter>?,
        ) {
            onRelaySettled(relay)
        }

        override fun onCannotConnect(
            relay: NormalizedRelayUrl,
            message: String,
            forFilters: List<Filter>?,
        ) {
            onRelaySettled(relay)
        }
    }
