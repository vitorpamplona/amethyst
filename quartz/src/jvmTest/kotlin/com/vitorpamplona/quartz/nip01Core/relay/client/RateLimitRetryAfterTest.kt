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

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndCollectResults
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.client.single.basic.BasicRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EoseMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CountCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.server.NostrServer
import com.vitorpamplona.quartz.nip01Core.relay.server.inprocess.InProcessWebSocket
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.PassThroughPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.PolicyResult
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/**
 * A relay that refuses the first [refuseFirst] REQs and EVENTs as `rate-limited:` — with a
 * NIP-01 back-off of [backOffMs] when it is not null — wired to a real [NostrClient] over the
 * in-process socket. Pins that the pool honors the back-off (asks again once it passes, not
 * before) and that a refusal without one is not read as "retry now".
 */
class RateLimitRetryAfterTest {
    private class Relay(
        val backOffMs: Long?,
        val refuseFirst: Int = 1,
        /** COUNTs refused before one is answered; REQs and EVENTs then go by [refuseFirst]. */
        val refuseCounts: Int = 0,
    ) : AutoCloseable {
        val reqs = AtomicInteger(0)
        val events = AtomicInteger(0)
        val counts = AtomicInteger(0)

        val server =
            NostrServer(
                store = EventStore(null),
                policyBuilder = {
                    object : PassThroughPolicy() {
                        override fun accept(cmd: ReqCmd): PolicyResult<ReqCmd> =
                            if (reqs.incrementAndGet() <= refuseFirst) {
                                PolicyResult.Rejected.rateLimited("slow down there chief", backOffMs)
                            } else {
                                PolicyResult.Accepted(cmd)
                            }

                        override fun accept(cmd: CountCmd): PolicyResult<CountCmd> =
                            if (counts.incrementAndGet() <= refuseCounts) {
                                PolicyResult.Rejected.rateLimited("slow down there chief", backOffMs)
                            } else {
                                PolicyResult.Accepted(cmd)
                            }

                        override fun accept(cmd: EventCmd): PolicyResult<EventCmd> =
                            if (events.incrementAndGet() <= refuseFirst) {
                                PolicyResult.Rejected.rateLimited("slow down there chief", backOffMs)
                            } else {
                                PolicyResult.Accepted(cmd)
                            }
                    }
                },
            )

        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        val sockets =
            object : WebsocketBuilder {
                override fun build(
                    url: NormalizedRelayUrl,
                    out: WebSocketListener,
                ): WebSocket = InProcessWebSocket(server, out)
            }

        val client = NostrClient(sockets, scope)

        override fun close() {
            client.close()
            server.close()
            scope.cancel()
        }
    }

    private val url = RelayUrlNormalizer.normalizeOrNull("wss://ratelimited.example.com/")!!

    private class Listener : SubscriptionListener {
        val closedParams = CompletableDeferred<String?>()
        val eose = CompletableDeferred<Unit>()

        override fun onClosed(
            message: String,
            relay: NormalizedRelayUrl,
            forFilters: List<Filter>?,
            params: String?,
        ) {
            closedParams.complete(params)
        }

        override fun onEose(
            relay: NormalizedRelayUrl,
            forFilters: List<Filter>?,
        ) {
            eose.complete(Unit)
        }
    }

    private suspend fun note(): Event = NostrSignerInternal(KeyPair()).sign(Event.build(kind = 1, content = "hi") {})

    @Test
    fun aClosedReqIsAskedAgainOnceTheBackOffPasses() =
        runBlocking {
            Relay(backOffMs = 400).use { relay ->
                val listener = Listener()
                val start = TimeSource.Monotonic.markNow()
                relay.client.subscribe("sub", mapOf(url to listOf(Filter(kinds = listOf(1)))), listener)

                assertEquals("400", withTimeout(5_000) { listener.closedParams.await() })
                withTimeout(5_000) { listener.eose.await() }

                assertTrue(start.elapsedNow().inWholeMilliseconds >= 400, "re-sent before the back-off passed")
                assertEquals(2, relay.reqs.get())
            }
        }

    @Test
    fun aNewReqMidBackOffWaitsWithTheRefusedOne() =
        runBlocking {
            Relay(backOffMs = 600).use { relay ->
                val first = Listener()
                val second = Listener()
                relay.client.subscribe("sub", mapOf(url to listOf(Filter(kinds = listOf(1)))), first)
                withTimeout(5_000) { first.closedParams.await() }

                relay.client.subscribe("sub2", mapOf(url to listOf(Filter(kinds = listOf(7)))), second)
                delay(200)
                assertEquals(1, relay.reqs.get(), "a REQ reached the relay inside its back-off")

                withTimeout(5_000) { first.eose.await() }
                withTimeout(5_000) { second.eose.await() }
                assertEquals(3, relay.reqs.get())
            }
        }

