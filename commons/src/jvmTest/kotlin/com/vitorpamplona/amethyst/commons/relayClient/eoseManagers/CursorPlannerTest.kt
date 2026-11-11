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

import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A subscription reads its windows from the cursors of the holders its keys name, and writes its
 * EOSEs back to the holders it serves at that moment.
 */
class CursorPlannerTest {
    private val relay = NormalizedRelayUrl("wss://relay.example.com/")
    private val alice = "a".repeat(64)
    private val bob = "b".repeat(64)
    private val carol = "c".repeat(64)

    private fun notes(vararg authors: String) = Filter(authors = authors.toList(), kinds = listOf(1))

    private fun shape(vararg authors: String) = FilterShape(authors = authors.toSet(), kinds = setOf(1))

    private class Holder : RelayCursorHolder {
        override val relayCursors = RelayCursors()
    }

    @Test
    fun aRequestSplitByCursorVouchesForAllOfItOnceAnswered() {
        val cursors = RelayCursors()
        cursors.newEose(relay, listOf(shape(alice), shape(bob)), 1_000)
        assertEquals(listOf(CursorPart(shape(alice, bob), 1_000)), cursors.plan(relay, shape(alice, bob)))
    }

    @Test
    fun theWindowIsTheLaterOfTheFiltersFloorAndTheCursor() {
        val cursors = RelayCursors()
        cursors.newEose(relay, listOf(shape(alice)), 1_000)

        val laterFloor = CursorPlanner.window(listOf(RelayBasedFilter(relay, notes(alice).copy(since = 2_000))), listOf(cursors))
        assertEquals(2_000L, laterFloor.single().filter.since)
        val earlierFloor = CursorPlanner.window(listOf(RelayBasedFilter(relay, notes(alice).copy(since = 500))), listOf(cursors))
        assertEquals(1_000L, earlierFloor.single().filter.since)
        val noCursor = CursorPlanner.window(listOf(RelayBasedFilter(relay, notes(alice).copy(since = 500))), listOf(RelayCursors()))
        assertEquals(500L, noCursor.single().filter.since)
    }

    @Test
    fun anOlderPageIsNotTouched() {
        val cursors = RelayCursors()
        cursors.newEose(relay, listOf(shape(alice)), 1_000)
        val page = RelayBasedFilter(relay, notes(alice).copy(until = 400))
        assertEquals(listOf(page), CursorPlanner.window(listOf(page), listOf(cursors)))
    }

    @Test
    fun newAuthorsAreSplitIntoTheirOwnFilter() {
        val cursors = RelayCursors()
        cursors.newEose(relay, listOf(shape(alice)), 1_000)

        val windows = CursorPlanner.window(listOf(RelayBasedFilter(relay, notes(alice, bob))), listOf(cursors))

        assertEquals(listOf(listOf(alice) to 1_000L, listOf(bob) to null), windows.map { it.filter.authors to it.filter.since })
    }

    @Test
    fun withSeveralHoldersEachAuthorIsAskedFromTheOldestCursorAnyHolderHas() {
        val home = RelayCursors()
        home.newEose(relay, listOf(shape(alice, bob)), 1_000)
        val other = RelayCursors()
        other.newEose(relay, listOf(shape(alice)), 2_000)

        val parts = CursorPlanner.plan(relay, shape(alice, bob, carol), listOf(home, other))

        // alice: 1_000 (home is behind), bob: other never asked → scratch, carol: nobody asked → scratch.
        assertEquals(
            listOf(CursorPart(shape(alice), 1_000), CursorPart(shape(bob, carol), null)),
            parts,
        )
    }

    @Test
    fun withoutAuthorsOneHolderWithoutACursorMeansFromScratch() {
        val caughtUp = RelayCursors()
        val anyone = FilterShape(kinds = setOf(1))
        caughtUp.newEose(relay, listOf(anyone), 1_000)

        assertEquals(listOf(CursorPart(anyone, null)), CursorPlanner.plan(relay, anyone, listOf(caughtUp, RelayCursors())))
        assertEquals(listOf(CursorPart(anyone, 1_000)), CursorPlanner.plan(relay, anyone, listOf(caughtUp, caughtUp)))
    }

    @Test
    fun manyHoldersStillSplitAFilterInTwoAtMost() {
        // One time per holder and relay: an author is caught up for all of them, or asked from scratch.
        val authors = (1..6).map { "$it".repeat(64) }
        val holders =
            (1..6).map { i ->
                RelayCursors().also { it.newEose(relay, listOf(shape(*authors.take(i + 1).toTypedArray())), i * 100L) }
            }

        val parts = CursorPlanner.plan(relay, shape(*authors.toTypedArray()), holders)

        assertEquals(listOf(CursorPart(shape(*authors.take(2).toTypedArray()), 100), CursorPart(shape(*authors.drop(2).toTypedArray()), null)), parts)
    }

