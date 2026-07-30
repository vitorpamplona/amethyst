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

import com.vitorpamplona.amethyst.commons.model.concord.ConcordChannel
import com.vitorpamplona.amethyst.commons.model.concord.ConcordCommunitySession
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.EventFinderQueryState
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChannelId
import com.vitorpamplona.quartz.concord.envelope.ConcordStreamEnvelope
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A reply notification from a Concord community must be able to render the message it answers.
 *
 * The reported failure: a kind-1111 Concord reply arrives by push, its parent kind-9 message isn't in
 * the cache, and the reply-to slot stays an unresolvable `nostr:nevent1…`. The reason is that the
 * parent is a *rumor* — it only ever exists inside a kind-1059 Chat Plane wrap — so the loader's
 * `{"ids":[…]}` REQ can never find it (and leaks the private rumor id while trying). These tests pin
 * the two halves of the fix: the id REQ is not emitted, and a plane window aimed at the community's
 * relays is.
 */
class FilterMissingConcordRumorsTest {
    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://relay.concord.example/")!!
    private val otherRelay = RelayUrlNormalizer.normalizeOrNull("wss://someone-elses-relay.example/")!!

    private val communityId = "b1b1b1b1b1b1b1b1b1b1b1b1b1b1b1b1b1b1b1b1b1b1b1b1b1b1b1b1b1b1b1b1"
    private val channelIdHex = "b4853b67e720d3bbb96afad899648d8403139698bbf3c68826df2c8d1bca4e23"
    private val planePk = "cc00cc00cc00cc00cc00cc00cc00cc00cc00cc00cc00cc00cc00cc00cc00cc00"
    private val priorEpochPlanePk = "aa00aa00aa00aa00aa00aa00aa00aa00aa00aa00aa00aa00aa00aa00aa00aa00"

    /** The reply from the report, verbatim: a kind-1111 comment bound to the channel, unsigned (a rumor). */
    private val replyJson =
        """
        {
          "id": "93da2ffc2efb228b7d956c51ec15f19fcf2a4a0ebb536b4330a16d899d862250",
          "pubkey": "5e759c2ca4a4e222ba7af89e6ff315e1d27843fe8bd0a3e7e61e4ba5b1c07326",
          "created_at": 1785382199,
          "kind": 1111,
          "tags": [
            ["channel", "b4853b67e720d3bbb96afad899648d8403139698bbf3c68826df2c8d1bca4e23"],
            ["epoch", "2"],
            ["K", "9"],
            ["E", "f514388dcfe5e4da8051c363545848e0ecbd55aa618be9658a5385df1344a007", "", "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"],
            ["P", "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"],
            ["k", "9"],
            ["e", "f514388dcfe5e4da8051c363545848e0ecbd55aa618be9658a5385df1344a007", "", "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"],
            ["p", "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"],
            ["ms", "62"]
          ],
          "content": "I saw you fixed it in today's update.",
          "sig": ""
        }
        """.trimIndent()

    private val parentId = "f514388dcfe5e4da8051c363545848e0ecbd55aa618be9658a5385df1344a007"
    private val replyId = "93da2ffc2efb228b7d956c51ec15f19fcf2a4a0ebb536b4330a16d899d862250"

    /**
     * [LocalCache] is a singleton, so each test has to start from an empty pair of notes — otherwise a
     * previous test's channel gatherer (a different mock) is still attached to the reply and answers
     * for it.
     */
    @Before
    fun dropCachedNotes() {
        LocalCache.notes.remove(parentId)
        LocalCache.notes.remove(replyId)
    }

    /**
     * The channel a Concord rumor is attached to at ingest. Mocked rather than folded from a Control
     * Plane: all the loaders read off it is its id and its community's relays.
     */
    private fun concordChannel() =
        mockk<ConcordChannel>(relaxed = true).also {
            every { it.channelId } returns ConcordChannelId(communityId, channelIdHex)
            every { it.relays() } returns setOf(relay)
        }

    /**
     * Reproduces the post-ingest cache state of the reported notification: the kind-1111 reply loaded
     * and attached to its Concord channel, its parent a bare id-only [Note]. Returns the parent.
     */
    private fun landReplyWithUnloadedParent(channel: ConcordChannel?): Note {
        val replyEvent = Event.fromJson(replyJson)
        val parent = LocalCache.getOrCreateNote(parentId)
        val reply = LocalCache.getOrCreateNote(replyEvent.id)

        reply.loadEvent(replyEvent, LocalCache.getOrCreateUser(replyEvent.pubKey), listOf(parent))
        // The wrap it rode in on was seen here, so this is the only relay the reply itself points at.
        reply.addRelay(relay)
        parent.addReply(reply)
        channel?.let { reply.addGatherer(it) }

        return parent
    }

