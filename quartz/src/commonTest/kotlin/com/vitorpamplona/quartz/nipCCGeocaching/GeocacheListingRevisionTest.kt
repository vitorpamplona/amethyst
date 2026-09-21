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
package com.vitorpamplona.quartz.nipCCGeocaching

import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheListingEvent
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheListingRevision
import com.vitorpamplona.quartz.nipCCGeocaching.listing.tags.CacheType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The owner-side lifecycle: archiving a cache and locking in its first finder.
 *
 * Both republish at the same address, so the thing worth testing is not that the new tag is
 * present — it is that nothing else went missing. A real listing carries tags this library
 * models nothing for, and a revision that rebuilt from parsed fields would drop them silently.
 */
class GeocacheListingRevisionTest {
    private val owner = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val winner = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"

    /**
     * A listing carrying two tags Quartz has no model for: `client` and `expiration`. Both are
     * real — every Treasures listing on the network carries `client`, and Lightning Piggy stamps
     * `expiration` on all of them.
     */
    private fun listing(extra: List<Array<String>> = emptyList()): GeocacheListingEvent {
        val tags =
            (
                listOf(
                    arrayOf("d", "treasure-troll-trunk"),
                    arrayOf("name", "Treasure Troll's Trunk"),
                    arrayOf("g", "u4xsu6ryb"),
                    arrayOf("g", "u4x"),
                    arrayOf("D", "3"),
                    arrayOf("T", "2"),
                    arrayOf("S", "regular"),
                    arrayOf("t", "traditional"),
                    arrayOf("n", "first-to-find"),
                ) + extra
            ).toTypedArray()

        return GeocacheListingEvent("id", owner, 1_748_619_568L, tags, "A trunk in the woods", "sig")
    }

    @Test
    fun archivingAddsArchivedWithoutLosingTheCacheType() {
        val revision = GeocacheListingRevision.archived(listing(), createdAt = 1_748_619_600L)
        val reparsed = GeocacheListingEvent("id", owner, revision.createdAt, revision.tags, revision.content, "sig")

        assertTrue(reparsed.isArchived())
        // The spec puts `archived` alongside the type rather than replacing it. A traditional
        // cache that gets archived is still a traditional cache.
        assertEquals(CacheType.TRADITIONAL, reparsed.cacheType())
        assertEquals("Treasure Troll's Trunk", reparsed.cacheName())
        assertEquals(GeocacheListingEvent.KIND, revision.kind)
    }

    @Test
    fun archivingTwiceDoesNotDuplicateTheTag() {
        val once = GeocacheListingRevision.archived(listing())
        val onceEvent = GeocacheListingEvent("id", owner, once.createdAt, once.tags, once.content, "sig")
        val twice = GeocacheListingRevision.archived(onceEvent)

        assertEquals(1, twice.tags.count { it.size > 1 && it[0] == "t" && it[1] == "archived" })
    }

    @Test
    fun lockingInTheWinnerReplacesAnyEarlierClaim() {
        val earlier = listing(listOf(arrayOf("F", owner)))
        val revision = GeocacheListingRevision.withFirstToFindWinner(earlier, winner)
        val reparsed = GeocacheListingEvent("id", owner, revision.createdAt, revision.tags, revision.content, "sig")

        // `F` is authoritative and permanent; two winners is not a state the spec can answer.
        assertEquals(1, revision.tags.count { it.isNotEmpty() && it[0] == "F" })
        assertEquals(winner, reparsed.firstToFindWinner())
    }

    @Test
    fun aRevisionKeepsTagsQuartzDoesNotModel() {
        val withForeignTags =
            listing(
                listOf(
                    arrayOf("client", "Treasures"),
                    arrayOf("expiration", "1817364570"),
                ),
            )

        val revision = GeocacheListingRevision.archived(withForeignTags)

        // The whole reason these helpers copy the tag array instead of rebuilding from fields:
        // an owner must not lose data by pressing Archive in a client that models less than the
        // one they published from.
        assertTrue(revision.tags.any { it.size > 1 && it[0] == "client" && it[1] == "Treasures" })
        assertTrue(revision.tags.any { it.size > 1 && it[0] == "expiration" && it[1] == "1817364570" })
    }

    @Test
    fun aRevisionKeepsTheWholeGeohashLadder() {
        val revision = GeocacheListingRevision.archived(listing())
        val reparsed = GeocacheListingEvent("id", owner, revision.createdAt, revision.tags, revision.content, "sig")

        assertEquals(listOf("u4xsu6ryb", "u4x"), reparsed.geohashes())
    }

    @Test
    fun theAddressSurvivesSoTheRevisionReplacesRatherThanForks() {
        val original = listing()
        val revision = GeocacheListingRevision.archived(original)
        val reparsed = GeocacheListingEvent("id", owner, revision.createdAt, revision.tags, revision.content, "sig")

        assertEquals(original.address().toValue(), reparsed.address().toValue())
        assertNull(reparsed.firstToFindWinner())
    }
}
