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
package com.vitorpamplona.amethyst.commons.napplet.gateways

import com.vitorpamplona.amethyst.commons.napplet.OutboxRelayPlan
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NappletOutboxRoutingTest {
    private val alice = "a1".repeat(32)
    private val bob = "b2".repeat(32)
    private val carol = "c3".repeat(32)

    private fun relay(url: String) = RelayUrlNormalizer.normalize(url)

    private val mine = setOf(relay("wss://mine.example.com"))

    private class Lists {
        val write = HashMap<HexKey, List<NormalizedRelayUrl>>()
        val read = HashMap<HexKey, List<NormalizedRelayUrl>>()
        val fetched = mutableListOf<List<HexKey>>()

        // What a relay-list fetch would learn, by author.
        val onFetch = HashMap<HexKey, List<NormalizedRelayUrl>>()
    }

    private fun routing(lists: Lists) =
        NappletOutboxRouting(
            relayListOf = { author, write -> (if (write) lists.write else lists.read)[author] },
            fetchRelayLists = { authors ->
                lists.fetched += authors
                authors.forEach { author -> lists.onFetch[author]?.let { lists.write[author] = it } }
            },
            fallback = { mine },
        )

    @Test
    fun readsGoToEachAuthorsWriteRelays() =
        runTest {
            val lists = Lists()
            lists.write[alice] = listOf(relay("wss://alice.example.com"))
            lists.onFetch[bob] = listOf(relay("wss://bob.example.com"))

            val route = routing(lists).route(listOf(alice, bob), write = true)

            assertEquals(setOf(relay("wss://alice.example.com"), relay("wss://bob.example.com")), route.relays)
            // Only the author without a known list was looked up, once.
            assertEquals(listOf(listOf(bob)), lists.fetched)
            assertEquals(OutboxRelayPlan.Source.NIP65, route.source)
            assertTrue(route.missing.isEmpty())
        }

    @Test
    fun anAuthorWithNoListIsReportedAndReadsFallBackToTheUsersRelays() =
        runTest {
            val lists = Lists()
            lists.write[alice] = listOf(relay("wss://alice.example.com"))
            val partial = routing(lists).route(listOf(alice, carol), write = true)
            assertEquals(listOf(carol), partial.missing)
            assertEquals(setOf(relay("wss://alice.example.com")), partial.relays)

            val nobody = routing(Lists()).route(listOf(carol), write = true)
            assertEquals(mine, nobody.relays)
            assertEquals(OutboxRelayPlan.Source.FALLBACK, nobody.source)

            // No author at all: the user's relays.
            assertEquals(mine, routing(Lists()).route(emptyList(), write = true).relays)
        }

    @Test
    fun aDeliveryNeverGuessesARecipientsInbox() =
        runTest {
            val route = routing(Lists()).route(listOf(carol), write = false)
            assertTrue(route.relays.isEmpty())
            assertEquals(listOf(carol), route.missing)
        }

    @Test
    fun aNappletCannotWidenTheRoutePastItsLimits() =
        runTest {
            val lists = Lists()
            val authors = (0 until 200).map { it.toString(16).padStart(64, '0') }
            authors.forEach { author -> lists.write[author] = (1..5).map { relay("wss://r$it-${author.takeLast(4)}.example.com") } }

            val route = routing(lists).route(authors + "not-a-key", write = true)
            assertEquals(NappletOutboxRouting.MAX_RELAYS, route.relays.size)
            assertTrue(lists.fetched.isEmpty())
        }

    @Test
    fun hintsOnPrivateNetworksAreDropped() {
        val checked =
            NappletOutboxRouting.checkedHints(
                listOf("wss://relay.example.com", "ws://127.0.0.1:7777", "wss://192.168.1.4", "ws://localhost:4869", "wss://relay.local", "not a url", "wss://relay.example.com/"),
            )
        assertEquals(setOf(relay("wss://relay.example.com")), checked)
    }
}
