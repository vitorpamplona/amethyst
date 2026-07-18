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
package com.vitorpamplona.quartz.nip01Core.relay.client.single.basic

import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.EmptyConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Reproduces the reconnect storm from the 2026-06-12 benchmark sweep: a relay
 * that completes the WebSocket handshake and then immediately resets the
 * connection must still be subject to exponential backoff. Before the fix,
 * onOpen reset the backoff delay to 1s on every cycle, producing a reconnect
 * attempt every ~3s indefinitely (733 attempts in 40 min for one relay).
 */
class BasicRelayClientBackoffTest {
    private val url = NormalizedRelayUrl("wss://flaky.example.com")

    class MutableClock(
        var now: Long = 1_000_000L,
    )

    /**
     * Simulates [totalSeconds] of wall-clock time with a pool tick every
     * [tickSeconds] (subscription churn calls connectAndSyncFiltersIfDisconnected
     * roughly this often during active use). [onConnectAttempt] is invoked after
     * every new socket build so the test can drive the relay's behavior.
     */
    private fun runTicks(
        client: BasicRelayClient,
        builder: FakeWebsocketBuilder,
        clock: MutableClock,
        totalSeconds: Long,
        tickSeconds: Long = 3,
        onConnectAttempt: (WebSocketListener) -> Unit,
    ) {
        val end = clock.now + totalSeconds
        while (clock.now < end) {
            clock.now += tickSeconds
            val before = builder.connectAttempts
            client.connectAndSyncFiltersIfDisconnected()
            if (builder.connectAttempts > before) {
                onConnectAttempt(builder.lastListener)
            }
        }
    }

    /**
     * Ticks until the relay dials again, then stops immediately — leaving the clock
     * parked right after a failed attempt. Anchors tests that need to reason about the
     * remaining backoff window instead of landing at an arbitrary point in the cycle.
     * Returns false if no attempt happened within [limitSeconds].
     */
    private fun tickUntilNextAttempt(
        client: BasicRelayClient,
        builder: FakeWebsocketBuilder,
        clock: MutableClock,
        limitSeconds: Long,
        tickSeconds: Long = 3,
        onConnectAttempt: (WebSocketListener) -> Unit,
    ): Boolean {
        val end = clock.now + limitSeconds
        while (clock.now < end) {
            clock.now += tickSeconds
            val before = builder.connectAttempts
            client.connectAndSyncFiltersIfDisconnected()
            if (builder.connectAttempts > before) {
                onConnectAttempt(builder.lastListener)
                return true
            }
        }
        return false
    }

    private fun newClient(
        builder: FakeWebsocketBuilder,
        clock: MutableClock,
    ) = BasicRelayClient(
        url = url,
        socketBuilder = builder,
        listener = EmptyConnectionListener,
        nowInSeconds = { clock.now },
    )

    @Test
    fun handshakeThenResetRelayStillBacksOffExponentially() {
        val builder = FakeWebsocketBuilder()
        val clock = MutableClock()
        val client = newClient(builder, clock)

        // first connection: handshake completes, then the server resets.
        client.connect()
        builder.lastListener.onOpen(50, false)
        builder.lastListener.onFailure(RuntimeException("Connection reset"), null, null)

        // 40 minutes of pool ticks every 3s; the relay flaps on every attempt.
        runTicks(client, builder, clock, totalSeconds = 40 * 60) { listener ->
            listener.onOpen(50, false)
            listener.onFailure(RuntimeException(), null, null)
        }

        // exponential backoff (1,2,4,...,300s cap) allows at most ~17 attempts
        // in 40 min. The bug produced one attempt per ~2 ticks (hundreds).
        assertTrue(
            builder.connectAttempts <= 20,
            "Expected exponential backoff to cap reconnects, got ${builder.connectAttempts} attempts in 40 min",
        )
    }

    @Test
    fun preHandshakeFailuresBackOffExponentially() {
        val builder = FakeWebsocketBuilder()
        val clock = MutableClock()
        val client = newClient(builder, clock)

        client.connect()
        builder.lastListener.onFailure(RuntimeException("SSL handshake failed"), null, null)

        runTicks(client, builder, clock, totalSeconds = 40 * 60) { listener ->
            listener.onFailure(RuntimeException("SSL handshake failed"), null, null)
        }

        assertTrue(
            builder.connectAttempts <= 20,
            "Expected exponential backoff for pre-handshake failures, got ${builder.connectAttempts} attempts",
        )
    }

    @Test
    fun stableConnectionResetsBackoffAfterDisconnect() {
        val builder = FakeWebsocketBuilder()
        val clock = MutableClock()
        val client = newClient(builder, clock)

        // grow the backoff with a few flapping cycles.
        client.connect()
        builder.lastListener.onOpen(50, false)
        builder.lastListener.onFailure(RuntimeException(), null, null)
        runTicks(client, builder, clock, totalSeconds = 120) { listener ->
            listener.onOpen(50, false)
            listener.onFailure(RuntimeException(), null, null)
        }

        // now the relay recovers: connection stays up for 10 minutes,
        // then the server closes it cleanly.
        runTicks(client, builder, clock, totalSeconds = 15 * 60) { listener ->
            listener.onOpen(50, false)
        }
        assertTrue(client.isConnected(), "Test setup: relay should have reconnected and stayed up")
        clock.now += 10 * 60
        builder.lastListener.onClosed(1000, "server restart")

        // after a long stable session, the relay should reconnect quickly
        // (within a few ticks), not after the accumulated multi-minute delay.
        val attemptsBefore = builder.connectAttempts
        runTicks(client, builder, clock, totalSeconds = 15) { listener ->
            listener.onOpen(50, false)
        }
        assertTrue(
            builder.connectAttempts > attemptsBefore,
            "Expected backoff to reset after a stable session: no reconnect within 15s of a clean close",
        )
    }

