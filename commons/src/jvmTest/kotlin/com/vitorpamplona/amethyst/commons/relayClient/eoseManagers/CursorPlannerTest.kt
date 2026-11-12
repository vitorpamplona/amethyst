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
    fun aRewindIsNotUndoneByAnAnswerToTheRequestStillOnTheWire() {
        val cursors = RelayCursors()
        val asked = notes(alice).copy(since = 1_000)
        cursors.planned(relay, CursorSource.DEFAULT, setOf(1_000))
        cursors.newEose(relay, listOf(shape(alice)), 2_000)

        // The pruner dropped notes from 500 on.
        cursors.rewindTo(500)
        // A live event arrives on the REQ still asking since 1_000, before the re-plan goes out.
        CursorPlanner.record(listOf(cursors), relay, listOf(asked), 3_000)

        assertEquals(500L, cursors.since(relay), "the band from 500 to 1_000 was never asked again")
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
    fun aKindTheRelayRefusedDoesNotLeaveTheRequestUncovered() {
        val refusing = NormalizedRelayUrl("wss://refuses-long-form.example.com/")
        val asked = Filter(authors = listOf(alice), kinds = listOf(1, 30023))
        // The pool took 30023 out of what it sent, and its EOSE reports what it sent.
        val sent = Filter(authors = listOf(alice), kinds = listOf(1))
        val cursors = RelayCursors()
        val listener = CursorRoutingListener(RecordingListener(), asked = { listOf(asked) }) { listOf(cursors) }

        listener.onEose(refusing, listOf(sent))

        val again = cursors.plan(refusing, FilterShape.of(asked)).single()
        assertEquals(cursors.since(refusing), again.since, "asked again from the cursor, not from scratch")
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
        assertNull(manager.ownSince["feed"], "the builder is given no since: the holders' cursors set the windows")
    }

    @Test
    fun twoManagersFillingOneHolderKeepTheirOwnEntries() {
        // Discover's tabs, Home's feeds, the notifications feed: one holder, several managers. Each
        // plans against what it sent itself; one overwriting the other would refetch both forever.
        val holder = Holder()
        val inbox = CursorSource("inbox")
        val probe = CursorSource("probe")
        val longForm = FilterShape(kinds = setOf(30023))

        holder.relayCursors.newEose(relay, listOf(shape(alice)), 1_000, inbox)
        holder.relayCursors.newEose(relay, listOf(longForm), 2_000, probe)

        assertEquals(listOf(CursorPart(shape(alice), 1_000)), holder.relayCursors.plan(relay, shape(alice), inbox))
        assertEquals(listOf(CursorPart(longForm, 2_000)), holder.relayCursors.plan(relay, longForm, probe))
        assertEquals(listOf(CursorPart(longForm, null)), holder.relayCursors.plan(relay, longForm, inbox), "another manager's answer is not this one's")

        holder.relayCursors.rewindTo(1_500)
        assertEquals(1_500L, holder.relayCursors.since(relay, probe))
        assertEquals(1_000L, holder.relayCursors.since(relay, inbox))
        holder.relayCursors.clear()
        assertNull(holder.relayCursors.since(relay))
    }

    @Test
    fun anAnswerPlannedForTheReplacedFeedIsNotRecordedOnTheNewOne() {
        // The old feed was caught up to 1,000: its REQ asked only what came after.
        val old = RelayCursors()
        old.newEose(relay, listOf(shape(alice)), 1_000)
        val sent = CursorPlanner.window(listOf(RelayBasedFilter(relay, notes(alice))), listOf(old))
        assertEquals(1_000L, sent.single().filter.since)

        // A switch away and back: a new, empty feed is served before that REQ's EOSE arrives.
        val replacement = RelayCursors()
        CursorPlanner.record(listOf(replacement), relay, sent.map { it.filter }, 2_000)
        assertNull(replacement.since(relay), "it holds nothing older than 1,000: the answer is not its own")

        // Its own plan (from scratch, or from its floor) is.
        val own = CursorPlanner.window(listOf(RelayBasedFilter(relay, notes(alice).copy(since = 500))), listOf(replacement))
        CursorPlanner.record(listOf(replacement), relay, own.map { it.filter }, 2_100)
        assertEquals(2_100L, replacement.since(relay))
    }

    @Test
    fun windowsPlannedInSeveralPassesAreAllTheHoldersOwn() {
        // The account metadata plans one relay twice (its lists, then the switcher avatars): the
        // second pass must not make the first pass's answers look like someone else's.
        val holder = RelayCursors()
        val lists = CursorPlanner.window(listOf(RelayBasedFilter(relay, notes(alice).copy(since = 700))), listOf(holder))
        val avatars = CursorPlanner.window(listOf(RelayBasedFilter(relay, Filter(kinds = listOf(0), authors = listOf(bob), since = 900))), listOf(holder))

        CursorPlanner.record(listOf(holder), relay, (lists + avatars).map { it.filter }, 2_000)

        assertEquals(listOf(CursorPart(shape(alice), 2_000)), holder.plan(relay, shape(alice)))
    }

    @Test
    fun anAnswerToAFilterAskingFromEarlierThanThePlanIsTheHoldersOwn() {
        // Cleared after an unmute, the feed plans from its floor (900), later than the REQ on the
        // wire (500). The client does not resend a REQ whose `since` only moved forward, so the
        // answers keep coming for the wire filter: they cover more than the plan and count.
        val cleared = RelayCursors()
        CursorPlanner.window(listOf(RelayBasedFilter(relay, notes(alice).copy(since = 900))), listOf(cleared))

        CursorPlanner.record(listOf(cleared), relay, listOf(notes(alice).copy(since = 500)), 2_000)

        assertEquals(2_000L, cleared.since(relay))
    }

    @Test
    fun eachManagerWritesUnderItsOwnName() {
        val holder = Holder()
        val key = Key("feed", holder)
        val first = Manager { setOf(key) }
        val second = Manager { setOf(key) }

        listOf(first, second).forEach { it.updateSubscriptions(setOf(key)) }
        val sub = first.findOrCreateSubFor(key)
        sub.listener.onEose(relay, sub.filters()?.get(relay))

        first.updateSubscriptions(setOf(key))
        second.updateSubscriptions(setOf(key))
        assertEquals(
            holder.relayCursors.since(relay),
            first
                .findOrCreateSubFor(key)
                .filters()
                ?.get(relay)
                ?.single()
                ?.since,
        )
        assertNull(
            second
                .findOrCreateSubFor(key)
                .filters()
                ?.get(relay)
                ?.single()
                ?.since,
            "the first manager's answer covers nothing the second asked",
        )
    }

    @Test
    fun clearingAHolderReplansTheManagersServingIt() {
        // An unmute widens what a feed accepts: its cursors are cleared, and a live REQ must ask
        // again, or relays never resend what is older than its window.
        val holder = Holder()
        var keys = setOf(Key("feed", holder))
        var replans = 0
        val manager =
            object : PerUniqueIdEoseManager<Key, String>(EmptyNostrClient(), { keys }) {
                override fun updateFilter(
                    key: Key,
                    since: SincePerRelayMap?,
                ) = listOf(RelayBasedFilter(relay, notes(alice)))

                override fun id(key: Key) = key.id

                override fun invalidateFilters(ignoreIfDoing: Boolean) {
                    replans++
                }
            }

        manager.updateSubscriptions(keys)
        val sub = manager.findOrCreateSubFor(keys.first())
        sub.listener.onEose(relay, sub.filters()?.get(relay))
        holder.relayCursors.clear()
        assertEquals(1, replans, "the serving manager plans again")

        holder.relayCursors.clear()
        assertEquals(1, replans, "nothing left to forget: no new REQ")

        keys = emptySet()
        manager.updateSubscriptions(keys)
        sub.listener.onEose(relay, listOf(notes(alice)))
        holder.relayCursors.newEose(relay, listOf(shape(alice)), 1_000)
        holder.relayCursors.clear()
        assertEquals(1, replans, "a manager no longer serving it is not told")
    }

    @Test
    fun aKeyWithoutHoldersIsAskedFromScratch() {
        val key = Key("feed", null)
        val manager = Manager { setOf(key) }

        manager.updateSubscriptions(setOf(key))
        val sub = manager.findOrCreateSubFor(key)
        sub.listener.onEose(relay, sub.filters()?.get(relay))
        manager.updateSubscriptions(setOf(key))

        // Nothing vouches for what is in memory, so no cursor is kept on its behalf.
        assertNull(manager.ownSince["feed"])
        assertNull(
            sub
                .filters()
                ?.get(relay)
                ?.single()
                ?.since,
        )
    }
}
