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
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.IRelayClientListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EoseMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
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
            val appScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val client = NostrClient(socketBuilder, appScope)

            val resultChannel = Channel<String>(UNLIMITED)
            val events = mutableListOf<String>()
            val mySubId = "test-sub-id-2"

            val listener =
                object : IRelayClientListener {
                    override fun onIncomingMessage(
                        relay: IRelayClient,
                        msgStr: String,
                        msg: Message,
                    ) {
                        Log.d("Test", "Receiving message: $msgStr")
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

            client.subscribe(listener)

            val filters =
                mapOf(
                    RelayUrlNormalizer.normalize("wss://relay.damus.io") to
                        listOf(
                            Filter(
                                kinds = listOf(MetadataEvent.KIND),
                                limit = 100,
                            ),
                        ),
                )

            val filtersShouldIgnore =
                mapOf(
                    RelayUrlNormalizer.normalize("wss://relay.damus.io") to
                        listOf(
                            Filter(
                                kinds = listOf(AdvertisedRelayListEvent.KIND),
                                limit = 500,
                            ),
                        ),
                )

            val filtersShouldSendAfterEOSE =
                mapOf(
                    RelayUrlNormalizer.normalize("wss://relay.damus.io") to
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
                        while (events.size < 112) {
                            Log.d("Test", "Processing message ${events.size}")
                            // simulates an update in the middle of the sub
                            if (events.size == 1) {
                                client.openReqSubscription(mySubId, filtersShouldIgnore)
                            }
                            if (events.size == 5) {
                                client.openReqSubscription(mySubId, filtersShouldSendAfterEOSE)
                            }
                            events.add(resultChannel.receive())
                        }
                    }
                }

                launch {
                    client.openReqSubscription(mySubId, filters)
                }
            }

            client.close(mySubId)
            client.unsubscribe(listener)
            client.disconnect()

            appScope.cancel()

            // gets all 113 messages (100 events + 1 EOSE + 10 events + 1 EOSE)
            assertEquals(112, events.size)
            // checks if first 100 have Ids
            assertEquals(true, events.take(100).all { it.length == 64 })
            // checks if EOSE is after the first 100
            assertEquals("EOSE", events[100])
            // checks if next 10 have Ids
            assertEquals(true, events.drop(101).take(10).all { it.length == 64 })
            // checks if EOSE is after the next 10
            assertEquals("EOSE", events[111])
        }
}
