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
package com.vitorpamplona.quartz.nip01Core.relay.client

import com.vitorpamplona.quartz.nip01Core.relay.client.single.basic.FakeWebsocketBuilder
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Behavioral contract of the keep-alive sweep after it was changed to suspend
 * on the client's active-state flow instead of ticking unconditionally:
 *
 *  1. while ACTIVE, a relay closed by the server is redialed within one 60s
 *     sweep interval (once the per-relay backoff gate opens);
 *  2. while INACTIVE (host called [NostrClient.disconnect], i.e. the app went
 *     to the background), no amount of elapsed time produces a dial;
 *  3. after [NostrClient.connect] reactivates the client, redialing works
 *     again — a bug in the suspend/resume logic would leave every
 *     server-dropped relay disconnected forever.
 *
 * Coroutine timers (keep-alive 60s, reconnect debounce 200ms) run on the test
 * scheduler's virtual clock; the per-relay backoff gate compares real wall
 * seconds (integer granularity), hence the short real sleeps before each
 * expected redial.
 *
 * Harness note: every socket the client dials must eventually be driven to
 * onOpen/onClosed — a [FakeWebsocketBuilder] socket that is never completed
 * leaves the relay in "connecting" forever and blocks all later dials, which
 * is exactly what a production dial never does. [settleDisconnected] flushes
 * pending debounced reconnects and completes any socket they may open.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NostrClientKeepAliveTest {
    private val url = NormalizedRelayUrl("wss://keepalive.example.com")

    /**
     * Flushes pending sub-second timers (the 200ms reconnect debounce) and,
     * if a flush dialed a new socket, completes its lifecycle so the relay
     * ends DISCONNECTED with no pending timers besides the keep-alive sweep.
     */
    private fun TestScope.settleDisconnected(builder: FakeWebsocketBuilder) {
        repeat(4) {
            val before = builder.connectAttempts
            advanceTimeBy(500)
            runCurrent()
            if (builder.connectAttempts == before) return
            builder.lastListener.onOpen(50, false)
            builder.lastListener.onClosed(1000, "test settle")
        }
    }

    @Test
    fun keepAliveSweepPausesWhileInactiveAndResumesAfterReconnect() =
        runTest {
            val builder = FakeWebsocketBuilder()
            val client = NostrClient(builder, this)
            try {
                // Flush refreshConnection's initial emission against the
                // still-empty pool so it can't dial later.
                advanceTimeBy(500)
                runCurrent()

                // Subscribing dials the relay immediately.
                client.subscribe("sub", mapOf(url to listOf(Filter())))
                assertEquals(1, builder.connectAttempts)

                // Server closes the established connection. While ACTIVE the
                // client must redial within one keep-alive interval.
                builder.lastListener.onOpen(50, false)
                builder.lastListener.onClosed(1000, "server closed")
                settleDisconnected(builder)
                val beforeActiveSweep = builder.connectAttempts
                Thread.sleep(3_500)
                advanceTimeBy(61_000)
                runCurrent()
                assertTrue(
                    builder.connectAttempts > beforeActiveSweep,
                    "active client should redial a server-closed relay within one sweep, " +
                        "got ${builder.connectAttempts} attempts (baseline $beforeActiveSweep)",
                )

                // Leave the relay cleanly disconnected, then deactivate.
                builder.lastListener.onOpen(50, false)
                builder.lastListener.onClosed(1000, "server closed")
                settleDisconnected(builder)
                client.disconnect()
                val whileInactiveBaseline = builder.connectAttempts

                // The relay's backoff gate is reset by disconnect(), so any
                // stray sweep tick WOULD dial — this fails if the sweep keeps
                // running (or anything else dials) while inactive.
                Thread.sleep(3_500)
                advanceTimeBy(30 * 60_000)
                runCurrent()
                assertEquals(
                    whileInactiveBaseline,
                    builder.connectAttempts,
                    "no dial may happen while the client is inactive",
                )

                // Reactivation dials the pool immediately...
                client.connect()
                runCurrent()
                assertEquals(
                    whileInactiveBaseline + 1,
                    builder.connectAttempts,
                    "connect() should redial the pool immediately",
                )

                // ...and after the pause a server-closed relay must again be
                // redialed within one sweep — fails if the sweep never
                // resumes after the inactive stretch.
                builder.lastListener.onOpen(50, false)
                builder.lastListener.onClosed(1000, "server closed")
                settleDisconnected(builder)
                val beforeResumedSweep = builder.connectAttempts
                Thread.sleep(3_500)
                advanceTimeBy(61_000)
                runCurrent()
                assertTrue(
                    builder.connectAttempts > beforeResumedSweep,
                    "sweep did not resume after connect(); a server-dropped relay would stay disconnected forever",
                )
            } finally {
                client.close()
            }
        }
}