    @Test
    fun aNewPublishMidBackOffWaitsWithTheRefusedOne() =
        runBlocking {
            Relay(backOffMs = 600).use { relay ->
                val refused = note()
                val first = async { relay.client.publishAndCollectResults(refused, setOf(url), timeoutInSeconds = 5).getValue(url) }
                while (relay.events.get() < 1) delay(10)
                delay(100)

                val second = async { relay.client.publishAndCollectResults(note(), setOf(url), timeoutInSeconds = 5).getValue(url) }
                delay(200)
                assertEquals(1, relay.events.get(), "an EVENT reached the relay inside its back-off")

                assertTrue(first.await().accepted)
                assertTrue(second.await().accepted)
                assertEquals(3, relay.events.get())
            }
        }

    @Test
    fun anySendOnTheConnectionWaitsOutItsBackOff() =
        runBlocking {
            // The hold lives on the relay connection, so a REQ sent straight to it, past the
            // pool (as a sub's update after its EOSE or a NOTICE-narrowed re-send is), still waits.
            Relay(backOffMs = 600).use { relay ->
                val eose = CompletableDeferred<Unit>()
                val connected = CompletableDeferred<Unit>()
                val connection =
                    BasicRelayClient(
                        url,
                        relay.sockets,
                        object : RelayConnectionListener {
                            override fun onConnected(
                                relay: IRelayClient,
                                pingMillis: Int,
                                compressed: Boolean,
                            ) {
                                connected.complete(Unit)
                            }

                            override suspend fun onIncomingMessage(
                                relay: IRelayClient,
                                msgStr: String,
                                msg: Message,
                            ) {
                                if (msg is EoseMessage && msg.subId == "direct") eose.complete(Unit)
                            }
                        },
                        scope = relay.scope,
                    )
                connection.connect()
                withTimeout(5_000) { connected.await() }

                val start = TimeSource.Monotonic.markNow()
                connection.sendIfConnected(ReqCmd("refused", listOf(Filter(kinds = listOf(1)))))
                withTimeout(5_000) { while (relay.reqs.get() < 1) delay(10) }
                delay(100)
                connection.sendIfConnected(ReqCmd("direct", listOf(Filter(kinds = listOf(7)))))
                delay(200)
                assertEquals(1, relay.reqs.get(), "a REQ reached the relay inside its back-off")

                withTimeout(5_000) { eose.await() }
                assertTrue(start.elapsedNow().inWholeMilliseconds >= 600, "sent before the back-off passed")
                connection.disconnect()
            }
        }

    @Test
    fun aCountsBackOffDoesNotHoldBackReqs() =
        runBlocking {
            Relay(backOffMs = 5_000, refuseFirst = 0, refuseCounts = 1).use { relay ->
                relay.client.count("c", mapOf(url to listOf(Filter(kinds = listOf(1)))))
                withTimeout(5_000) { while (relay.counts.get() < 1) delay(10) }
                delay(100)

                val listener = Listener()
                relay.client.subscribe("sub", mapOf(url to listOf(Filter(kinds = listOf(1)))), listener)
                withTimeout(1_000) { listener.eose.await() }
            }
        }

    @Test
    fun aClosedReqWithoutABackOffIsNotRetriedAtOnce() =
        runBlocking {
            Relay(backOffMs = null).use { relay ->
                val listener = Listener()
                relay.client.subscribe("sub", mapOf(url to listOf(Filter(kinds = listOf(1)))), listener)

                assertNull(withTimeout(5_000) { listener.closedParams.await() })
                assertNull(withTimeoutOrNull(1_000) { listener.eose.await() })
                assertEquals(1, relay.reqs.get())
            }
        }

    @Test
    fun aPublishWaitsOutTheBackOffAndReportsTheRetry() =
        runBlocking {
            Relay(backOffMs = 300).use { relay ->
                val event = note()
                val start = TimeSource.Monotonic.markNow()
                val result = relay.client.publishAndCollectResults(event, setOf(url), timeoutInSeconds = 5).getValue(url)

                assertTrue(result.accepted, "the retry after the back-off was not reported: ${result.message}")
                assertTrue(start.elapsedNow().inWholeMilliseconds >= 300, "re-sent before the back-off passed")
                assertEquals(2, relay.events.get())
            }
        }

    @Test
    fun aBackOffPastTheTimeoutIsReportedAsTheRefusal() =
        runBlocking {
            Relay(backOffMs = 60_000).use { relay ->
                val result = relay.client.publishAndCollectResults(note(), setOf(url), timeoutInSeconds = 2).getValue(url)

                assertEquals(false, result.accepted)
                assertEquals("rate-limited: slow down there chief", result.message)
                assertEquals(60_000L, result.retryAfterMs)
                // Not re-sent: a minute is still ahead.
                delay(200)
                assertEquals(1, relay.events.get())
            }
        }
}