    /**
     * An account with no relay lists of its own, so the only relays any filter can name are the ones
     * the notes/channel supply — which is what these assertions are about.
     */
    private fun stubAccount(): Account {
        val account = mockk<Account>(relaxed = true)
        every { account.followPlusAllMineWithSearch.flow } returns MutableStateFlow(emptySet())
        every { account.searchRelayList.flow } returns MutableStateFlow(emptySet())
        return account
    }

    /** An account whose Control Plane has folded this channel, so its plane keys are derivable. */
    private fun accountWithFoldedChannel(): Account {
        val account = stubAccount()
        val session = mockk<ConcordCommunitySession>(relaxed = true)
        every { account.concordSessions.sessionFor(communityId) } returns session
        // Every held epoch's plane, so the window reaches across a CORD-06 Refounding.
        every { session.channelPlaneAddressesAllEpochs(channelIdHex) } returns listOf(planePk, priorEpochPlanePk)
        return account
    }

    private fun accountWithUnfoldedChannel(): Account {
        val account = stubAccount()
        every { account.concordSessions.sessionFor(any()) } returns null
        return account
    }

    @Test
    fun `the channel of an unloaded parent is borrowed from the reply that arrived over its plane`() {
        val channel = concordChannel()
        val parent = landReplyWithUnloadedParent(channel)

        assertSame("the parent's room comes from its consumed reply", channel, LocalCache.getChannelToLoadFrom(parent))
        assertSame(channel, concordChannelToLoadFrom(parent))
    }

    @Test
    fun `a missing Concord parent is never requested by id`() {
        val channel = concordChannel()
        val parent = landReplyWithUnloadedParent(channel)
        val account = accountWithFoldedChannel()
        val keys = listOf(EventFinderQueryState(parent, account))

        val ids = filterMissingEvents(keys).flatMap { it.filter.ids.orEmpty() }
        assertTrue("a rumor id must never be sent to a relay, it cannot be served: $ids", ids.isEmpty())
    }

    @Test
    fun `a missing Concord parent is fetched as a plane window on the community relays`() {
        val channel = concordChannel()
        val parent = landReplyWithUnloadedParent(channel)
        val account = accountWithFoldedChannel()
        val keys = listOf(EventFinderQueryState(parent, account))

        val filters = filterMissingConcordRumors(keys)
        assertEquals(1, filters.size)
        assertEquals(relay, filters[0].relay)

        val filter = filters[0].filter
        assertEquals(listOf(ConcordStreamEnvelope.KIND_WRAP), filter.kinds)
        assertEquals(listOf(planePk, priorEpochPlanePk).sorted(), filter.authors)
        // The window tops out at the reply — the message it answers is necessarily at or below it.
        assertEquals(1785382199L, filter.until)
        assertEquals(50, filter.limit)
        assertNull("a plane window is never an ids REQ", filter.ids)
    }

    @Test
    fun `the reply's own key backfills its parent, which is how a notification card loads it`() {
        // The EventFinder is mounted on the note the card renders — the reply — not on the parent.
        val channel = concordChannel()
        val parent = landReplyWithUnloadedParent(channel)
        val reply = parent.replies.single()
        val keys = listOf(EventFinderQueryState(reply, accountWithFoldedChannel()))

        assertEquals(1, filterMissingConcordRumors(keys).size)
        assertTrue(filterMissingEvents(keys).flatMap { it.filter.ids.orEmpty() }.isEmpty())
    }

    @Test
    fun `nothing is requested until the Control Plane folds the channel`() {
        val parent = landReplyWithUnloadedParent(concordChannel())
        val keys = listOf(EventFinderQueryState(parent, accountWithUnfoldedChannel()))

        assertTrue(filterMissingConcordRumors(keys).isEmpty())
    }

    @Test
    fun `a plain missing parent is still fetched by id`() {
        // Guard against over-blocking: only Concord content skips the ids REQ.
        val parent = landReplyWithUnloadedParent(channel = null)
        val keys = listOf(EventFinderQueryState(parent, accountWithUnfoldedChannel()))

        val byRelay = filterMissingEvents(keys)
        assertEquals(listOf(relay), byRelay.map { it.relay })
        assertEquals(listOf(listOf(parentId)), byRelay.map { it.filter.ids })
        assertTrue(filterMissingConcordRumors(keys).isEmpty())
    }

    @Test
    fun `two replies to the same unloaded parent collapse into one plane window`() {
        val channel = concordChannel()
        val parent = landReplyWithUnloadedParent(channel)
        val account = accountWithFoldedChannel()
        val reply = parent.replies.single()

        val keys =
            listOf(
                EventFinderQueryState(parent, account),
                EventFinderQueryState(reply, account),
            )

        assertEquals(1, filterMissingConcordRumors(keys).size)
    }

    @Test
    fun `an unrelated relay is not asked for the plane`() {
        val channel = concordChannel()
        every { channel.relays() } returns setOf(relay)
        val parent = landReplyWithUnloadedParent(channel)
        val keys = listOf(EventFinderQueryState(parent, accountWithFoldedChannel()))

        assertTrue(filterMissingConcordRumors(keys).none { it.relay == otherRelay })
    }
}
