/**
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

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.IRelayClientListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals

class NostrClientManualSubTest : BaseNostrClientTest() {
    @Test
    fun testEoseAfter100Events() =
        runBlocking {
            val client = NostrClient(socketBuilder, appScope)

            val resultChannel = Channel<String>(UNLIMITED)
            val events = mutableListOf<String>()
            val mySubId = "test-sub-id-1"

            val listener =
                object : IRelayClientListener {
                    override fun onEvent(
                        relay: IRelayClient,
                        subId: String,
                        event: Event,
                        arrivalTime: Long,
                        afterEOSE: Boolean,
                    ) {
                        if (mySubId == subId) {
                            resultChannel.trySend(event.id)
                        }
                    }

                    override fun onEOSE(
                        relay: IRelayClient,
                        subId: String,
                        arrivalTime: Long,
                    ) {
                        if (mySubId == subId) {
                            resultChannel.trySend("EOSE")
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

            client.openReqSubscription(mySubId, filters)

            withTimeoutOrNull(30000) {
                while (events.size < 101) {
                    val event = resultChannel.receive()
                    events.add(event)
                }
            }

            resultChannel.close()

            client.close(mySubId)
            client.unsubscribe(listener)
            client.disconnect()

            assertEquals(101, events.size)
            assertEquals(true, events.take(100).all { it.length == 64 })
            assertEquals("EOSE", events[100])
        }
}
