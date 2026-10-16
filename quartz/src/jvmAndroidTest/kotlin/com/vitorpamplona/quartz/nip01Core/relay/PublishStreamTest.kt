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
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.PublishResult
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishStream
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [publishStream] answers every event exactly once, keeps no more than its window
 * unanswered, sends each event over the socket once, and gives up on a relay that
 * stops answering instead of waiting forever.
 */
class PublishStreamTest {
    private val hub = InProcessRelays()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val signer = NostrSignerInternal(KeyPair())

    @AfterTest
    fun tearDown() {
        scope.cancel()
        hub.close()
    }

    /** The hub's relay, counting EVENT frames out and OKs back; [swallow] ids are sent nowhere. */
    private inner class CountingRelay(
        val swallow: Set<String> = emptySet(),
    ) : WebsocketBuilder {
        val eventFrames = AtomicInteger()
        val outstanding = AtomicInteger()
        val maxOutstanding = AtomicInteger()

        override fun build(
            url: NormalizedRelayUrl,
            out: WebSocketListener,
        ): WebSocket {
            val counting =
                object : WebSocketListener by out {
                    override suspend fun onMessage(text: String) {
                        if (text.startsWith("[\"OK\"")) outstanding.decrementAndGet()
                        out.onMessage(text)
                    }
                }
            val inner = hub.build(url, counting)
            return object : WebSocket by inner {
                override fun send(msg: String): Boolean {
                    if (!msg.startsWith("[\"EVENT\"")) return inner.send(msg)
                    if (swallow.any { msg.contains(it) }) return true
                    eventFrames.incrementAndGet()
                    maxOutstanding.accumulateAndGet(outstanding.incrementAndGet(), ::maxOf)
                    return inner.send(msg)
                }
            }
        }
    }

    private suspend fun notes(n: Int): List<Event> = (0 until n).map { signer.sign(TextNoteEvent.build("note $it")) }

    @Test
    fun everyEventIsAnsweredOnceWithinTheWindow() =
        runBlocking {
            val relay = CountingRelay()
            val client = NostrClient(relay, scope)
            val events = notes(400)
            val heard = ConcurrentHashMap<String, MutableList<PublishResult>>()

            val result = client.publishStream(InProcessRelays.DEFAULT_URL, events.asFlow(), maxInFlight = 16) { e, ok -> heard.computeIfAbsent(e.id) { mutableListOf() }.add(ok) }
            client.disconnect()

            assertEquals(400, result.sent)
            assertEquals(400, result.answered)
            assertFalse(result.stalled)
            assertEquals(events.map { it.id }.toSet(), heard.keys)
            heard.values.forEach { assertTrue(it.single().accepted, "rejected: ${it.single().message}") }
            assertEquals(400, relay.eventFrames.get(), "each event crosses the socket once")
            assertTrue(relay.maxOutstanding.get() <= 16, "the window held ${relay.maxOutstanding.get()} unanswered")
        }

    @Test
    fun theWindowIsBoundedInBytesToo() =
        runBlocking {
            val relay = CountingRelay()
            val client = NostrClient(relay, scope)
            // ~100 KB each: a window of 512 of these is 50 MB, far past OkHttp's 16 MiB send queue.
            val big = (0 until 30).map { signer.sign(TextNoteEvent.build("x".repeat(100_000) + it)) }
            val result = client.publishStream(InProcessRelays.DEFAULT_URL, big.asFlow(), maxInFlight = 512, maxInFlightBytes = 350_000) { _, _ -> }
            client.disconnect()
            assertEquals(30, result.answered)
            assertTrue(relay.maxOutstanding.get() <= 3, "a 350 KB budget held ${relay.maxOutstanding.get()} 100 KB events unanswered")
        }

    @Test
    fun copiesOfOneEventShareItsVerdict() =
        runBlocking {
            val client = NostrClient(CountingRelay(), scope)
            val one = notes(1).single()
            val heard = AtomicInteger()
            val result = client.publishStream(InProcessRelays.DEFAULT_URL, listOf(one, one, one).asFlow()) { _, ok -> if (ok.accepted) heard.incrementAndGet() }
            client.disconnect()
            assertEquals(3, result.sent)
            assertEquals(3, heard.get())
        }

    @Test
    fun slowInputStillSendsTheFirstEventAlone() =
        runBlocking {
            val relay = CountingRelay()
            val client = NostrClient(relay, scope)
            val events = notes(100)
            // Silent past the idle window before the first event: the input is slow, not the
            // relay, and the socket only starts connecting once the first event is published.
            val slow =
                flow {
                    delay(1_200)
                    events.forEach { emit(it) }
                }
            val result = client.publishStream(InProcessRelays.DEFAULT_URL, slow, idleTimeoutMs = 400) { _, _ -> }
            client.disconnect()

            assertFalse(result.stalled)
            assertEquals(100, result.answered)
            assertEquals(100, relay.eventFrames.get(), "a window sent into a connecting socket goes out twice")
        }

    @Test
    fun aRelayThatStopsAnsweringEndsTheStream() =
        runBlocking {
            val events = notes(50)
            val lost = events[10].id
            val client = NostrClient(CountingRelay(swallow = setOf(lost)), scope)
            val unanswered = ConcurrentHashMap.newKeySet<String>()

            val result =
                client.publishStream(InProcessRelays.DEFAULT_URL, events.asFlow(), maxInFlight = 8, idleTimeoutMs = 1_500) { e, ok ->
                    if (ok.isTransportFailure) unanswered.add(e.id)
                }
            client.disconnect()

            assertTrue(result.stalled)
            assertEquals(setOf(lost), unanswered)
            assertEquals(result.sent, result.answered + result.unanswered)
            assertEquals(50, result.sent, "one stuck event does not close the window on the rest")
        }
}
