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

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Tracks when one relay-subscription "window" has finished loading, so callers can wait for the
 * WHOLE set of relays to answer instead of declaring victory on the first EOSE.
 *
 * A subscription fans a single REQ out to several relays. The first EOSE is a misleading "done"
 * signal: a fast but near-empty relay can EOSE in milliseconds while the relay that actually holds
 * the data is still connecting (or stuck in an auth handshake). An auto-fill loop driven by the
 * first EOSE would therefore widen the time window again before the slow relay ever answered,
 * walking the window back uselessly.
 *
 * [loading] stays true until EVERY [setExpectedRelays] relay has answered (an EOSE, or a live event
 * that implies the stored set already drained), or until [timeout] elapses as a backstop for relays
 * that never answer (down, or looping on `auth-required`).
 */
class WindowLoadTracker(
    private val timeout: Duration = 15.seconds,
) {
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private var expected: Set<NormalizedRelayUrl> = emptySet()
    private val responded = mutableSetOf<NormalizedRelayUrl>()
    private var timeoutJob: Job? = null

    /** Begins a fresh window load: clears the responded set, raises [loading], and arms the timeout. */
    @Synchronized
    fun startLoading(scope: CoroutineScope) {
        responded.clear()
        _loading.value = true
        timeoutJob?.cancel()
        timeoutJob =
            scope.launch {
                delay(timeout)
                finish()
            }
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
        responded.add(relay)
        if (expected.isNotEmpty() && responded.containsAll(expected)) finish()
    }

    @Synchronized
    private fun finish() {
        _loading.value = false
        timeoutJob?.cancel()
        timeoutJob = null
    }
}