    @Test
    fun answersGoToTheHoldersServedWhenTheyArriveAndOnlyForLiveFilters() {
        var served = listOf<RelayCursors>()
        val inner = RecordingListener()
        val listener = CursorRoutingListener(inner) { served }
        val first = RelayCursors()
        val second = RelayCursors()

        served = listOf(first)
        listener.onEose(relay, listOf(notes(alice), notes(bob).copy(until = 10)))
        served = listOf(second)
        runBlocking { listener.onEvent(Event("e".repeat(64), alice, 5, 1, emptyArray(), "", ""), true, relay, listOf(notes(alice))) }
        runBlocking { listener.onEvent(Event("f".repeat(64), alice, 5, 1, emptyArray(), "", ""), false, relay, listOf(notes(carol))) }

        assertEquals(listOf(CursorPart(shape(alice), first.since(relay))), first.plan(relay, shape(alice)))
        assertEquals(listOf(CursorPart(shape(bob), null)), first.plan(relay, shape(bob)), "an older page vouches for nothing")
        assertEquals(listOf(CursorPart(shape(alice), second.since(relay))), second.plan(relay, shape(alice)))
        assertEquals(listOf(CursorPart(shape(carol), null)), second.plan(relay, shape(carol)), "a stored event is no proof of being live")
        assertEquals(1, inner.eoses)
        assertEquals(2, inner.events)
        listener.onClosed("closed", relay, null)
        assertEquals(1, inner.closed, "everything else reaches the wrapped listener")
    }

    private class RecordingListener : SubscriptionListener {
        var eoses = 0
        var events = 0
        var closed = 0

        override fun onEose(
            relay: NormalizedRelayUrl,
            forFilters: List<Filter>?,
        ) {
            eoses++
        }

        override suspend fun onEvent(
            event: Event,
            isLive: Boolean,
            relay: NormalizedRelayUrl,
            forFilters: List<Filter>?,
        ) {
            events++
        }

        override fun onClosed(
            message: String,
            relay: NormalizedRelayUrl,
            forFilters: List<Filter>?,
        ) {
            closed++
        }
    }

    /** A manager with one subscription per id, whose keys name a holder or not. */
    private class Key(
        val id: String,
        val holder: RelayCursorHolder?,
    ) : CursorHoldingQuery {
        override val cursorHolders get() = listOfNotNull(holder)
    }

    private inner class Manager(
        keys: () -> Set<Key>,
    ) : PerUniqueIdEoseManager<Key, String>(EmptyNostrClient(), keys) {
        val ownSince = mutableMapOf<String, SincePerRelayMap?>()

        override fun updateFilter(
            key: Key,
            since: SincePerRelayMap?,
        ): List<RelayBasedFilter> {
            ownSince[key.id] = since
            return listOf(RelayBasedFilter(relay, notes(alice)))
        }

        override fun id(key: Key) = key.id
    }

    @Test
    fun aManagerReadsAndWritesTheHoldersCursors() {
        val holder = Holder()
        val key = Key("feed", holder)
        val manager = Manager { setOf(key) }

        manager.updateSubscriptions(setOf(key))
        val sub = manager.findOrCreateSubFor(key)
        assertNull(
            sub
                .filters()
                ?.get(relay)
                ?.single()
                ?.since,
            "a new holder asks from scratch",
        )

        sub.listener.onEose(relay, sub.filters()?.get(relay))
        assertTrue(!holder.relayCursors.isEmpty(), "the answer reached the holder")

        manager.updateSubscriptions(setOf(key))
        assertEquals(
            holder.relayCursors.since(relay),
            sub
                .filters()
                ?.get(relay)
                ?.single()
                ?.since,
        )
        assertNull(manager.ownSince["feed"], "the manager's own map is not read for a key with holders")
    }

    @Test
    fun aKeyWithoutHoldersKeepsTheManagersOwnCursors() {
        val key = Key("feed", null)
        val manager = Manager { setOf(key) }

        manager.updateSubscriptions(setOf(key))
        manager.newEose(key, relay, 1_000)
        manager.updateSubscriptions(setOf(key))

        assertEquals(1_000L, manager.ownSince["feed"]?.get(relay)?.time)
    }
}