    /**
     * A relay that failed its way up to the 5-minute ceiling on one network must dial
     * immediately once the device moves to another network — the failures were measured
     * against an environment that no longer exists. Without [BasicRelayClient.resetBackoff]
     * the relay stays dark for up to 5 more minutes on a network that may reach it instantly.
     */
    @Test
    fun resetBackoffDialsImmediatelyAfterAMaxedOutBackoff() {
        val builder = FakeWebsocketBuilder()
        val clock = MutableClock()
        val client = newClient(builder, clock)

        // drive the backoff to its ceiling with an unreachable host.
        client.connect()
        builder.lastListener.onFailure(RuntimeException("Unable to resolve host"), null, null)
        runTicks(client, builder, clock, totalSeconds = 60 * 60) { listener ->
            listener.onFailure(RuntimeException("Unable to resolve host"), null, null)
        }

        // Tick until the next attempt actually fires, so the clock sits at a known point
        // in the backoff cycle (right after a failure) rather than wherever the hour ended.
        // Without this anchor the probe window below can straddle a legitimate retry.
        val anchored =
            tickUntilNextAttempt(client, builder, clock, limitSeconds = 20 * 60) { listener ->
                listener.onFailure(RuntimeException("Unable to resolve host"), null, null)
            }
        assertTrue(anchored, "Test setup: expected a retry within 20 min to anchor the cycle")

        // confirm it really is parked: a full minute of ticks buys no attempt.
        val parkedAt = builder.connectAttempts
        runTicks(client, builder, clock, totalSeconds = 60) { listener ->
            listener.onFailure(RuntimeException("Unable to resolve host"), null, null)
        }
        assertEquals(
            parkedAt,
            builder.connectAttempts,
            "Test setup: relay should be sitting on a long backoff",
        )

        // the network changed underneath us.
        client.resetBackoff()

        client.connectAndSyncFiltersIfDisconnected()
        assertEquals(
            parkedAt + 1,
            builder.connectAttempts,
            "Expected resetBackoff to let the relay dial at once on the new network",
        )
    }

    /**
     * A domain that does not resolve (tmp-relay.cesc.trade and friends: registration lapsed,
     * whole zone gone) should not spend ten dials climbing to the ceiling it will certainly
     * reach. It jumps straight there, like an HTTP upgrade rejection.
     */
    @Test
    fun `an unresolvable host goes straight to the long backoff`() {
        val builder = FakeWebsocketBuilder()
        val clock = MutableClock()
        val client = newClient(builder, clock)

        client.connect()
        builder.lastListener.onFailure(UnknownHostException("Unable to resolve host \"gone.example\""), null, null)

        // A generic failure would sit at 2s here and dial ~10 more times over the next
        // 10 minutes. A host that does not resolve gets one dial, at the 5-minute mark.
        runTicks(client, builder, clock, totalSeconds = 4 * 60) { listener ->
            listener.onFailure(UnknownHostException("Unable to resolve host \"gone.example\""), null, null)
        }
        assertEquals(
            1,
            builder.connectAttempts,
            "Expected no retry inside the long backoff window for an unresolvable host",
        )
    }

    /**
     * The safety valve for the eager verdict above: DNS is a property of the network, not of
     * the relay. A captive portal or a filtering resolver forges NXDOMAIN for hosts that are
     * perfectly reachable elsewhere, and Tor resolves at the exit node rather than locally.
     * So the moment the network or the transport changes, the verdict must be discarded.
     */
    @Test
    fun `a network change clears the long backoff of an unresolvable host`() {
        val builder = FakeWebsocketBuilder()
        val clock = MutableClock()
        val client = newClient(builder, clock)

        client.connect()
        builder.lastListener.onFailure(UnknownHostException("Unable to resolve host \"blocked.example\""), null, null)

        runTicks(client, builder, clock, totalSeconds = 60) { listener ->
            listener.onFailure(UnknownHostException(), null, null)
        }
        assertEquals(1, builder.connectAttempts, "Test setup: should be parked on the long backoff")

        // moved to another network / Tor came up: the old resolver's answer means nothing here.
        client.resetBackoff()
        client.connectAndSyncFiltersIfDisconnected()

        assertEquals(
            2,
            builder.connectAttempts,
            "A host that only failed to resolve on the previous network must be retried at once",
        )
    }

    /** resetBackoff is not a disconnect: a healthy session must survive it. */
    @Test
    fun resetBackoffLeavesALiveConnectionAlone() {
        val builder = FakeWebsocketBuilder()
        val clock = MutableClock()
        val client = newClient(builder, clock)

        client.connect()
        builder.lastListener.onOpen(50, false)
        assertTrue(client.isConnected(), "Test setup: relay should be connected")

        val attemptsBefore = builder.connectAttempts
        client.resetBackoff()

        assertTrue(client.isConnected(), "Expected resetBackoff to leave the live socket untouched")
        assertEquals(
            attemptsBefore,
            builder.connectAttempts,
            "Expected resetBackoff not to dial a second socket for an already-connected relay",
        )
    }
}

/**
 * Stands in for `java.net.UnknownHostException`, which commonTest cannot reference. The
 * production code classifies on the exception's simple name (message text is localized and
 * platform-specific; the class name is not), so a same-named class exercises exactly the
 * branch a real DNS failure would take.
 */
private class UnknownHostException(
    message: String? = null,
) : Exception(message)
