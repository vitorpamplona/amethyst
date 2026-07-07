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

import com.vitorpamplona.geode.fixtures.SyntheticEvents
import com.vitorpamplona.geode.testing.RelayClientTest
import com.vitorpamplona.geode.testing.preload
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllPagesFromPool
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlinx.coroutines.runBlocking
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals

class NostrClientFetchAllPagesPoolTest : RelayClientTest() {
    /**
     * The pool fans [fetchAllPagesFromPool] across relays, tags each event with the
     * relay it came from, and — like a fan-out REQ — does NOT dedup across relays:
     * an event held by two relays is delivered twice, once per source.
     */
    @Test
    fun fansOutPerRelayTagsSourceAndDoesNotDedupAcrossRelays() =
        runBlocking {
            val relayAUrl = RelayUrlNormalizer.normalize("ws://relay-a/")
            val relayBUrl = RelayUrlNormalizer.normalize("ws://relay-b/")

            // One shared event lives on BOTH relays; the rest are relay-exclusive.
            val shared = SyntheticEvents.fakeEvent(idSeed = 1)
            val onlyA = (2..4).map { SyntheticEvents.fakeEvent(idSeed = it) }
            val onlyB = (5..9).map { SyntheticEvents.fakeEvent(idSeed = it) }
            hub.getOrCreate(relayAUrl).preload(onlyA + shared)
            hub.getOrCreate(relayBUrl).preload(onlyB + shared)

            // onEvent runs on each relay's reader thread → collect thread-safely.
            val received = Collections.synchronizedList(mutableListOf<Pair<NormalizedRelayUrl, Event>>())
            val completed = ConcurrentHashMap<NormalizedRelayUrl, Int>()

            client.fetchAllPagesFromPool(
                filters =
                    linkedMapOf(
                        relayAUrl to listOf(Filter(kinds = listOf(1))),
                        relayBUrl to listOf(Filter(kinds = listOf(1))),
                    ),
                onRelayComplete = { relay, total -> completed[relay] = total },
            ) { event, relay -> received.add(relay to event) }

            val fromA = received.filter { it.first == relayAUrl }
            val fromB = received.filter { it.first == relayBUrl }
            // A: 3 exclusive + shared = 4 ; B: 5 exclusive + shared = 6.
            assertEquals(4, fromA.size, "every event must be tagged with relay A")
            assertEquals(6, fromB.size, "every event must be tagged with relay B")
            // The shared id arrives once per relay — not deduped across relays.
            assertEquals(2, received.count { it.second.id == shared.id }, "shared event must arrive from both relays")
            // onRelayComplete reports each relay's fetched total.
            assertEquals(4, completed[relayAUrl])
            assertEquals(6, completed[relayBUrl])
        }
}
