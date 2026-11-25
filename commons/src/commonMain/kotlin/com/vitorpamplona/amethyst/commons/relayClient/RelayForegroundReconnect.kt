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
package com.vitorpamplona.amethyst.commons.relayClient

import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Redials the relays that dropped while the app was in the background, as soon as it comes back.
 *
 * Quartz keeps a reconnect backoff per relay (up to five minutes) that survives `disconnect()`, and
 * `connect()` respects it, so a relay that failed while the app was away (doze, a dead network, the
 * always-on notification service keeping the pool up) could leave the user staring at a stale feed
 * for minutes after they return. On a return this clears every relay's backoff and asks for a routine
 * reconnect that skips the retry delays: relays that are down dial now, healthy connections stay.
 *
 * Each window that shows the app reports [windowStarted] / [windowStopped] (Android activities'
 * ON_START/ON_STOP, a desktop window's restore/minimize). The app is in the background only while no
 * window is started, so moving between the app's own windows is not a return. Two guards keep it from
 * becoming a redial storm: a trip shorter than [minAwayMs] is not a return (a rotation, a glance at
 * the notification shade), and the pool is forgiven at most once per [minIntervalMs] however often
 * the user switches apps; past that, the per-relay backoff does its job again.
 *
 * Expects its calls on one thread (the UI thread, where lifecycle callbacks arrive).
 */
class RelayForegroundReconnect(
    private val client: INostrClient,
    private val minAwayMs: Long = MIN_AWAY_MS,
    private val minIntervalMs: Long = MIN_INTERVAL_MS,
    private val nowMs: () -> Long = { TimeUtils.nowMillis() },
) {
    private var startedWindows = 0

    /** When the last window stopped, or null while one is started or before the first start. */
    private var backgroundedAtMs: Long? = null

    private var lastRedialMs: Long? = null

    /** A window that shows the app became visible. Returns true when this redialed the pool. */
    fun windowStarted(): Boolean {
        startedWindows++
        if (startedWindows != 1) return false

        val since = backgroundedAtMs ?: return false
        backgroundedAtMs = null

        // Wall clock on purpose: a phone in deep sleep does not advance the monotonic clock, so an
        // hour away with the screen off could read as a second.
        val now = nowMs()
        if (now - since < minAwayMs) return false
        val last = lastRedialMs
        if (last != null && now - last < minIntervalMs) return false
        lastRedialMs = now

        Log.d(TAG) { "Back from ${(now - since) / 1000}s in the background: forgiving relay backoffs and redialing" }
        // Eager, so the debounced reconnect below cannot drop it (see INostrClient.resetBackoff).
        client.resetBackoff()
        client.reconnect(onlyIfChanged = true, ignoreRetryDelays = true)
        return true
    }

    /** A window that shows the app is no longer visible. */
    fun windowStopped() {
        if (startedWindows == 0) return
        startedWindows--
        if (startedWindows == 0) backgroundedAtMs = nowMs()
    }

    companion object {
        private const val TAG = "RelayForegroundReconnect"
        const val MIN_AWAY_MS = 5_000L
        const val MIN_INTERVAL_MS = 30_000L
    }
}
