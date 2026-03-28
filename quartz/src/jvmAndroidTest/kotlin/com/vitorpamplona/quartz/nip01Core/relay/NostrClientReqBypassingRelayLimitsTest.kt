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
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllPages
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class NostrClientReqBypassingRelayLimitsTest : BaseNostrClientTest() {
    @Test
    fun testDownloadFromRelayReturnsMetadataEvents() =
        runBlocking {
            val appScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val client = NostrClient(socketBuilder, appScope)

            val events = mutableListOf<Event>()

            // nos.lol returns only 500 events per req
            val totalFound =
                client.fetchAllPages(
                    relay = "wss://nos.lol",
                    filters =
                        listOf(
                            Filter(
                                kinds = listOf(MetadataEvent.KIND),
                                limit = 1000,
                            ),
                        ),
                ) { event ->
                    events.add(event)
                }

            client.disconnect()
            delay(500)
            appScope.cancel()

            assertEquals(1000, totalFound, "Expected 1000 events from wss://nos.lol")
            assertEquals(1000, events.size, "Events list should be 1000 events")
            events.forEach { event ->
                assertEquals(MetadataEvent.KIND, event.kind, "All events should be kind ${MetadataEvent.KIND}")
            }
        }

    @Test
    fun testDownloadFromRelayReturnsMetadataAndContactListEvents() =
        runBlocking {
            val appScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val client = NostrClient(socketBuilder, appScope)

            val metadataEvents = mutableListOf<Event>()
            val contactListEvents = mutableListOf<Event>()

            // nos.lol returns only 500 events per req
            val totalFound =
                client.fetchAllPages(
                    relay = "wss://nos.lol",
                    filters =
                        listOf(
                            Filter(
                                kinds = listOf(MetadataEvent.KIND),
                                limit = 1000,
                            ),
                            Filter(
                                kinds = listOf(ContactListEvent.KIND),
                                limit = 1500,
                            ),
                        ),
                ) { event ->
                    if (event.kind == MetadataEvent.KIND) {
                        metadataEvents.add(event)
                    }
                    if (event.kind == ContactListEvent.KIND) {
                        contactListEvents.add(event)
                    }
                }

            client.disconnect()
            delay(500)
            appScope.cancel()

            assertEquals(2500, totalFound, "Expected 1000 events from wss://nos.lol")
            assertEquals(1000, metadataEvents.size, "Events list should be 1000 events")
            assertEquals(1500, contactListEvents.size, "Events list should be 1000 events")
            metadataEvents.forEach { event ->
                assertEquals(MetadataEvent.KIND, event.kind, "All events should be kind ${MetadataEvent.KIND}")
            }
            contactListEvents.forEach { event ->
                assertEquals(ContactListEvent.KIND, event.kind, "All events should be kind ${ContactListEvent.KIND}")
            }
        }
}
