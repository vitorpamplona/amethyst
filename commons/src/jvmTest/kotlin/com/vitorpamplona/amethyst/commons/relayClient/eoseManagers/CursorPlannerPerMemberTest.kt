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
package com.vitorpamplona.amethyst.commons.relayClient.eoseManagers

import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The notification tail asks one relay about several accounts in one `#p` filter. Each account's
 * window comes from its own feeds' cursors, and only accounts whose windows agree share a filter.
 */
class CursorPlannerPerMemberTest {
    private val relay = NormalizedRelayUrl("wss://relay.example.com/")
    private val alice = "a".repeat(64)
    private val bob = "b".repeat(64)

    // Two filters per relay, like the notification builders: the order never depends on the members.
    private fun build(
        pubkeys: List<String>,
        since: Long?,
    ) = listOf(
        RelayBasedFilter(relay, Filter(kinds = listOf(1), tags = mapOf("p" to pubkeys), limit = 500, since = since)),
        RelayBasedFilter(relay, Filter(kinds = listOf(7), tags = mapOf("p" to pubkeys), limit = 200, since = since)),
    )

    private fun plan(vararg members: CursorPlanner.Member<String>) = CursorPlanner.windowPerMember(members.toList(), build = ::build)

    private fun asked(filters: List<RelayBasedFilter>) = filters.map { Triple(it.filter.kinds!!.single(), it.filter.tags!!.getValue("p"), it.filter.since) }

    /** What the relay answered for [sent] reaches each holder, as the routing listener does. */
    private fun answer(
        sent: List<RelayBasedFilter>,
        time: Long,
        vararg holders: RelayCursors,
    ) = CursorPlanner.record(holders.toList(), relay, sent.map { it.filter }, time)

    @Test
    fun accountsThatAreCaughtUpTogetherShareTheirFilters() {
        val a = RelayCursors()
        val b = RelayCursors()
        answer(plan(CursorPlanner.Member(alice, null, listOf(a)), CursorPlanner.Member(bob, null, listOf(b))), 1_000, a, b)

        val next = plan(CursorPlanner.Member(alice, null, listOf(a)), CursorPlanner.Member(bob, null, listOf(b)))

        assertEquals(listOf(Triple(1, listOf(alice, bob), 1_000L), Triple(7, listOf(alice, bob), 1_000L)), asked(next))
    }

    @Test
    fun anAccountBackWithAnEmptyFeedIsAskedFromScratchAlone() {
        val a = RelayCursors()
        val b = RelayCursors()
        answer(plan(CursorPlanner.Member(alice, null, listOf(a)), CursorPlanner.Member(bob, null, listOf(b))), 1_000, a, b)

        // Bob switched away and back: a new feed set, with new cursors.
        val next = plan(CursorPlanner.Member(alice, null, listOf(a)), CursorPlanner.Member(bob, null, listOf(RelayCursors())))

        assertEquals(
            listOf(Triple(1, listOf(alice), 1_000L), Triple(7, listOf(alice), 1_000L), Triple(1, listOf(bob), null), Triple(7, listOf(bob), null)),
            asked(next),
        )
    }

    @Test
    fun anAccountJoiningARelayInheritsNoOtherAccountsCursor() {
        val a = RelayCursors()
        answer(plan(CursorPlanner.Member(alice, null, listOf(a))), 1_000, a)

        val b = RelayCursors()
        val joined = plan(CursorPlanner.Member(alice, null, listOf(a)), CursorPlanner.Member(bob, null, listOf(b)))
        assertEquals(setOf(Triple(1, listOf(bob), null), Triple(7, listOf(bob), null)), asked(joined).filter { it.second == listOf(bob) }.toSet())

        // Once answered, both are caught up and share again; Alice leaving keeps Bob's cursor.
        answer(joined, 2_000, a, b)
        assertEquals(listOf(Triple(1, listOf(alice, bob), 2_000L), Triple(7, listOf(alice, bob), 2_000L)), asked(plan(CursorPlanner.Member(alice, null, listOf(a)), CursorPlanner.Member(bob, null, listOf(b)))))
        assertEquals(listOf(Triple(1, listOf(bob), 2_000L), Triple(7, listOf(bob), 2_000L)), asked(plan(CursorPlanner.Member(bob, null, listOf(b)))))
    }

    @Test
    fun aFeedThatHoldsAFullPageFloorsItsOwnFirstFetchOnly() {
        val a = RelayCursors()
        val b = RelayCursors()

        val first = plan(CursorPlanner.Member(alice, 700, listOf(a)), CursorPlanner.Member(bob, null, listOf(b)))

        assertEquals(
            listOf(Triple(1, listOf(alice), 700L), Triple(7, listOf(alice), 700L), Triple(1, listOf(bob), null), Triple(7, listOf(bob), null)),
            asked(first),
        )
    }

    @Test
    fun anAccountWithNoHoldersIsAskedFromItsFloor() {
        val a = RelayCursors()
        answer(plan(CursorPlanner.Member(alice, null, listOf(a))), 1_000, a)

        // A screen's key whose feed set is already gone names no holder: nothing vouches for its notes.
        assertEquals(listOf(Triple(1, listOf(alice), 300L), Triple(7, listOf(alice), 300L)), asked(plan(CursorPlanner.Member(alice, 300, emptyList()))))
    }
}
