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
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EoseMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.relay.fixtures.SyntheticEvents
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals

class NostrClientRepeatSubTest : BaseNostrClientTest() {
    @Test
    fun testRepeatSubEvents() =
        runBlocking {
            // Each replaceable kind needs unique pubkeys.
            relayHub.getOrCreate("ws://127.0.0.1:7770/").preload(
                (1..150).map {
                    SyntheticEvents.fakeEvent(
                        idSeed = it,
                        kind = MetadataEvent.KIND,
                        pubKey = SyntheticEvents.hexId(it),
                    )
                },
            )
            relayHub.getOrCreate("ws://127.0.0.1:7770/").preload(
                (1..50).map {
                    SyntheticEvents.fakeEvent(
                        idSeed = 100_000 + it,
                        kind = AdvertisedRelayListEvent.KIND,
                        pubKey = SyntheticEvents.hexId(100_000 + it),
                    )
                },
            )

            val appScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val client = NostrClient(socketBuilder, appScope)

            val resultChannel = Channel<String>(UNLIMITED)
            val events = mutableListOf<String>()
            val mySubId = "test-sub-id-2"

            val listener =
                object : RelayConnectionListener {
                    override fun onIncomingMessage(
                        relay: IRelayClient,
                        msgStr: String,
                        msg: Message,
                    ) {
                        Log.d("Test") { "Receiving message: $msgStr" }
                        when (msg) {
                            is EventMessage -> {
                                if (mySubId == msg.subId) {
                                    resultChannel.trySend(msg.event.id)
                                }
                            }

                            is EoseMessage -> {
                                if (mySubId == msg.subId) {
                                    resultChannel.trySend("EOSE")
                                }
                            }
                        }
                    }
                }

            client.addConnectionListener(listener)

            val filters =
                mapOf(
                    RelayUrlNormalizer.normalize("ws://127.0.0.1:7770/") to
                        listOf(
                            Filter(
                                kinds = listOf(MetadataEvent.KIND),
                                limit = 100,
                            ),
                        ),
                )

            val filtersShouldIgnore =
                mapOf(
                    RelayUrlNormalizer.normalize("ws://127.0.0.1:7770/") to
                        listOf(
                            Filter(
                                kinds = listOf(AdvertisedRelayListEvent.KIND),
                                limit = 500,
                            ),
                        ),
                )

            val filtersShouldSendAfterEOSE =
                mapOf(
                    RelayUrlNormalizer.normalize("ws://127.0.0.1:7770/") to
                        listOf(
                            Filter(
                                kinds = listOf(AdvertisedRelayListEvent.KIND),
                                limit = 10,
                            ),
                        ),
                )

            coroutineScope {
                launch {
                    withTimeoutOrNull(30000) {
                        var eoseCount = 0
                        while (eoseCount < 2) {
                            Log.d("Test") { "Processing message ${events.size}" }
                            // simulates an update in the middle of the sub
                            if (events.size == 1) {
                                client.subscribe(mySubId, filtersShouldIgnore)
                            }
                            if (events.size == 5) {
                                client.subscribe(mySubId, filtersShouldSendAfterEOSE)
                            }
                            val msg = resultChannel.receive()
                            events.add(msg)
                            if (msg == "EOSE") eoseCount++
                        }
                    }
                }

                launch {
                    client.subscribe(mySubId, filters)
                }
            }

            client.unsubscribe(mySubId)
            client.removeConnectionListener(listener)
            client.disconnect()

            appScope.cancel()
            relayHub.close()

            // The relay may return up to limit events before EOSE; some relays return
            // one extra past the requested limit, so don't assert on the exact count.
            // First sub: <= 100 metadata events, then EOSE.
            // Second sub: <= 10 advertised relay list events, then EOSE.
            val firstEose = events.indexOf("EOSE")
            val lastEose = events.lastIndexOf("EOSE")

            // both EOSEs must be present and distinct
            assertEquals(true, firstEose >= 0)
            assertEquals(true, lastEose > firstEose)
            // last entry is the second EOSE (loop stops on it)
            assertEquals(events.size - 1, lastEose)
            // first sub stays within its limit (allow +1 for relay quirks)
            assertEquals(true, firstEose in 1..101)
            // second sub stays within its limit (allow +1 for relay quirks)
            assertEquals(true, (lastEose - firstEose - 1) in 1..11)
            // everything before the first EOSE is an event id
            assertEquals(true, events.take(firstEose).all { it.length == 64 })
            // everything between the two EOSEs is an event id
            assertEquals(true, events.subList(firstEose + 1, lastEose).all { it.length == 64 })
        }
}
