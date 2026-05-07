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
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllPages
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.testrelay.SyntheticEvents
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
            // Each event needs a unique pubkey so replaceable kind 0 doesn't
            // collapse them all to one row.
            val corpus =
                (1..1000).map {
                    SyntheticEvents.fakeEvent(
                        idSeed = it,
                        kind = MetadataEvent.KIND,
                        pubKey = SyntheticEvents.hexId(it),
                    )
                }
            relayHub.getOrCreate("ws://127.0.0.1:7770/").preload(corpus)

            val appScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val client = NostrClient(socketBuilder, appScope)

            val events = mutableListOf<Event>()

            val totalFound =
                client.fetchAllPages(
                    relay = "ws://127.0.0.1:7770/",
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
            relayHub.close()

            assertEquals(1000, totalFound)
            assertEquals(1000, events.size)
            events.forEach { event ->
                assertEquals(MetadataEvent.KIND, event.kind)
            }
        }

    @Test
    fun testDownloadFromRelayReturnsMetadataAndContactListEvents() =
        runBlocking {
            val metadata =
                (1..1000).map {
                    SyntheticEvents.fakeEvent(
                        idSeed = it,
                        kind = MetadataEvent.KIND,
                        pubKey = SyntheticEvents.hexId(it),
                    )
                }
            val contacts =
                (1..1500).map {
                    SyntheticEvents.fakeEvent(
                        idSeed = 100_000 + it,
                        kind = ContactListEvent.KIND,
                        pubKey = SyntheticEvents.hexId(100_000 + it),
                    )
                }
            relayHub.getOrCreate("ws://127.0.0.1:7770/").preload(metadata + contacts)

            val appScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val client = NostrClient(socketBuilder, appScope)

            val metadataEvents = mutableListOf<Event>()
            val contactListEvents = mutableListOf<Event>()

            val totalFound =
                client.fetchAllPages(
                    relay = "ws://127.0.0.1:7770/",
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
            relayHub.close()

            assertEquals(2500, totalFound)
            assertEquals(1000, metadataEvents.size)
            assertEquals(1500, contactListEvents.size)
        }
}
