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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.nip51Lists.simpleGroupList.GroupTag
import com.vitorpamplona.quartz.nip7DThreads.ThreadEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the exact REQ each NIP-29 data source puts on the wire (kinds, `#d`/`#h` scope, per-relay
 * batching, `since`/`until`/`limit`, authors) — the "does the correct query load per screen" half of
 * amethyst/plans/2026-07-18-nip29-group-chat-test-plan.md (Tier B), without an Account/relay client.
 */
class RelayGroupFilterBuildersTest {
    private val relayAUrl = "wss://relay-a.example/"
    private val relayBUrl = "wss://relay-b.example/"
    private val relayA = RelayUrlNormalizer.normalizeOrNull(relayAUrl)!!
    private val relayB = RelayUrlNormalizer.normalizeOrNull(relayBUrl)!!

    // Joined set: two groups on relay A, one on relay B.
    private val joined =
        setOf(
            GroupTag("g1", relayAUrl),
            GroupTag("g2", relayAUrl),
            GroupTag("g3", relayBUrl),
        )
    private val g1OnA = GroupId("g1", relayA)

    private val timelineKinds = listOf(ChatEvent.KIND, PollEvent.KIND)
    private val threadKinds = listOf(ThreadEvent.KIND, CommentEvent.KIND)

    // --- State (always-on): one #d filter per host relay, batching that relay's group ids ---

    @Test
    fun `state batches one d-filter per host relay`() {
        val filters = buildRelayGroupStateFilters(joined) { null }
        assertEquals(2, filters.size)

        val a = filters.single { it.relay == relayA }
        assertEquals(RELAY_GROUP_STATE_KINDS, a.filter.kinds)
        assertEquals(setOf("g1", "g2"), a.filter.tags!!["d"]!!.toSet())
        assertNull("state is #d-scoped, never #h", a.filter.tags!!["h"])

        val b = filters.single { it.relay == relayB }
        assertEquals(listOf("g3"), b.filter.tags!!["d"])
    }

    @Test
    fun `state applies the per-relay since`() {
        val filters = buildRelayGroupStateFilters(joined) { relay -> if (relay == relayA) 111L else null }
        assertEquals(111L, filters.single { it.relay == relayA }.filter.since)
        assertNull(filters.single { it.relay == relayB }.filter.since)
    }

    // --- Joined chat tail (always-on): batched #h per relay, time floor, NO per-group limit ---

    @Test
    fun `joined chat tail batches one h-filter per relay with no count limit`() {
        val filters = buildRelayGroupJoinedChatTailFilters(joined, 999L)
        assertEquals(2, filters.size)

        val a = filters.single { it.relay == relayA }
        assertEquals(timelineKinds, a.filter.kinds)
        assertEquals(setOf("g1", "g2"), a.filter.tags!!["h"]!!.toSet())
        assertEquals(999L, a.filter.since)
        assertNull("a time-floored tail must NOT cap by count (that is what lets it batch)", a.filter.limit)
        assertNull(a.filter.until)
    }

    // --- Open chat tail: a single group's recent #h on the host relay ---

    @Test
    fun `open chat tail is a single host h-filter with since only`() {
        val f = buildRelayGroupOpenChatTailFilter(g1OnA, 5L)
        assertEquals(relayA, f.relay)
        assertEquals(timelineKinds, f.filter.kinds)
        assertEquals(listOf("g1"), f.filter.tags!!["h"])
        assertEquals(5L, f.filter.since)
        assertNull(f.filter.until)
        assertNull(f.filter.limit)
    }

    // --- Open chat history (backward pager): only armed relays, each at its own until, all authors ---

    @Test
    fun `history emits only armed relays at their until, all authors`() {
        val untilByRelay = mapOf(relayA to 200L) // relayB not armed → no cursor
        val filters = buildRelayGroupHistoryFilters(g1OnA, listOf(relayA, relayB), { untilByRelay[it] }, 50)

        val f = filters.single()
        assertEquals(relayA, f.relay)
        assertEquals(200L, f.filter.until)
        assertEquals(50, f.filter.limit)
        assertNull("history must be all-authors so it re-materializes my own messages too", f.filter.authors)
        assertNull("backward paging is until-anchored, not since", f.filter.since)
        assertEquals(listOf("g1"), f.filter.tags!!["h"])
    }

    @Test
    fun `history with nothing armed builds no filters`() {
        assertTrue(buildRelayGroupHistoryFilters(g1OnA, emptyList(), { 1L }, 50).isEmpty())
    }

    // --- Threads tab: kind 11/1111 #h on the host relay ---

    @Test
    fun `threads filter is host-scoped thread kinds`() {
        val f = buildRelayGroupThreadsFilter(g1OnA, 7L)
        assertEquals(relayA, f.relay)
        assertEquals(threadKinds, f.filter.kinds)
        assertEquals(listOf("g1"), f.filter.tags!!["h"])
        assertEquals(7L, f.filter.since)
    }

    // --- Card warmup joined-skip: joined groups are covered always-on, so warmup must skip them ---

    @Test
    fun `joined check keys on both group id and host relay`() {
        assertTrue(isRelayGroupJoined(joined, GroupId("g1", relayA)))
        // Same group id but a DIFFERENT host relay is a different group — must not count as joined.
        assertFalse(isRelayGroupJoined(joined, GroupId("g1", relayB)))
        assertFalse(isRelayGroupJoined(joined, GroupId("gX", relayA)))
        assertFalse(isRelayGroupJoined(emptySet(), GroupId("g1", relayA)))
    }

    // --- Empty joined set ---

    @Test
    fun `empty joined set produces no filters`() {
        assertTrue(buildRelayGroupStateFilters(emptySet()) { null }.isEmpty())
        assertTrue(buildRelayGroupJoinedChatTailFilters(emptySet(), 1L).isEmpty())
    }
}
