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

import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.PublishResult
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** How we read a relay's answer to a group command (a create, an edit, a Buzz DM add). */
class RelayGroupCommandResultsTest {
    private val relay = RelayUrlNormalizer.normalize("wss://buzz.example.com")
    private val dm = "11111111-1111-4111-8111-111111111111"
    private val other = "22222222-2222-4222-8222-222222222222"

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
    fun aRefusalThePoolWillRetryIsNotARefusal() {
        // A slow signer: the AUTH hasn't landed when the retry is answered; the pool re-sends later.
        val auth = mapOf(relay to PublishResult(false, "auth-required: we only accept events from authenticated users"))
        assertNull(relayGroupCommandRefusal(auth))
        assertEquals(BuzzDmAddResult.NoAnswer, buzzDmAddResult(auth, relay, dm))
        // A back-off longer than the wait: the pool re-sends after it.
        val backOff = mapOf(relay to PublishResult(false, "rate-limited: slow down", retryAfterMs = 30_000))
        assertNull(relayGroupCommandRefusal(backOff))
        assertEquals(BuzzDmAddResult.NoAnswer, buzzDmAddResult(backOff, relay, dm))
        // Without a back-off the pool drops the event: that is the relay's answer.
        val dropped = mapOf(relay to PublishResult(false, "rate-limited: slow down"))
        assertEquals("slow down", relayGroupCommandRefusal(dropped))
        assertEquals(BuzzDmAddResult.Refused("rate-limited: slow down"), buzzDmAddResult(dropped, relay, dm))
    }

    @Test
    fun anAgentsHistoryTagRefusalAsksForAnUntaggedAdd() {
        assertTrue(buzzDmAddRefusedHistoryTag(mapOf(relay to PublishResult(false, ONLY_PEOPLE_SHARE_HISTORY))))
        assertFalse(buzzDmAddRefusedHistoryTag(mapOf(relay to PublishResult(false, "forbidden: not a member"))))
        assertFalse(buzzDmAddRefusedHistoryTag(mapOf(relay to PublishResult(true, "response:{}"))))
    }
}
