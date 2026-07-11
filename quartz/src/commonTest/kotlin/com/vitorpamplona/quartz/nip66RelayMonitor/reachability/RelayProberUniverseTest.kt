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
package com.vitorpamplona.quartz.nip66RelayMonitor.reachability

import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip65RelayList.tags.AdvertisedRelayInfo
import com.vitorpamplona.quartz.nip65RelayList.tags.AdvertisedRelayType
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RelayProberUniverseTest {
    private fun norm(url: String) = RelayUrlNormalizer.normalizeOrNull(url)!!

    private fun relayInfo(url: String) = AdvertisedRelayInfo(norm(url), AdvertisedRelayType.BOTH)

    @Test
    fun collectsAdvertisedRelaysAcrossAuthors() =
        runBlocking {
            val store = EventStore(null)
            store.insert(AdvertisedRelayListEvent.create(listOf(relayInfo("wss://a.example"), relayInfo("wss://b.example")), NostrSignerSync()))
            store.insert(AdvertisedRelayListEvent.create(listOf(relayInfo("wss://b.example"), relayInfo("wss://c.example")), NostrSignerSync()))

            val universe = RelayProber.knownRelayUniverse(store)
            assertEquals(setOf(norm("wss://a.example"), norm("wss://b.example"), norm("wss://c.example")), universe)
            store.close()
        }

    @Test
    fun capsPathUrlsPerAuthority() =
        runBlocking {
            val store = EventStore(null)
            // One paid/filter host advertised as one path URL per user.
            val paths = (1..10).map { relayInfo("wss://filter.example/user$it") }
            store.insert(AdvertisedRelayListEvent.create(paths + relayInfo("wss://solo.example"), NostrSignerSync()))

            val universe = RelayProber.knownRelayUniverse(store, maxPerAuthority = 3)
            assertEquals(3, universe.count { it.url.contains("filter.example") }, "per-authority URLs must be capped")
            assertTrue(norm("wss://solo.example") in universe)
            store.close()
        }

    @Test
    fun onionRelaysAreExcludedByDefault() =
        runBlocking {
            val store = EventStore(null)
            store.insert(
                AdvertisedRelayListEvent.create(
                    listOf(relayInfo("wss://clear.example"), relayInfo("ws://someonionaddressabcdefghijklmnop.onion")),
                    NostrSignerSync(),
                ),
            )

            val universe = RelayProber.knownRelayUniverse(store)
            assertEquals(setOf(norm("wss://clear.example")), universe)

            val withOnion = RelayProber.knownRelayUniverse(store, includeOnion = true)
            assertEquals(2, withOnion.size)
            store.close()
        }

    @Test
    fun recordProbedKeepsPerRelayRttAndLiveWins() =
        runBlocking {
            val store = EventStore(null)
            val reach = RelayReachabilityStore(store, NostrSignerInternal(KeyPair()))

            val fast = norm("wss://fast.example")
            val slow = norm("wss://slow.example")
            val dead = norm("wss://dead.example")
            reach.recordProbed(mapOf(fast to 42L, slow to 9000L), setOf(dead, slow))

            val snapshot = reach.snapshot()
            // A relay both probed-live and reported dead stays live (live wins).
            assertEquals(setOf(fast, slow), snapshot.live)
            assertEquals(setOf(dead), snapshot.dead)
            store.close()
        }
}
