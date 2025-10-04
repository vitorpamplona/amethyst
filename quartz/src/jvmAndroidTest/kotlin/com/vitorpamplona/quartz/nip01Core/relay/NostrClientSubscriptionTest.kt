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
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClientSubscription
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals

class NostrClientSubscriptionTest : BaseNostrClientTest() {
    @Test
    fun testNostrClientSubscription() =
        runBlocking {
            val appScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val client = NostrClient(socketBuilder, appScope)

            val resultChannel = Channel<Event>(UNLIMITED)
            val events = mutableSetOf<Event>()

            val sub =
                NostrClientSubscription(
                    client = client,
                    filter = {
                        mapOf(
                            RelayUrlNormalizer.normalize("wss://relay.damus.io") to
                                listOf(
                                    Filter(
                                        kinds = listOf(MetadataEvent.KIND),
                                        limit = 100,
                                    ),
                                ),
                        )
                    },
                ) { event ->
                    assertEquals(MetadataEvent.KIND, event.kind)
                    resultChannel.trySend(event)
                }

            withTimeoutOrNull(30000) {
                while (events.size < 100) {
                    val event = resultChannel.receive()
                    events.add(event)
                }
            }

            resultChannel.close()

            sub.closeSubscription()

            client.disconnect()
            appScope.cancel()

            assertEquals(100, events.size)
        }
}
