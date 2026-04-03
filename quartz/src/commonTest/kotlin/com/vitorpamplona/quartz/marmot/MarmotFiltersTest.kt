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
package com.vitorpamplona.quartz.marmot

import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageEvent
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for Marmot relay subscription filter builders.
 */
class MarmotFiltersTest {
    private val testPubKey = "a".repeat(64)
    private val testGroupId = "b".repeat(64)
    private val testRef = "c".repeat(64)

    @Test
    fun testKeyPackagesByAuthor() {
        val filter = MarmotFilters.keyPackagesByAuthor(testPubKey)

        assertEquals(listOf(KeyPackageEvent.KIND), filter.kinds)
        assertEquals(listOf(testPubKey), filter.authors)
        assertNull(filter.tags)
        assertNull(filter.since)
    }

    @Test
    fun testKeyPackagesByAuthors() {
        val pubkeys = listOf("a".repeat(64), "b".repeat(64))
        val filter = MarmotFilters.keyPackagesByAuthors(pubkeys)

        assertEquals(listOf(KeyPackageEvent.KIND), filter.kinds)
        assertEquals(pubkeys, filter.authors)
    }

    @Test
    fun testKeyPackageByRef() {
        val filter = MarmotFilters.keyPackageByRef(testRef)

        assertEquals(listOf(KeyPackageEvent.KIND), filter.kinds)
        assertNull(filter.authors)
        val tags = filter.tags
        assertNotNull(tags)
        assertEquals(listOf(testRef), tags["i"])
    }

    @Test
    fun testGroupEventsByGroupId() {
        val filter = MarmotFilters.groupEventsByGroupId(testGroupId)

        assertEquals(listOf(GroupEvent.KIND), filter.kinds)
        val tags = filter.tags
        assertNotNull(tags)
        assertEquals(listOf(testGroupId), tags["h"])
        assertNull(filter.since)
    }

    @Test
    fun testGroupEventsByGroupIdSince() {
        val since = 1700000000L
        val filter = MarmotFilters.groupEventsByGroupIdSince(testGroupId, since)

        assertEquals(listOf(GroupEvent.KIND), filter.kinds)
        val tags = filter.tags
        assertNotNull(tags)
        assertEquals(listOf(testGroupId), tags["h"])
        assertEquals(since, filter.since)
    }

    @Test
    fun testGiftWrapsForUser() {
        val filter = MarmotFilters.giftWrapsForUser(testPubKey)

        assertEquals(listOf(GiftWrapEvent.KIND), filter.kinds)
        val tags = filter.tags
        assertNotNull(tags)
        assertEquals(listOf(testPubKey), tags["p"])
    }

    @Test
    fun testGiftWrapsForUserSince() {
        val since = 1700000000L
        val filter = MarmotFilters.giftWrapsForUserSince(testPubKey, since)

        assertEquals(listOf(GiftWrapEvent.KIND), filter.kinds)
        val tags = filter.tags
        assertNotNull(tags)
        assertEquals(listOf(testPubKey), tags["p"])
        assertEquals(since, filter.since)
    }

    @Test
    fun testKeyPackagesMigration() {
        val filter = MarmotFilters.keyPackagesMigration(testPubKey)

        val kinds = filter.kinds
        assertNotNull(kinds)
        assertTrue(kinds.contains(KeyPackageEvent.KIND))
        assertTrue(kinds.contains(443))
        assertEquals(listOf(testPubKey), filter.authors)
    }

    @Test
    fun testFiltersAreNotEmpty() {
        // None of the filter builders should produce empty filters
        val filters =
            listOf(
                MarmotFilters.keyPackagesByAuthor(testPubKey),
                MarmotFilters.keyPackageByRef(testRef),
                MarmotFilters.groupEventsByGroupId(testGroupId),
                MarmotFilters.giftWrapsForUser(testPubKey),
                MarmotFilters.keyPackagesMigration(testPubKey),
            )

        filters.forEach { filter ->
            assertTrue(!filter.isEmpty(), "Filter should not be empty: $filter")
        }
    }
}
