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
package com.vitorpamplona.amethyst.service.relayClient

import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.Log

/**
 * Detects the "Tor is Active but every circuit is dead" state and asks [onCircuitsDead] to
 * self-heal it.
 *
 * Tor can reach [com.vitorpamplona.amethyst.ui.tor.TorServiceStatus.Active] — SOCKS proxy up,
 * bootstrap "succeeded" off cached consensus — while no exit circuit actually works (the
 * `ExitTimeout` / `RESOLVEFAILED` barrage). The lifecycle watchdogs in
 * [com.vitorpamplona.amethyst.ui.tor.TorManager] can't see this: they only arm while status is
 * `Connecting`. The relay layer is the only place that has both halves of the signal — per-relay
 * success ([RelayConnectionListener.onConnected]) and failure
 * ([RelayConnectionListener.onCannotConnect]) — plus knowledge of which relays are Tor-routed.
 *
 * The discriminator (the whole point) is: fire only when, while Tor is Active and connectivity is
 * up, there have been at least [FAIL_THRESHOLD] Tor-routed failures within [WINDOW_MS] **and zero
 * Tor-routed successes in that window**. One successful Tor open means circuits work — suppress.
 * Gating on connectivity avoids resetting Tor during a general network outage (where clearnet
 * relays fail too). Arti emits no per-stream success log, so this success signal can only come
 * from the relay layer — a failure-only signal can't tell "all dead" from "some dead", and would
 * reset Tor every time a few dead relays are dialed.
 *
 * Register with [register] and tear down with [unregister]. All callbacks arrive on relay/OkHttp
 * threads, so the sliding window is guarded by an intrinsic lock.
 */
class TorCircuitHealthTracker(
    private val client: INostrClient,
    private val isTorRouted: (NormalizedRelayUrl) -> Boolean,
    private val isTorActive: () -> Boolean,
    private val isConnectivityActive: () -> Boolean,
    private val onCircuitsDead: () -> Unit,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    /** Epoch-millis of Tor-routed failures still inside the rolling [WINDOW_MS]. */
    private val failures = ArrayDeque<Long>()

    /**
     * Epoch-millis of the last Tor-routed success, or null if we've never observed one. Null
     * (not "now") at construction on purpose: before any success we must NOT assume circuits are
     * healthy, otherwise an all-dead-from-cold-start state would be suppressed for a full window.
     */
    @Volatile private var lastTorSuccessAtMs: Long? = null

    private val listener =
        object : RelayConnectionListener {
            override fun onConnected(
                relay: IRelayClient,
                pingMillis: Int,
                compressed: Boolean,
            ) {
                if (!isTorRouted(relay.url)) return
                // One Tor success means circuits work — disarm the window.
                synchronized(this@TorCircuitHealthTracker) {
                    lastTorSuccessAtMs = nowMs()
                    failures.clear()
                }
            }

            override fun onCannotConnect(
                relay: IRelayClient,
                errorMessage: String,
            ) {
                if (!isTorRouted(relay.url)) return
                if (!isTorActive() || !isConnectivityActive()) return

                val now = nowMs()
                val fire =
                    synchronized(this@TorCircuitHealthTracker) {
                        failures.addLast(now)
                        while (failures.isNotEmpty() && now - failures.first() > WINDOW_MS) {
                            failures.removeFirst()
                        }
                        val lastSuccess = lastTorSuccessAtMs
                        val noSuccessInWindow = lastSuccess == null || now - lastSuccess >= WINDOW_MS
                        if (failures.size >= FAIL_THRESHOLD && noSuccessInWindow) {
                            // Re-arm: drop the window and push the success marker forward so a
                            // burst of late failures from the same dead state doesn't re-fire
                            // before the reset has had a chance to take effect.
                            failures.clear()
                            lastTorSuccessAtMs = now
                            true
                        } else {
                            false
                        }
                    }

                if (fire) {
                    Log.w(TAG) { "Tor Active but $FAIL_THRESHOLD+ Tor relays failed with no success in ${WINDOW_MS}ms — requesting self-heal" }
                    onCircuitsDead()
                }
            }
        }

    fun register() {
        client.addConnectionListener(listener)
    }

    fun unregister() {
        client.removeConnectionListener(listener)
    }

    companion object {
        const val TAG = "TorCircuitHealthTracker"

        /** Tor-routed failures within [WINDOW_MS] (and zero successes) needed to declare circuits dead. */
        const val FAIL_THRESHOLD = 8

        /** Rolling window for counting failures and the "no success since" check. */
        const val WINDOW_MS = 30_000L
    }
}
