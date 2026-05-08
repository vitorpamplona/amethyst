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
package com.vitorpamplona.amethyst.commons.feeds.custom

import com.vitorpamplona.amethyst.commons.search.SearchQuery
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchQueryToFeedTest {
    @Test
    fun convertsHashtagsToFeedFilter() {
        val query =
            SearchQuery(
                hashtags = persistentListOf("bitcoin", "nostr"),
            )

        val feed = query.toFeedDefinition(name = "BTC + Nostr", emoji = "\u20BF")

        assertEquals("BTC + Nostr", feed.name)
        assertEquals("\u20BF", feed.emoji)
        val source = feed.source as FeedSource.Filter
        assertEquals(listOf("bitcoin", "nostr"), source.hashtags)
    }

    @Test
    fun convertsAuthorsToFeedFilter() {
        val query =
            SearchQuery(
                authors = persistentListOf("abc123", "def456"),
            )

        val feed = query.toFeedDefinition(name = "Devs")
        val source = feed.source as FeedSource.Filter
        assertEquals(listOf("abc123", "def456"), source.authors)
    }

    @Test
    fun convertsExcludeTerms() {
        val query =
            SearchQuery(
                hashtags = persistentListOf("bitcoin"),
                excludeTerms = persistentListOf("scam", "spam"),
            )

        val feed = query.toFeedDefinition(name = "Clean BTC")
        val source = feed.source as FeedSource.Filter
        assertEquals(listOf("scam", "spam"), source.excludeKeywords)
    }

    @Test
    fun convertsKinds() {
        val query =
            SearchQuery(
                kinds = persistentListOf(1, 30023),
                hashtags = persistentListOf("dev"),
            )

        val feed = query.toFeedDefinition(name = "Dev Articles")
        val source = feed.source as FeedSource.Filter
        assertEquals(listOf(1, 30023), source.kinds)
    }

    @Test
    fun canBecomeFeedWithHashtags() {
        assertTrue(SearchQuery(hashtags = persistentListOf("btc")).canBecomeFeed())
    }

    @Test
    fun canBecomeFeedWithAuthors() {
        assertTrue(SearchQuery(authors = persistentListOf("abc")).canBecomeFeed())
    }

    @Test
    fun canNotBecomeFeedEmpty() {
        assertFalse(SearchQuery.EMPTY.canBecomeFeed())
    }

    @Test
    fun canNotBecomeFeedTextOnly() {
        assertFalse(SearchQuery(text = "hello").canBecomeFeed())
    }
}
