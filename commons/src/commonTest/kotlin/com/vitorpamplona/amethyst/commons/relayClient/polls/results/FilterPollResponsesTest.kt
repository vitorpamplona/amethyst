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
package com.vitorpamplona.amethyst.commons.relayClient.polls.results

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.relays.MutableTime
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip88Polls.response.PollResponseEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FilterPollResponsesTest {
    private val pollId = "a".repeat(64)

    /** The fixed discovery set in canonical form, spelled out so a change to it has to be acknowledged. */
    private val discovery =
        listOf(
            "wss://relay.primal.net/",
            "wss://relay.damus.io/",
            "wss://relay.snort.social/",
            "wss://nos.lol/",
            "wss://relay.ditto.pub/",
        )

    /** A kind-1068 poll with the given `relay` tags, seen on [seenOn], from an author with no inbox loaded. */
    private fun pollNote(
        relayTags: List<String> = emptyList(),
        seenOn: List<NormalizedRelayUrl> = emptyList(),
    ): Note {
        val event =
            PollEvent(
                id = pollId,
                pubKey = "1".repeat(64),
                createdAt = 1000,
                tags = relayTags.map { arrayOf("relay", it) }.toTypedArray(),
                content = "Question?",
                sig = "0".repeat(128),
            )
        val note = Note(pollId)
        note.loadEvent(event, User("1".repeat(64)) { addr -> Note(addr.toValue()) }, emptyList())
        seenOn.forEach { note.addRelay(it) }
        return note
    }

    @Test
    fun ballotsFiledOnlyOnADiscoveryRelayAreStillAskedFor() {
        // No relay tags on the poll, nothing seen anywhere, author inbox empty: the old query
        // built an empty relay set and fetched nothing, under-counting every cross-client vote.
        val filters = filterPollResponses(pollNote(), since = null)

        val byRelay = assertNotNull(filters).associateBy { it.relay.url }
        assertEquals(discovery.toSet(), byRelay.keys)

        val ditto = byRelay.getValue("wss://relay.ditto.pub/")
        assertEquals(listOf(PollResponseEvent.KIND), ditto.filter.kinds)
        assertEquals(listOf(pollId), ditto.filter.tags?.get("e"))
    }

    @Test
    fun voteRelaySetIncludesDiscoveryWhenNothingElseNamesThem() {
        assertEquals(discovery, voteRelaySet(null, emptyList()).map { it.url })
    }

    @Test
    fun pollRelaysLeadReactionsFollowAndDiscoveryIsDeduplicated() {
        val note =
            pollNote(
                relayTags = listOf("wss://relay.azzamo.net", "wss://relay.primal.net"),
                seenOn =
                    listOf(
                        RelayUrlNormalizer.normalize("wss://nostr.wine"),
                        RelayUrlNormalizer.normalize("wss://relay.azzamo.net"),
                    ),
            )

        val relays = filterPollResponses(note, since = null)?.map { it.relay.url }

        // relay.primal.net is named by both the poll and the discovery set; it appears once, at
        // the poll's position.
        assertEquals(
            listOf("wss://relay.azzamo.net/", "wss://relay.primal.net/", "wss://nostr.wine/") + discovery.filter { it != "wss://relay.primal.net/" },
            relays,
        )
    }

    @Test
    fun sinceCarriesOverPerRelayAndDiscoveryRelaysStartFresh() {
        val note = pollNote(relayTags = listOf("wss://relay.azzamo.net"))
        val since: SincePerRelayMap = mutableMapOf(RelayUrlNormalizer.normalize("wss://relay.azzamo.net") to MutableTime(500))

        val filters = assertNotNull(filterPollResponses(note, since)).associateBy { it.relay.url }

        assertEquals(500, filters.getValue("wss://relay.azzamo.net/").filter.since)
        assertNull(filters.getValue("wss://relay.primal.net/").filter.since)
    }
}
