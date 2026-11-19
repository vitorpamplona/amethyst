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
package com.vitorpamplona.amethyst.commons.model

import com.vitorpamplona.amethyst.commons.model.buzz.BuzzRelayDialect
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.PublishResult
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** How we read a relay's answer to a group command (a create, an edit, a Buzz DM add), and a Buzz group state's shape. */
class RelayGroupCommandResultsTest {
    private val relay = RelayUrlNormalizer.normalize("wss://buzz.example.com")
    private val dm = "11111111-1111-4111-8111-111111111111"
    private val other = "22222222-2222-4222-8222-222222222222"

    private fun metadata(vararg tags: Array<String>): Event = EventFactory.create("00".repeat(32), "aa".repeat(32), 100, GroupMetadataEvent.KIND, arrayOf(*tags), "", "22".repeat(64))

    @Test
    fun aRefusedCommandReportsTheRelaysReason() {
        val refused = mapOf(relay to PublishResult(false, "invalid: an open channel named \"general\" already exists"))
        assertEquals("an open channel named \"general\" already exists", relayGroupCommandRefusal(refused))
        assertNull(relayGroupCommandRefusal(mapOf(relay to PublishResult(true, ""))))
        // The relay never weighed in: nothing to report, the create may still land.
        assertNull(relayGroupCommandRefusal(mapOf(relay to PublishResult(false, PublishResult.NO_RESPONSE))))
    }

    @Test
    fun aDmAddFollowsTheDmTheRelayNames() {
        // command_executor.rs: `response:{"channel_id":…,"created":…}`.
        val grown = buzzDmAddResult(mapOf(relay to PublishResult(true, "response:{\"channel_id\":\"$dm\",\"created\":false}")), relay, dm)
        assertEquals(BuzzDmAddResult.Added(GroupId(dm, relay), moved = false), grown)
        val moved = buzzDmAddResult(mapOf(relay to PublishResult(true, "response:{\"channel_id\":\"$other\",\"created\":true}")), relay, dm)
        assertEquals(BuzzDmAddResult.Added(GroupId(other, relay), moved = true), moved)
    }

    @Test
    fun aDmAddThatWouldShareHistoryAsksFirst() {
        assertEquals(BuzzDmAddResult.NeedsHistoryChoice, buzzDmAddResult(mapOf(relay to PublishResult(false, "needs-choice: history")), relay, dm))
        assertEquals(
            BuzzDmAddResult.Refused(ONLY_PEOPLE_SHARE_HISTORY),
            buzzDmAddResult(mapOf(relay to PublishResult(false, ONLY_PEOPLE_SHARE_HISTORY)), relay, dm),
        )
        assertEquals(BuzzDmAddResult.NoAnswer, buzzDmAddResult(mapOf(relay to PublishResult(false, PublishResult.NO_RESPONSE)), relay, dm))
        assertEquals(BuzzDmAddResult.NoAnswer, buzzDmAddResult(mapOf(relay to PublishResult(true, "duplicate: already processed")), relay, dm))
    }

    @Test
    fun recognisesBuzzGroupStateWithAndWithoutTheCreatorTag() {
        // NIP-CL relay: channel type first, then the creator.
        assertTrue(BuzzRelayDialect.looksLikeBuzzGroupState(metadata(arrayOf("d", dm), arrayOf("t", "stream"), arrayOf("P", "bb".repeat(32)))))
        // Before buzz `8bb49a1b6`: the channel type alone, on a UUID-named channel.
        assertTrue(BuzzRelayDialect.looksLikeBuzzGroupState(metadata(arrayOf("d", dm), arrayOf("name", "general"), arrayOf("t", "forum"))))
        // NIP-29: topics, or a `#stream` topic on a group with a short id.
        assertFalse(BuzzRelayDialect.looksLikeBuzzGroupState(metadata(arrayOf("d", "pizza"), arrayOf("t", "stream"))))
        assertFalse(BuzzRelayDialect.looksLikeBuzzGroupState(metadata(arrayOf("d", dm), arrayOf("t", "bitcoin"), arrayOf("t", "stream"))))
        assertFalse(BuzzRelayDialect.looksLikeBuzzGroupState(null))
    }
}
