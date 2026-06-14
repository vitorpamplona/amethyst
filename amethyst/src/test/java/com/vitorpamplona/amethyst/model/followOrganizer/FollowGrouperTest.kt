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
package com.vitorpamplona.amethyst.model.followOrganizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowGrouperTest {
    private val day = 24L * 60L * 60L
    private val now = 1_000_000_000L

    private fun stat(
        key: String,
        ageDays: Long? = null,
        kinds: Map<Int, Int> = emptyMap(),
        tags: Map<String, Int> = emptyMap(),
    ) = FollowStats(
        pubkey = key,
        lastSeen = ageDays?.let { now - it * day },
        totalEvents = kinds.values.sum(),
        kindCounts = kinds,
        hashtagCounts = tags,
    )

    @Test
    fun lastSeenBucketsByRecency() {
        val stats =
            listOf(
                stat("active", ageDays = 2),
                stat("recent", ageDays = 20),
                stat("slowing", ageDays = 60),
                stat("dormant", ageDays = 200),
                stat("silent", ageDays = 500),
                stat("nodata", ageDays = null),
            )

        val groups = FollowGrouper.byLastSeen(stats, now)
        val byMember = groups.associateBy({ it.members.single() }, { it.title })

        assertEquals("Active (last 7 days)", byMember["active"])
        assertEquals("Recent (last 30 days)", byMember["recent"])
        assertEquals("Slowing down (last 90 days)", byMember["slowing"])
        assertEquals("Dormant (last year)", byMember["dormant"])
        assertEquals("Silent (over a year)", byMember["silent"])
        assertEquals("No recent posts cached", byMember["nodata"])
    }

    @Test
    fun lastSeenDropsEmptyBuckets() {
        val groups = FollowGrouper.byLastSeen(listOf(stat("a", ageDays = 1)), now)
        assertEquals(1, groups.size)
        assertEquals("Active (last 7 days)", groups.single().title)
    }

    @Test
    fun contentTypePicksDominantKind() {
        val stats =
            listOf(
                stat("writer", kinds = mapOf(30023 to 5, 1 to 1)),
                stat("noteguy", kinds = mapOf(1 to 10)),
                stat("photog", kinds = mapOf(20 to 4)),
                stat("reposter", kinds = mapOf(6 to 9, 7 to 30)),
            )

        val groups = FollowGrouper.byContentType(stats)
        val byMember = groups.flatMap { g -> g.members.map { it to g.title } }.toMap()

        assertEquals("Long-form writers", byMember["writer"])
        assertEquals("Note posters", byMember["noteguy"])
        assertEquals("Photographers", byMember["photog"])
        assertEquals("Reposters & no cached posts", byMember["reposter"])
    }

    @Test
    fun topicsKeepsOnlySharedTags() {
        val stats =
            listOf(
                stat("a", tags = mapOf("nostr" to 10)),
                stat("b", tags = mapOf("nostr" to 5)),
                stat("c", tags = mapOf("nostr" to 3)),
                stat("d", tags = mapOf("bitcoin" to 8)),
                stat("e", tags = emptyMap()),
            )

        val groups = FollowGrouper.byTopics(stats, minGroupSize = 3)

        assertEquals(1, groups.size)
        assertEquals("#nostr", groups.single().title)
        assertEquals(setOf("a", "b", "c"), groups.single().members.toSet())
    }

    @Test
    fun topicsAssignsEachFollowToTheirTopTag() {
        val stats =
            listOf(
                stat("a", tags = mapOf("art" to 9, "music" to 1)),
                stat("b", tags = mapOf("art" to 4)),
                stat("c", tags = mapOf("art" to 2)),
            )

        val groups = FollowGrouper.byTopics(stats, minGroupSize = 3)
        assertEquals(1, groups.size)
        assertTrue(groups.single().members.containsAll(listOf("a", "b", "c")))
    }
}
