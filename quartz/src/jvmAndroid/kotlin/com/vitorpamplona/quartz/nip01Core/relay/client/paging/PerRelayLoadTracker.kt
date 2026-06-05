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
package com.vitorpamplona.quartz.nip01Core.relay.client.paging

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

/**
 * Tracks which relays currently have a demand-driven history page **in flight**, so the loading card
 * can show a spinner while any relay is fetching and clear it the moment they've all answered (or
 * parked). Unlike [WindowLoadTracker] this has no notion of a "window" or "round" — relays are advanced
 * one page at a time, independently, by their on-screen markers, so completion is simply "nothing in
 * flight."
 *
 * A single backstop covers a relay that accepts a REQ and then goes silent (auth-walled / dead): if
 * nothing has been heard from ANY in-flight relay for [silenceMs], the still-pending relays are dropped
 * from the in-flight set (so the spinner clears) and reported to [onSilenced] so the owner can flag them
 * stalled. Relays that answer with CLOSED / cannot-connect are settled directly by the owner and don't
 * need the watchdog.
 */
class PerRelayLoadTracker(
    private val name: String,
    private val silenceMs: Long = 15_000L,
    private val onSilenced: (Set<NormalizedRelayUrl>) -> Unit = {},
) {
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val inFlight = ConcurrentHashMap.newKeySet<NormalizedRelayUrl>()

    @Volatile
    private var lastActivityMs = 0L

    @Volatile
    private var watchdog: Job? = null

    // Delays dropping the spinner so back-to-back pages don't flicker it off between each one.
    @Volatile
    private var clearJob: Job? = null

    @Volatile
    private var scope: CoroutineScope? = null

    fun bind(scope: CoroutineScope) {
        this.scope = scope
    }

    fun isInFlight(relay: NormalizedRelayUrl) = inFlight.contains(relay)

    fun count() = inFlight.size

    /** A relay's next page was just requested. Raises the spinner and (re)arms the silence watchdog. */
    @Synchronized
    fun onAdvance(relay: NormalizedRelayUrl) {
        clearJob?.cancel() // a new page is starting — keep the spinner up, no flicker
        clearJob = null
        inFlight.add(relay)
        lastActivityMs = System.currentTimeMillis()
        _loading.value = true
        ensureWatchdog()
    }

    /** A sign of life from a relay (an event). Keeps the silence watchdog from firing. */
    fun onActivity() {
        lastActivityMs = System.currentTimeMillis()
    }

    /**
     * A relay answered (EOSE / CLOSED / cannot-connect). Drops it from in-flight. When the last one
     * settles, the spinner is dropped after a short linger rather than immediately, so a relay paging
     * page-after-page (each page settles then the marker fires the next) keeps a steady spinner instead
     * of flickering it off for the few ms between pages. The linger is cancelled the moment a new page
     * starts ([onAdvance]).
     */
    @Synchronized
    fun onSettled(relay: NormalizedRelayUrl) {
        lastActivityMs = System.currentTimeMillis()
        if (inFlight.remove(relay) && inFlight.isEmpty()) scheduleClear()
    }

    private fun scheduleClear() {
        clearJob?.cancel()
        val s = scope
        if (s == null) {
            _loading.value = false
            return
        }
        clearJob =
            s.launch {
                delay(LOADING_LINGER_MS)
                synchronized(this@PerRelayLoadTracker) {
                    if (inFlight.isEmpty()) _loading.value = false
                }
            }
    }

    /** Drops everything (e.g. account/conversation switched). */
    @Synchronized
    fun reset() {
        inFlight.clear()
        clearJob?.cancel()
        clearJob = null
        _loading.value = false
        watchdog?.cancel()
        watchdog = null
    }

    private fun ensureWatchdog() {
        if (watchdog?.isActive == true) return
        val s = scope ?: return
        watchdog =
            s.launch {
                while (isActive) {
                    delay(WATCHDOG_TICK_MS)
                    val silenced =
                        synchronized(this@PerRelayLoadTracker) {
                            if (inFlight.isNotEmpty() && System.currentTimeMillis() - lastActivityMs > silenceMs) {
                                val pending = inFlight.toSet()
                                inFlight.clear()
                                _loading.value = false
                                pending
                            } else {
                                emptySet()
                            }
                        }
                    if (silenced.isNotEmpty()) {
                        Log.d(TAG) { "[$name] silenced (no response ${silenceMs}ms): ${silenced.map { it.url }}" }
                        onSilenced(silenced)
                    }
                    if (inFlight.isEmpty()) break
                }
            }
    }

    companion object {
        private const val TAG = "DMPagination"
        private const val WATCHDOG_TICK_MS = 1_000L

        // How long to keep the spinner up after the last page settles, to bridge the gap to the next
        // back-to-back page so the card doesn't flicker between every page.
        private const val LOADING_LINGER_MS = 600L
    }
}
