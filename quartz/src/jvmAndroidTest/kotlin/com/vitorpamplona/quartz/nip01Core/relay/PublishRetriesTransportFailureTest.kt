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
package com.vitorpamplona.quartz.nip01Core.relay

import com.vitorpamplona.geode.InProcessRelays
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.PublishResult
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndCollectResults
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A relay that hangs up between our EVENT frame and its OK told us nothing:
 * the event may be stored, or it may not. We reported it as a failed publish
 * and never tried again, which is how the Marmot interop harness kept losing
 * a message a run to `disconnected before OK` on a loopback relay that was
 * perfectly healthy a second later.
 *
 * A Nostr event is idempotent under its own id, so re-sending it after a
 * transport failure costs a duplicate the relay collapses and buys the OK we
 * were owed. The retry stays inside the caller's existing timeout, so nothing
 * waits longer than it used to.
 */
class PublishRetriesTransportFailureTest {
    private val hub = InProcessRelays()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @AfterTest
    fun tearDown() {
        scope.cancel()
        hub.close()
    }

    /**
     * Wraps the in-process hub and hangs up the first [dropFirstConnections]
     * sockets the moment they carry an `EVENT` frame — the relay took our
     * bytes and vanished before answering, which is the case that has no
     * verdict in it.
     */
    private class HangsUpOnFirstEvent(
        private val delegate: WebsocketBuilder,
        private val dropFirstConnections: Int,
        /**
         * How long the relay takes to come back after it hung up. A real one
         * does not reappear instantly, and the reconnect is the part the retry
         * has to have time for.
         */
        private val reconnectDelayMs: Long = 0,
    ) : WebsocketBuilder {
        val eventFramesSeen = AtomicInteger(0)
        private val socketsBuilt = AtomicInteger(0)

        override fun build(
            url: NormalizedRelayUrl,
            out: WebSocketListener,
        ): WebSocket {
            val index = socketsBuilt.getAndIncrement()
            if (index >= dropFirstConnections && reconnectDelayMs > 0) Thread.sleep(reconnectDelayMs)
            val inner = delegate.build(url, out)
            val hangUp = index < dropFirstConnections
            return object : WebSocket by inner {
                override fun send(msg: String): Boolean {
                    if (!msg.startsWith("[\"EVENT\"")) return inner.send(msg)
                    eventFramesSeen.incrementAndGet()
                    if (!hangUp) return inner.send(msg)
                    // Take the bytes, answer nothing, drop the socket.
                    inner.disconnect()
                    out.onClosed(1006, "abnormal closure")
                    return true
                }
            }
        }
    }

    @Test
    fun aDisconnectBeforeTheOkIsRetriedAndSucceeds() =
        runBlocking {
            val builder = HangsUpOnFirstEvent(hub, dropFirstConnections = 1)
            val client = NostrClient(builder, scope)
            val event = NostrSignerInternal(KeyPair()).sign(TextNoteEvent.build("survives a hang-up"))

            val results =
                client.publishAndCollectResults(
                    event = event,
                    relayList = setOf(InProcessRelays.DEFAULT_URL),
                    timeoutInSeconds = 20,
                )

            assertEquals(1, results.size)
            val result = results.values.single()
            assertTrue(
                result.accepted,
                "the retry must land the event: the relay was healthy, it just hung up before the OK " +
                    "(got \"${result.message}\")",
            )
            assertTrue(
                builder.eventFramesSeen.get() >= 2,
                "the event has to actually go out a second time, not just be re-reported",
            )
            client.disconnect()
        }

    @Test
    fun aRetryOutlivesTheCallersOriginalDeadline() =
        runBlocking {
            // The bug this pins: the retry was issued and then timed out before
            // the relay could answer, so the publish did the work and threw the
            // answer away. The hang-up costs the whole one-second budget here —
            // only the grace granted when the retry is issued can land it.
            val builder =
                HangsUpOnFirstEvent(
                    hub,
                    dropFirstConnections = 1,
                    reconnectDelayMs = 1_500,
                )
            val client = NostrClient(builder, scope)
            val event = NostrSignerInternal(KeyPair()).sign(TextNoteEvent.build("slow to come back"))

            val results =
                client.publishAndCollectResults(
                    event = event,
                    relayList = setOf(InProcessRelays.DEFAULT_URL),
                    timeoutInSeconds = 1,
                )

            val result = results.values.single()
            assertTrue(
                result.accepted,
                "a retry issued at the edge of the budget must be given time to answer " +
                    "(got \"${result.message}\")",
            )
            client.disconnect()
        }

    @Test
    fun aRelayThatKeepsHangingUpStillReportsTheTransportFailure() =
        runBlocking {
            // Every socket dies the same way, so no retry can help. The result
            // must still name the transport failure rather than claim success
            // or hide the relay.
            val builder = HangsUpOnFirstEvent(hub, dropFirstConnections = Int.MAX_VALUE)
            val client = NostrClient(builder, scope)
            val event = NostrSignerInternal(KeyPair()).sign(TextNoteEvent.build("never lands"))

            val results =
                client.publishAndCollectResults(
                    event = event,
                    relayList = setOf(InProcessRelays.DEFAULT_URL),
                    timeoutInSeconds = 8,
                )

            val result = results.getValue(InProcessRelays.DEFAULT_URL)
            assertFalse(result.accepted)
            assertTrue(
                result.isTransportFailure,
                "a hang-up is never a verdict from the relay (got \"${result.message}\")",
            )
            client.disconnect()
        }

    @Test
    fun aHealthyPublishStillTakesOneRoundTrip() =
        runBlocking {
            val builder = HangsUpOnFirstEvent(hub, dropFirstConnections = 0)
            val client = NostrClient(builder, scope)
            val event = NostrSignerInternal(KeyPair()).sign(TextNoteEvent.build("no retry needed"))

            val results =
                client.publishAndCollectResults(
                    event = event,
                    relayList = setOf(InProcessRelays.DEFAULT_URL),
                    timeoutInSeconds = 20,
                )

            assertTrue(results.values.single().accepted)
            assertEquals(
                1,
                builder.eventFramesSeen.get(),
                "an OK on the first try must not be followed by a speculative resend",
            )
            client.disconnect()
        }

    @Test
    fun retriesCanBeTurnedOff() =
        runBlocking {
            val builder = HangsUpOnFirstEvent(hub, dropFirstConnections = 1)
            val client = NostrClient(builder, scope)
            val event = NostrSignerInternal(KeyPair()).sign(TextNoteEvent.build("one shot only"))

            val results =
                client.publishAndCollectResults(
                    event = event,
                    relayList = setOf(InProcessRelays.DEFAULT_URL),
                    timeoutInSeconds = 8,
                    transportRetries = 0,
                )

            assertEquals(PublishResult.DISCONNECTED, results.values.single().message)
            assertEquals(1, builder.eventFramesSeen.get())
            client.disconnect()
        }
}
