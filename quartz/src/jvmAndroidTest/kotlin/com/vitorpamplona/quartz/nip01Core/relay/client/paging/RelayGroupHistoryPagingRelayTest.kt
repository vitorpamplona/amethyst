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
package com.vitorpamplona.quartz.nip01Core.relay.client.paging

import com.vitorpamplona.geode.fixtures.SyntheticEvents
import com.vitorpamplona.geode.testing.RelayClientTest
import com.vitorpamplona.geode.testing.collectUntilEose
import com.vitorpamplona.geode.testing.preload
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip29RelayGroups.tags.GroupIdTag
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The **can't-miss-messages** property of the NIP-29 group-history pager, proven against the in-process
 * relay rather than a mocked filter. The `RelayGroupOpenChatHistorySubAssembler` walks a group's chat
 * backward with an `#h`-scoped `until`+`limit` REQ; this pins the two relay-side guarantees that whole
 * design rests on:
 *
 *  1. A backward `#h`+kind-9 walk over one group delivers **every** message **exactly once** and stops
 *     cleanly on an empty page — no gap, no re-download (the same contract as
 *     [UntilLimitPagingRelayTest], but with the group's `h`-tag on the wire).
 *  2. An `#h`-scoped walk **isolates one group from another on the same relay**, even when their
 *     `createdAt` ranges fully overlap — so paging one group can never surface, or be blocked by,
 *     another group's messages. This is the guarantee that a serving relay hosting many groups can't
 *     leak or hide messages across the `h`-tag boundary.
 */
class RelayGroupHistoryPagingRelayTest : RelayClientTest() {
    /** One group's kind-9 chat: distinct ids, monotonic createdAt from 1, all carrying `#h = groupId`. */
    private fun groupChat(
        idBase: Int,
        count: Int,
        groupId: String,
    ): List<Event> =
        List(count) { i ->
            SyntheticEvents.fakeEvent(
                idSeed = idBase + i,
                kind = ChatEvent.KIND,
                createdAt = (i + 1).toLong(),
                tags = arrayOf(arrayOf(GroupIdTag.TAG_NAME, groupId)),
            )
        }

    private fun hFilter(
        groupId: String,
        until: Long?,
    ) = Filter(
        kinds = listOf(ChatEvent.KIND),
        tags = mapOf(GroupIdTag.TAG_NAME to listOf(groupId)),
        until = until,
        limit = LIMIT,
    )

    @Test
    fun backwardHTagWalkCoversEveryGroupMessageOnceAndStopsOnEmptyPage() =
        runBlocking {
            defaultRelay.preload(groupChat(idBase = 1, count = TOTAL, groupId = "g1"))
            // A second group on the same relay must never leak into g1's walk.
            defaultRelay.preload(groupChat(idBase = 1_000_000, count = 77, groupId = "g2"))

            val seenIds = mutableSetOf<String>()
            var totalReceived = 0
            var pages = 0
            var until: Long? = null

            while (pages < SAFETY_CAP) {
                val (events, eose) = client.collectUntilEose(defaultRelayUrl, hFilter("g1", until))
                assertTrue(eose, "every page must end with EOSE")

                if (events.isEmpty()) break // gap-proof stop: empty page = nothing older

                pages++
                assertTrue(events.size <= LIMIT, "page must respect the limit")
                assertTrue(events.all { it.isTaggedGroup("g1") }, "an #h=g1 REQ must return only g1 messages")
                until?.let { cursor -> assertTrue(events.all { it.createdAt <= cursor }, "page must be older than the cursor") }

                events.forEach { e ->
                    seenIds.add(e.id)
                    totalReceived++
                }
                until = events.minOf { it.createdAt } - 1
            }

            assertEquals(TOTAL, totalReceived, "no message delivered twice across pages")
            assertEquals(TOTAL, seenIds.size, "every group message fetched exactly once")
            // 250 / 100 → 100 + 100 + 50, then an empty page stops the walk.
            assertEquals(3, pages)
        }

    @Test
    fun hTagScopedWalkIsolatesGroupsWithOverlappingTimeRangesOnTheSameRelay() =
        runBlocking {
            // Two groups on ONE relay, createdAt ranges deliberately OVERLAPPING (both start at 1) so the
            // only thing that can separate them is the #h tag, never time.
            defaultRelay.preload(groupChat(idBase = 1, count = 40, groupId = "gA"))
            defaultRelay.preload(groupChat(idBase = 1_000, count = 60, groupId = "gB"))

            val a = walkGroup("gA")
            val b = walkGroup("gB")

            assertEquals(40, a.size, "the #h=gA walk sees exactly gA's messages")
            assertEquals(60, b.size, "the #h=gB walk sees exactly gB's messages")
            assertTrue(a.all { it.isTaggedGroup("gA") }, "gA walk must not surface any gB message")
            assertTrue(b.all { it.isTaggedGroup("gB") }, "gB walk must not surface any gA message")
            assertTrue(a.map { it.id }.intersect(b.map { it.id }.toSet()).isEmpty(), "no message id belongs to both groups")
        }

    /** Full backward `#h`-scoped drain for one group; returns the distinct events it delivered. */
    private suspend fun walkGroup(groupId: String): List<Event> {
        val out = mutableMapOf<String, Event>()
        var until: Long? = null
        var pages = 0
        while (pages < SAFETY_CAP) {
            val (events, eose) = client.collectUntilEose(defaultRelayUrl, hFilter(groupId, until))
            assertTrue(eose)
            if (events.isEmpty()) break
            pages++
            events.forEach { out[it.id] = it }
            until = events.minOf { it.createdAt } - 1
        }
        return out.values.toList()
    }

    // --- Tier E1: the PRODUCTION cursor state machine, driven over a real relay ---
    //
    // The walks above use a hand-rolled until loop; these drive the actual `RelayLoadingCursors` — the
    // state machine `RelayGroupOpenChatHistorySubAssembler` runs in production — against the live in-process
    // relay, so a regression in the real advance/onEvent/onEose logic surfaces here, not just in the unit
    // tests that feed it synthetic callbacks (RelayLoadingCursorsTest / BackwardRelayPagerTest).

    /**
     * Steps the production [RelayLoadingCursors] backward over one group's `#h` chat until it reports done,
     * exactly as the history assembler does: advance → REQ at the requested `until` → feed each event and
     * the EOSE back in → advance again. Returns every id delivered.
     */
    private suspend fun driveCursorsToBottom(
        groupId: String,
        now: Long,
    ): Set<String> {
        val cursors = RelayLoadingCursors()
        cursors.floor = now
        val relay = defaultRelayUrl
        val seen = mutableSetOf<String>()
        cursors.advance(relay, start = now)
        var guard = 0
        while (!cursors.isDone(relay) && guard++ < SAFETY_CAP) {
            val (events, eose) = client.collectUntilEose(defaultRelayUrl, hFilter(groupId, cursors.requestedUntilFor(relay)))
            events.forEach {
                seen.add(it.id)
                cursors.onEvent(relay, it.createdAt)
            }
            if (eose) cursors.onEose(relay)
            cursors.advance(relay, start = now)
        }
        assertTrue(cursors.isDone(relay), "the production cursors must reach the bottom (empty page + EOSE)")
        return seen
    }

    @Test
    fun productionCursorsWalkOneGroupToTheBottomOverTheWire() =
        runBlocking {
            defaultRelay.preload(groupChat(idBase = 1, count = TOTAL, groupId = "g1"))
            defaultRelay.preload(groupChat(idBase = 1_000_000, count = 30, groupId = "g2")) // must not leak into g1's walk

            val seen = driveCursorsToBottom("g1", now = 10_000L)
            assertEquals(TOTAL, seen.size, "the production RelayLoadingCursors must fetch every g1 message exactly once")
        }

    @Test
    fun cursorsTreatAShortPageAsMoreToComeAndOnlyAnEmptyPageAsTheBottom() =
        runBlocking {
            // A group with fewer messages than the page limit returns a short first page — the relay capping
            // the response, NOT the bottom. Only a following empty page + EOSE ends the walk. This is the
            // property that stops a short page from silently truncating a group's history.
            defaultRelay.preload(groupChat(idBase = 1, count = 5, groupId = "g1"))
            val cursors = RelayLoadingCursors()
            val now = 10_000L
            cursors.floor = now
            val relay = defaultRelayUrl

            cursors.advance(relay, start = now)
            val (page1, eose1) = client.collectUntilEose(defaultRelayUrl, hFilter("g1", cursors.requestedUntilFor(relay)))
            page1.forEach { cursors.onEvent(relay, it.createdAt) }
            if (eose1) cursors.onEose(relay)
            assertEquals(5, page1.size)
            assertTrue(page1.size < LIMIT, "precondition: the first page is short")
            assertFalse(cursors.isDone(relay), "a short page must NOT be treated as exhaustion")

            cursors.advance(relay, start = now)
            val (page2, eose2) = client.collectUntilEose(defaultRelayUrl, hFilter("g1", cursors.requestedUntilFor(relay)))
            page2.forEach { cursors.onEvent(relay, it.createdAt) }
            if (eose2) cursors.onEose(relay)
            assertTrue(page2.isEmpty())
            assertTrue(cursors.isDone(relay), "an empty page + EOSE is the bottom")
        }

    private fun Event.isTaggedGroup(groupId: String) = tags.any { it.size >= 2 && it[0] == GroupIdTag.TAG_NAME && it[1] == groupId }

    companion object {
        private const val TOTAL = 250
        private const val LIMIT = 100
        private const val SAFETY_CAP = 10
    }
}
