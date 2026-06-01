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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
 * So completion is **activity-based**: [loading] stays true until either every expected relay has
 * EOSE'd, or the event stream has gone quiet for [idleTimeout] (a flood of events keeps resetting
 * that timer via [onActivity], so a window that is still streaming is never declared done). An
 * [absoluteCap] bounds the wait for pathological relays that dribble forever.
 */
class WindowLoadTracker(
    private val idleTimeout: Duration = 3.seconds,
    private val absoluteCap: Duration = 5.minutes,
) {
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private var expected: Set<NormalizedRelayUrl> = emptySet()
    private val responded = mutableSetOf<NormalizedRelayUrl>()
    private var watchdog: Job? = null

    // Incremented on every (re)start so a stale watchdog that wakes right as a new load begins
    // recognizes it has been superseded and bows out instead of completing the new window.
    private var generation = 0

    // Wall-clock of the last EOSE or event for the current window; the watchdog completes the
    // window once this stops advancing for [idleTimeout]. Volatile so the hot per-event path
    // ([onActivity]) stays lock-free.
    @Volatile
    private var lastActivityMs = 0L

    /** Begins a fresh window load: clears the responded set, raises [loading], and arms the watchdog. */
    @Synchronized
    fun startLoading(scope: CoroutineScope) {
        val gen = ++generation
        responded.clear()
        lastActivityMs = System.currentTimeMillis()
        _loading.value = true
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
    // newer load, the window already finished, or the idle/cap deadline is reached. Synchronized so
    // the generation/loading checks and the completion are atomic against startLoading/finish.
    @Synchronized
    private fun tick(
        gen: Int,
        now: Long,
        deadline: Long,
    ): Boolean {
        if (gen != generation || !_loading.value) return false
        if (now - lastActivityMs >= idleTimeout.inWholeMilliseconds || now >= deadline) {
            _loading.value = false
            return false
        }
        return true
    }

    /**
     * Records that the current window is still actively receiving events (stored OR live). Keeps the
     * idle watchdog from completing while a relay is mid-flood. Lock-free: just bumps a timestamp.
     */
    fun onActivity() {
        lastActivityMs = System.currentTimeMillis()
    }

    /** Records which relays the current REQ was sent to. Completes immediately if there are none. */
    @Synchronized
    fun setExpectedRelays(relays: Set<NormalizedRelayUrl>) {
        expected = relays
        if (relays.isEmpty() || responded.containsAll(relays)) finish()
    }

    /** Marks [relay] as having answered (EOSE or live event). Completes once all expected have. */
    @Synchronized
    fun onRelayResponded(relay: NormalizedRelayUrl) {
        lastActivityMs = System.currentTimeMillis()
        responded.add(relay)
        if (expected.isNotEmpty() && responded.containsAll(expected)) finish()
    }

    @Synchronized
    private fun finish() {
        _loading.value = false
        watchdog?.cancel()
        watchdog = null
    }

    companion object {
        private const val IDLE_CHECK_MS = 500L
    }
}

/**
 * Builds the standard [SubscriptionListener] that feeds this tracker: every event (stored backfill
 * included) marks activity so a relay mid-flood is never mistaken for done, and an EOSE or live
 * event marks that relay answered. [forward] is invoked with the same EOSE / live-event signal so
 * the owning EOSE manager can record the relay's EOSE timestamp (its usual `newEose`).
 */
fun WindowLoadTracker.trackingListener(forward: (NormalizedRelayUrl, List<Filter>?) -> Unit): SubscriptionListener =
    object : SubscriptionListener {
        override fun onEose(
            relay: NormalizedRelayUrl,
            forFilters: List<Filter>?,
        ) {
            onRelayResponded(relay)
            forward(relay, forFilters)
        }

        override fun onEvent(
            event: Event,
            isLive: Boolean,
            relay: NormalizedRelayUrl,
            forFilters: List<Filter>?,
        ) {
            onActivity()
            if (isLive) {
                onRelayResponded(relay)
                forward(relay, forFilters)
            }
        }
    }
