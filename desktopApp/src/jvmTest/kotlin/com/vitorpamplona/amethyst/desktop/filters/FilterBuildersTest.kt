/**
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
package com.vitorpamplona.amethyst.desktop.filters

import com.vitorpamplona.amethyst.desktop.subscriptions.FilterBuilders
import com.vitorpamplona.amethyst.desktop.subscriptions.buildFilter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FilterBuildersTest {
    private val testPubKey = "0000000000000000000000000000000000000000000000000000000000000001"
    private val testPubKey2 = "0000000000000000000000000000000000000000000000000000000000000002"
    private val testEventId = "1111111111111111111111111111111111111111111111111111111111111111"

    @Test
    fun testTextNotesGlobal() {
        val filter = FilterBuilders.textNotesGlobal(limit = 50)

        assertEquals(listOf(1), filter.kinds)
        assertEquals(50, filter.limit)
        assertNull(filter.authors)
        assertNull(filter.tags)
        assertNull(filter.since)
        assertNull(filter.until)
    }

    @Test
    fun testTextNotesGlobalWithTimeRange() {
        val since = 1609459200L // 2021-01-01
        val until = 1640995200L // 2022-01-01
        val filter = FilterBuilders.textNotesGlobal(limit = 100, since = since, until = until)

        assertEquals(listOf(1), filter.kinds)
        assertEquals(100, filter.limit)
        assertEquals(since, filter.since)
        assertEquals(until, filter.until)
    }

    @Test
    fun testTextNotesFromAuthors() {
        val authors = listOf(testPubKey, testPubKey2)
        val filter = FilterBuilders.textNotesFromAuthors(authors, limit = 25)

        assertEquals(listOf(1), filter.kinds)
        assertEquals(authors, filter.authors)
        assertEquals(25, filter.limit)
        assertNull(filter.tags)
    }

    @Test
    fun testTextNotesFromAuthorsWithTimeRange() {
        val authors = listOf(testPubKey)
        val since = 1609459200L
        val filter = FilterBuilders.textNotesFromAuthors(authors, limit = 10, since = since)

        assertEquals(listOf(1), filter.kinds)
        assertEquals(authors, filter.authors)
        assertEquals(10, filter.limit)
        assertEquals(since, filter.since)
    }

    @Test
    fun testUserMetadata() {
        val filter = FilterBuilders.userMetadata(testPubKey)

        assertEquals(listOf(0), filter.kinds)
        assertEquals(listOf(testPubKey), filter.authors)
        assertEquals(1, filter.limit)
    }

    @Test
    fun testContactList() {
        val filter = FilterBuilders.contactList(testPubKey)

        assertEquals(listOf(3), filter.kinds)
        assertEquals(listOf(testPubKey), filter.authors)
        assertEquals(1, filter.limit)
    }

    @Test
    fun testNotificationsForUser() {
        val filter = FilterBuilders.notificationsForUser(testPubKey, limit = 100)

        assertEquals(listOf(1, 7, 6, 16, 9735), filter.kinds)
        assertNotNull(filter.tags)
        assertEquals(listOf(testPubKey), filter.tags!!["p"])
        assertEquals(100, filter.limit)
    }

    @Test
    fun testNotificationsForUserWithSince() {
        val since = 1609459200L
        val filter = FilterBuilders.notificationsForUser(testPubKey, limit = 50, since = since)

        assertEquals(listOf(1, 7, 6, 16, 9735), filter.kinds)
        assertNotNull(filter.tags)
        assertEquals(listOf(testPubKey), filter.tags!!["p"])
        assertEquals(50, filter.limit)
        assertEquals(since, filter.since)
    }

    @Test
    fun testByKinds() {
        val kinds = listOf(1, 7, 6)
        val filter = FilterBuilders.byKinds(kinds, limit = 20)

        assertEquals(kinds, filter.kinds)
        assertEquals(20, filter.limit)
        assertNull(filter.authors)
        assertNull(filter.tags)
    }

    @Test
    fun testByKindsWithTimeRange() {
        val kinds = listOf(30023) // Long-form content
        val since = 1609459200L
        val until = 1640995200L
        val filter = FilterBuilders.byKinds(kinds, limit = 5, since = since, until = until)

        assertEquals(kinds, filter.kinds)
        assertEquals(5, filter.limit)
        assertEquals(since, filter.since)
        assertEquals(until, filter.until)
    }

    @Test
    fun testByAuthors() {
        val authors = listOf(testPubKey, testPubKey2)
        val filter = FilterBuilders.byAuthors(authors, limit = 30)

        assertEquals(authors, filter.authors)
        assertEquals(30, filter.limit)
        assertNull(filter.kinds)
    }

    @Test
    fun testByAuthorsWithKinds() {
        val authors = listOf(testPubKey)
        val kinds = listOf(1, 30023)
        val filter = FilterBuilders.byAuthors(authors, kinds = kinds, limit = 15)

        assertEquals(authors, filter.authors)
        assertEquals(kinds, filter.kinds)
        assertEquals(15, filter.limit)
    }

    @Test
    fun testByIds() {
        val ids = listOf(testEventId)
        val filter = FilterBuilders.byIds(ids)

        assertEquals(ids, filter.ids)
        assertNull(filter.kinds)
        assertNull(filter.authors)
        assertNull(filter.limit)
    }

    @Test
    fun testByPTags() {
        val pubKeys = listOf(testPubKey, testPubKey2)
        val filter = FilterBuilders.byPTags(pubKeys, limit = 40)

        assertNotNull(filter.tags)
        assertEquals(pubKeys, filter.tags!!["p"])
        assertEquals(40, filter.limit)
        assertNull(filter.kinds)
    }

    @Test
    fun testByPTagsWithKinds() {
        val pubKeys = listOf(testPubKey)
        val kinds = listOf(7) // Reactions
        val filter = FilterBuilders.byPTags(pubKeys, kinds = kinds, limit = 25)

        assertNotNull(filter.tags)
        assertEquals(pubKeys, filter.tags!!["p"])
        assertEquals(kinds, filter.kinds)
        assertEquals(25, filter.limit)
    }

    @Test
    fun testByETags() {
        val eventIds = listOf(testEventId)
        val filter = FilterBuilders.byETags(eventIds, limit = 10)

        assertNotNull(filter.tags)
        assertEquals(eventIds, filter.tags!!["e"])
        assertEquals(10, filter.limit)
        assertNull(filter.kinds)
    }

    @Test
    fun testByETagsWithKinds() {
        val eventIds = listOf(testEventId)
        val kinds = listOf(1, 7) // Text notes and reactions
        val filter = FilterBuilders.byETags(eventIds, kinds = kinds, limit = 20)

        assertNotNull(filter.tags)
        assertEquals(eventIds, filter.tags!!["e"])
        assertEquals(kinds, filter.kinds)
        assertEquals(20, filter.limit)
    }

    @Test
    fun testByTags() {
        val tags = mapOf("p" to listOf(testPubKey), "t" to listOf("bitcoin", "nostr"))
        val filter = FilterBuilders.byTags(tags, limit = 15)

        assertEquals(tags, filter.tags)
        assertEquals(15, filter.limit)
        assertNull(filter.kinds)
    }

    @Test
    fun testByTagsWithKinds() {
        val tags = mapOf("t" to listOf("bitcoin"))
        val kinds = listOf(1)
        val filter = FilterBuilders.byTags(tags, kinds = kinds, limit = 50)

        assertEquals(tags, filter.tags)
        assertEquals(kinds, filter.kinds)
        assertEquals(50, filter.limit)
    }

    // DSL Builder Tests

    @Test
    fun testBuildFilterWithKinds() {
        val filter =
            buildFilter {
                kinds(1, 7)
                limit(50)
            }

        assertEquals(listOf(1, 7), filter.kinds)
        assertEquals(50, filter.limit)
    }

    @Test
    fun testBuildFilterWithAuthors() {
        val filter =
            buildFilter {
                authors(testPubKey, testPubKey2)
                limit(25)
            }

        assertEquals(listOf(testPubKey, testPubKey2), filter.authors)
        assertEquals(25, filter.limit)
    }

    @Test
    fun testBuildFilterWithPTag() {
        val filter =
            buildFilter {
                kinds(7)
                pTag(testPubKey)
                limit(100)
            }

        assertEquals(listOf(7), filter.kinds)
        assertNotNull(filter.tags)
        assertEquals(listOf(testPubKey), filter.tags!!["p"])
        assertEquals(100, filter.limit)
    }

    @Test
    fun testBuildFilterWithETag() {
        val filter =
            buildFilter {
                kinds(1)
                eTag(testEventId)
                limit(10)
            }

        assertEquals(listOf(1), filter.kinds)
        assertNotNull(filter.tags)
        assertEquals(listOf(testEventId), filter.tags!!["e"])
        assertEquals(10, filter.limit)
    }

    @Test
    fun testBuildFilterWithCustomTag() {
        val filter =
            buildFilter {
                kinds(1)
                tag("t", listOf("bitcoin", "nostr"))
                limit(20)
            }

        assertEquals(listOf(1), filter.kinds)
        assertNotNull(filter.tags)
        assertEquals(listOf("bitcoin", "nostr"), filter.tags!!["t"])
        assertEquals(20, filter.limit)
    }

    @Test
    fun testBuildFilterWithTimeRange() {
        val since = 1609459200L
        val until = 1640995200L
        val filter =
            buildFilter {
                kinds(1)
                since(since)
                until(until)
                limit(30)
            }

        assertEquals(listOf(1), filter.kinds)
        assertEquals(since, filter.since)
        assertEquals(until, filter.until)
        assertEquals(30, filter.limit)
    }

    @Test
    fun testBuildFilterComplex() {
        val since = 1609459200L
        val filter =
            buildFilter {
                kinds(1, 7, 6)
                authors(testPubKey)
                pTag(testPubKey2)
                since(since)
                limit(50)
            }

        assertEquals(listOf(1, 7, 6), filter.kinds)
        assertEquals(listOf(testPubKey), filter.authors)
        assertNotNull(filter.tags)
        assertEquals(listOf(testPubKey2), filter.tags!!["p"])
        assertEquals(since, filter.since)
        assertEquals(50, filter.limit)
    }

    @Test
    fun testBuildFilterWithSearch() {
        val filter =
            buildFilter {
                kinds(1)
                search("bitcoin")
                limit(10)
            }

        assertEquals(listOf(1), filter.kinds)
        assertEquals("bitcoin", filter.search)
        assertEquals(10, filter.limit)
    }

    @Test
    fun testBuildFilterWithIds() {
        val filter =
            buildFilter {
                ids(testEventId)
            }

        assertEquals(listOf(testEventId), filter.ids)
        assertNull(filter.kinds)
    }

    @Test
    fun testBuildFilterWithIdsList() {
        val ids = listOf(testEventId, "2222222222222222222222222222222222222222222222222222222222222222")
        val filter =
            buildFilter {
                ids(ids)
            }

        assertEquals(ids, filter.ids)
    }

    @Test
    fun testBuildFilterWithAuthorsList() {
        val authors = listOf(testPubKey, testPubKey2)
        val filter =
            buildFilter {
                authors(authors)
                limit(15)
            }

        assertEquals(authors, filter.authors)
        assertEquals(15, filter.limit)
    }

    @Test
    fun testBuildFilterWithKindsList() {
        val kinds = listOf(1, 7, 6, 16, 9735)
        val filter =
            buildFilter {
                kinds(kinds)
                limit(100)
            }

        assertEquals(kinds, filter.kinds)
        assertEquals(100, filter.limit)
    }

    // Integration Tests - Real-world scenarios

    @Test
    fun testGlobalFeedScenario() {
        val filter = FilterBuilders.textNotesGlobal(limit = 50)

        assertTrue(!filter.isEmpty())
        assertEquals(listOf(1), filter.kinds)
        assertEquals(50, filter.limit)
    }

    @Test
    fun testFollowingFeedScenario() {
        val followedUsers = listOf(testPubKey, testPubKey2)
        val filter = FilterBuilders.textNotesFromAuthors(followedUsers, limit = 50)

        assertTrue(!filter.isEmpty())
        assertEquals(listOf(1), filter.kinds)
        assertEquals(followedUsers, filter.authors)
        assertEquals(50, filter.limit)
    }

    @Test
    fun testUserProfileScenario() {
        val metadataFilter = FilterBuilders.userMetadata(testPubKey)
        val postsFilter = FilterBuilders.textNotesFromAuthors(listOf(testPubKey), limit = 50)
        val contactListFilter = FilterBuilders.contactList(testPubKey)

        assertTrue(!metadataFilter.isEmpty())
        assertTrue(!postsFilter.isEmpty())
        assertTrue(!contactListFilter.isEmpty())

        assertEquals(listOf(0), metadataFilter.kinds)
        assertEquals(listOf(1), postsFilter.kinds)
        assertEquals(listOf(3), contactListFilter.kinds)
    }

    @Test
    fun testNotificationsScenario() {
        val filter = FilterBuilders.notificationsForUser(testPubKey, limit = 100)

        assertTrue(!filter.isEmpty())
        assertEquals(listOf(1, 7, 6, 16, 9735), filter.kinds)
        assertNotNull(filter.tags)
        assertEquals(listOf(testPubKey), filter.tags!!["p"])
        assertEquals(100, filter.limit)
    }

    @Test
    fun testThreadViewScenario() {
        // Getting replies to a specific event
        val filter =
            buildFilter {
                kinds(1)
                eTag(testEventId)
                limit(100)
            }

        assertTrue(!filter.isEmpty())
        assertEquals(listOf(1), filter.kinds)
        assertNotNull(filter.tags)
        assertEquals(listOf(testEventId), filter.tags!!["e"])
    }

    @Test
    fun testReactionsToEventScenario() {
        val filter =
            buildFilter {
                kinds(7) // Reactions
                eTag(testEventId)
                limit(50)
            }

        assertTrue(!filter.isEmpty())
        assertEquals(listOf(7), filter.kinds)
        assertNotNull(filter.tags)
        assertEquals(listOf(testEventId), filter.tags!!["e"])
    }

    @Test
    fun testHashtagFeedScenario() {
        val filter =
            buildFilter {
                kinds(1)
                tag("t", listOf("bitcoin"))
                limit(50)
            }

        assertTrue(!filter.isEmpty())
        assertEquals(listOf(1), filter.kinds)
        assertNotNull(filter.tags)
        assertEquals(listOf("bitcoin"), filter.tags!!["t"])
    }
}
