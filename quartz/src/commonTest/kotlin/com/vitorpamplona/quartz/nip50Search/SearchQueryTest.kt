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
package com.vitorpamplona.quartz.nip50Search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchQueryTest {
    @Test
    fun plainTextHasNoExtensions() {
        val q = SearchQuery.parse("best nostr apps")
        assertEquals("best nostr apps", q.terms)
        assertTrue(q.extensions.isEmpty())
        assertNull(q.domain)
        assertNull(q.language)
        assertNull(q.sentiment)
        assertNull(q.nsfw)
        // NIP-50 default: nsfw included unless explicitly excluded.
        assertTrue(q.nsfwIncluded)
    }

    @Test
    fun nullAndBlankYieldEmpty() {
        assertEquals(SearchQuery.EMPTY, SearchQuery.parse(null))
        assertTrue(SearchQuery.parse("   ").terms.isEmpty())
        assertTrue(SearchQuery.parse("   ").extensions.isEmpty())
    }

    @Test
    fun extractsKnownExtensions() {
        val q = SearchQuery.parse("bitcoin domain:example.com language:en")
        assertEquals("bitcoin", q.terms)
        assertEquals("example.com", q.domain)
        assertEquals("en", q.language)
    }

    @Test
    fun includeSpamFlag() {
        val q = SearchQuery.parse("memes include:spam")
        assertEquals("memes", q.terms)
        assertTrue(q.includeSpam)

        assertFalse(SearchQuery.parse("memes").includeSpam)
    }

    @Test
    fun sentimentParsing() {
        assertEquals(Sentiment.NEGATIVE, SearchQuery.parse("vurtnec sentiment:negative").sentiment)
        assertEquals(Sentiment.NEUTRAL, SearchQuery.parse("x sentiment:neutral").sentiment)
        assertEquals(Sentiment.POSITIVE, SearchQuery.parse("x sentiment:positive").sentiment)
        // Unrecognized sentiment value -> null, token still captured raw.
        val bad = SearchQuery.parse("x sentiment:angry")
        assertNull(bad.sentiment)
        assertEquals("angry", bad.extension("sentiment"))
    }

    @Test
    fun nsfwBooleanParsing() {
        assertEquals(false, SearchQuery.parse("x nsfw:false").nsfw)
        assertFalse(SearchQuery.parse("x nsfw:false").nsfwIncluded)
        assertEquals(true, SearchQuery.parse("x nsfw:true").nsfw)
        assertTrue(SearchQuery.parse("x nsfw:true").nsfwIncluded)
        // Non-boolean value is not coerced.
        assertNull(SearchQuery.parse("x nsfw:maybe").nsfw)
    }

    @Test
    fun unknownExtensionsArePreserved() {
        val q = SearchQuery.parse("hello foo:bar")
        assertEquals("hello", q.terms)
        assertEquals("bar", q.extension("foo"))
    }

    @Test
    fun urlsStayInTerms() {
        // "https://example.com" must not be parsed as a `https:` extension.
        val q = SearchQuery.parse("check https://example.com out")
        assertEquals("check https://example.com out", q.terms)
        assertTrue(q.extensions.isEmpty())
    }

    @Test
    fun uppercaseKeyIsNotAnExtension() {
        val q = SearchQuery.parse("NASA:cool stuff")
        assertEquals("NASA:cool stuff", q.terms)
        assertTrue(q.extensions.isEmpty())
    }

    @Test
    fun emptyKeyOrValueStaysInTerms() {
        assertEquals(":value", SearchQuery.parse(":value").terms)
        assertEquals("key:", SearchQuery.parse("key:").terms)
    }

    @Test
    fun extensionsOnlyHasEmptyTerms() {
        val q = SearchQuery.parse("domain:example.com")
        assertTrue(q.isTermsEmpty())
        assertEquals("example.com", q.domain)
    }

    @Test
    fun duplicateKeyKeepsLast() {
        val q = SearchQuery.parse("x language:en language:pt")
        assertEquals("pt", q.language)
    }

    @Test
    fun extensionsCanInterleaveWithTerms() {
        val q = SearchQuery.parse("best domain:example.com nostr apps")
        assertEquals("best nostr apps", q.terms)
        assertEquals("example.com", q.domain)
    }

    @Test
    fun toSearchStringRoundTrips() {
        val q = SearchQuery.parse("best nostr apps domain:example.com language:en")
        assertEquals("best nostr apps domain:example.com language:en", q.toSearchString())
    }

    @Test
    fun toSearchStringWithExtensionsOnly() {
        val q = SearchQuery.parse("nsfw:false")
        assertEquals("nsfw:false", q.toSearchString())
    }
}
