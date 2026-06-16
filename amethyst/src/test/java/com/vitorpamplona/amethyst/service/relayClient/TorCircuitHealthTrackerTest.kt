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

import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [TorCircuitHealthTracker]'s discriminator. Drives the captured
 * [RelayConnectionListener] directly with a virtual clock — no Arti, no real relay client.
 */
class TorCircuitHealthTrackerTest {
    private val torRelay = NormalizedRelayUrl("wss://torrelay.example/")
    private val clearRelay = NormalizedRelayUrl("wss://clearrelay.example/")

    @Test
    fun `fires after threshold Tor failures with no success in window`() {
        val h = Harness()
        repeat(TorCircuitHealthTracker.FAIL_THRESHOLD) { h.failTor() }
        assertEquals(1, h.deadCount)
    }

    @Test
    fun `does not fire below threshold`() {
        val h = Harness()
        repeat(TorCircuitHealthTracker.FAIL_THRESHOLD - 1) { h.failTor() }
        assertEquals(0, h.deadCount)
    }

    @Test
    fun `a single Tor success disarms the window`() {
        val h = Harness()
        repeat(TorCircuitHealthTracker.FAIL_THRESHOLD - 1) { h.failTor() }
        h.succeedTor() // resets the failure window AND the no-success clock
        h.failTor()
        assertEquals("one success means circuits work — suppress", 0, h.deadCount)
    }

    @Test
    fun `clearnet relay outcomes are ignored`() {
        val h = Harness()
        repeat(TorCircuitHealthTracker.FAIL_THRESHOLD * 2) { h.failClear() }
        assertEquals(0, h.deadCount)
    }

    @Test
    fun `does not fire when connectivity is down`() {
        val h = Harness(connectivityActive = false)
        repeat(TorCircuitHealthTracker.FAIL_THRESHOLD) { h.failTor() }
        assertEquals("general outage must not reset Tor", 0, h.deadCount)
    }

    @Test
    fun `does not fire when Tor is not Active`() {
        val h = Harness(torActive = false)
        repeat(TorCircuitHealthTracker.FAIL_THRESHOLD) { h.failTor() }
        assertEquals(0, h.deadCount)
    }

    @Test
    fun `failures older than the window do not accumulate`() {
        val h = Harness()
        // Fill almost to threshold, then let the window slide fully past those failures.
        repeat(TorCircuitHealthTracker.FAIL_THRESHOLD - 1) { h.failTor() }
        h.now += TorCircuitHealthTracker.WINDOW_MS + 1
        // The no-success clause holds (we never succeeded), but the stale failures must have aged
        // out of the window, so a single fresh failure can't reach threshold.
        h.failTor()
        assertEquals(0, h.deadCount)
    }

    @Test
    fun `re-arms after firing — needs a fresh window to fire again`() {
        val h = Harness()
        repeat(TorCircuitHealthTracker.FAIL_THRESHOLD) { h.failTor() }
        assertEquals(1, h.deadCount)

        // Immediately after firing the window is cleared and the no-success clock reset, so the
        // next failure can't re-fire until both the window refills and WINDOW_MS elapses.
        h.failTor()
        assertEquals(1, h.deadCount)

        h.now += TorCircuitHealthTracker.WINDOW_MS + 1
        repeat(TorCircuitHealthTracker.FAIL_THRESHOLD) { h.failTor() }
        assertEquals(2, h.deadCount)
    }

    // ------------------------------------------------------------------
    // harness
    // ------------------------------------------------------------------

    private inner class Harness(
        torActive: Boolean = true,
        connectivityActive: Boolean = true,
    ) {
        var now: Long = 1_000_000_000_000L
        var deadCount: Int = 0
            private set

        private val client = CapturingClient()

        init {
            TorCircuitHealthTracker(
                client = client,
                isTorRouted = { it == torRelay },
                isTorActive = { torActive },
                isConnectivityActive = { connectivityActive },
                onCircuitsDead = { deadCount++ },
                nowMs = { now },
            ).register()
        }

        fun failTor() = client.listener!!.onCannotConnect(relayClient(torRelay), "SOCKS: Connection refused")

        fun failClear() = client.listener!!.onCannotConnect(relayClient(clearRelay), "boom")

        fun succeedTor() = client.listener!!.onConnected(relayClient(torRelay), 400, false)
    }

    private fun relayClient(relayUrl: NormalizedRelayUrl): IRelayClient =
        object : IRelayClient {
            override val url = relayUrl

            override fun connect() = error("unused")

            override fun needsToReconnect() = error("unused")

            override fun connectAndSyncFiltersIfDisconnected(ignoreRetryDelays: Boolean) = error("unused")

            override fun isConnected() = error("unused")

            override fun sendOrConnectAndSync(cmd: Command) = error("unused")

            override fun sendIfConnected(cmd: Command) = error("unused")

            override fun disconnect() = error("unused")
        }

    /** Minimal [INostrClient] (delegating to [EmptyNostrClient]) that just captures the listener. */
    private class CapturingClient(
        private val delegate: INostrClient = EmptyNostrClient(),
    ) : INostrClient by delegate {
        var listener: RelayConnectionListener? = null

        override fun addConnectionListener(listener: RelayConnectionListener) {
            this.listener = listener
        }

        override fun removeConnectionListener(listener: RelayConnectionListener) {
            if (this.listener === listener) this.listener = null
        }
    }
}
