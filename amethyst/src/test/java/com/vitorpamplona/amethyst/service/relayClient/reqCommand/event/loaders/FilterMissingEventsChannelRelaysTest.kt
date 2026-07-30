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
package com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.loaders

import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.EventFinderQueryState
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

/**
 * A NIP-29 / Buzz group's history lives on ONE relay — its host — and nowhere else, so the id REQ for
 * an unloaded message in that group has to name that relay. The group is keyed by (host relay, group
 * id), which is not derivable from the event's tags alone, so the loader has to resolve it through the
 * channel the ingest path attached (`LocalCache.getChannelToLoadFrom`) rather than the tag-only
 * `getAnyChannel`. Without that, a message whose reply reached us from a mirror — or with no relay
 * provenance at all — is only ever asked for on the account's default relays, which don't carry the
 * group.
 */
class FilterMissingEventsChannelRelaysTest {
    private val hostRelay = RelayUrlNormalizer.normalizeOrNull("wss://buzz.host.example/")!!
    private val mirrorRelay = RelayUrlNormalizer.normalizeOrNull("wss://mirror.example/")!!

    private val parentId = "1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a"
    private val replyId = "2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b"

    /** A NIP-29 kind-9 group message answering [parentId], scoped to group `g1` by its `h` tag. */
    private val replyJson =
        """
        {
          "id": "$replyId",
          "pubkey": "5e759c2ca4a4e222ba7af89e6ff315e1d27843fe8bd0a3e7e61e4ba5b1c07326",
          "created_at": 1785382199,
          "kind": 9,
          "tags": [
            ["h", "g1"],
            ["e", "$parentId", "", "reply"]
          ],
          "content": "same question here",
          "sig": ""
        }
        """.trimIndent()

    @Before
    fun dropCachedNotes() {
        LocalCache.notes.remove(parentId)
        LocalCache.notes.remove(replyId)
    }

    private fun stubAccount(): Account {
        val account = mockk<Account>(relaxed = true)
        every { account.followPlusAllMineWithSearch.flow } returns MutableStateFlow(emptySet())
        every { account.searchRelayList.flow } returns MutableStateFlow(emptySet())
        every { account.concordSessions.sessionFor(any()) } returns null
        return account
    }

    @Test
    fun `an unloaded group parent is asked for on the group's host relay, not only where the reply came from`() {
        val group = RelayGroupChannel(GroupId("g1", hostRelay))
        val replyEvent = Event.fromJson(replyJson)

        val parent = LocalCache.getOrCreateNote(parentId)
        val reply = LocalCache.getOrCreateNote(replyId)
        reply.loadEvent(replyEvent, LocalCache.getOrCreateUser(replyEvent.pubKey), listOf(parent))
        // The reply reached us from a mirror; only the group's channel knows where the group lives.
        reply.addRelay(mirrorRelay)
        parent.addReply(reply)
        reply.addGatherer(group)

        assertSame("the parent's group comes from its consumed reply", group, LocalCache.getChannelToLoadFrom(parent))

        val filters = filterMissingEvents(listOf(EventFinderQueryState(parent, stubAccount())))
        assertEquals(setOf(hostRelay, mirrorRelay), filters.map { it.relay }.toSet())
        filters.forEach { assertEquals(listOf(parentId), it.filter.ids) }
    }
}
