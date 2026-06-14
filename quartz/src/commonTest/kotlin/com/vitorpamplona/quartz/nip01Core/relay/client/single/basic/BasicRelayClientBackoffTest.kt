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
}
