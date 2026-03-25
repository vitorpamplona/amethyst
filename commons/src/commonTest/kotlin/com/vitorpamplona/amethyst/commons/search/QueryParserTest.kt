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
package com.vitorpamplona.amethyst.commons.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QueryParserTest {
    @Test
    fun emptyInput() {
        val q = QueryParser.parse("")
        assertTrue(q.isEmpty)
        assertEquals(SearchQuery.EMPTY, q)
    }

    @Test
    fun whitespaceOnly() {
        val q = QueryParser.parse("   ")
        assertTrue(q.isEmpty)
    }

    @Test
    fun plainText() {
        val q = QueryParser.parse("bitcoin lightning")
        assertEquals("bitcoin lightning", q.text)
        assertTrue(q.authors.isEmpty())
        assertTrue(q.kinds.isEmpty())
    }

    @Test
    fun kindOperatorAlias() {
        val q = QueryParser.parse("kind:note")
        assertEquals(listOf(1), q.kinds.toList())
        assertTrue(q.text.isBlank())
    }

    @Test
    fun kindOperatorNumeric() {
        val q = QueryParser.parse("kind:30023")
        assertEquals(listOf(30023), q.kinds.toList())
    }

    @Test
    fun kindOperatorArticle() {
        val q = QueryParser.parse("kind:article")
        assertEquals(listOf(30023), q.kinds.toList())
    }

    @Test
    fun kindOperatorInvalid() {
        val q = QueryParser.parse("kind:invalid")
        // Unresolvable kind → treated as text
        assertEquals("kind:invalid", q.text)
        assertTrue(q.kinds.isEmpty())
    }

    @Test
    fun pseudoKindReply() {
        val q = QueryParser.parse("kind:reply")
        assertTrue(q.kinds.isEmpty())
        assertEquals(listOf("reply"), q.pseudoKinds.toList())
    }

    @Test
    fun pseudoKindMedia() {
        val q = QueryParser.parse("kind:media")
        assertTrue(q.kinds.isEmpty())
        assertEquals(listOf("media"), q.pseudoKinds.toList())
    }

    @Test
    fun multipleKinds() {
        val q = QueryParser.parse("kind:note kind:article")
        assertEquals(listOf(1, 30023), q.kinds.toList())
    }

    @Test
    fun sinceDate() {
        val q = QueryParser.parse("since:2025-01-01")
        // 2025-01-01 00:00:00 UTC
        assertEquals(1735689600L, q.since)
    }

    @Test
    fun sinceDateYearOnly() {
        val q = QueryParser.parse("since:2025")
        // 2025-01-01 00:00:00 UTC
        assertEquals(1735689600L, q.since)
    }

    @Test
    fun sinceDateYearMonth() {
        val q = QueryParser.parse("since:2025-06")
        // 2025-06-01 00:00:00 UTC
        val q2 = QueryParser.parse("since:2025-06-01")
        assertEquals(q2.since, q.since)
    }

    @Test
    fun sinceInvalidDate() {
        val q = QueryParser.parse("since:not-a-date")
        assertNull(q.since)
        assertEquals("since:not-a-date", q.text)
    }

    @Test
    fun untilDate() {
        val q = QueryParser.parse("until:2025-12-31")
        assertNull(q.since)
        assertTrue(q.until != null && q.until > 0)
    }

    @Test
    fun hashtag() {
        val q = QueryParser.parse("#bitcoin")
        assertEquals(listOf("bitcoin"), q.hashtags.toList())
        assertTrue(q.text.isBlank())
    }

    @Test
    fun multipleHashtags() {
        val q = QueryParser.parse("#bitcoin #nostr")
        assertEquals(listOf("bitcoin", "nostr"), q.hashtags.toList())
    }

    @Test
    fun negationTerm() {
        val q = QueryParser.parse("-spam")
        assertEquals(listOf("spam"), q.excludeTerms.toList())
        assertTrue(q.text.isBlank())
    }

    @Test
    fun multipleNegations() {
        val q = QueryParser.parse("-spam -scam")
        assertEquals(listOf("spam", "scam"), q.excludeTerms.toList())
    }

    @Test
    fun quotedPhrase() {
        val q = QueryParser.parse("\"exact phrase\"")
        assertEquals("\"exact phrase\"", q.text)
    }

    @Test
    fun orTerms() {
        val q = QueryParser.parse("bitcoin OR lightning")
        assertEquals(listOf("bitcoin", "lightning"), q.orTerms.toList())
        assertTrue(q.text.isBlank())
    }

    @Test
    fun orTermsWithOperators() {
        val q = QueryParser.parse("from:vitor bitcoin OR lightning kind:note")
        assertEquals(listOf("bitcoin", "lightning"), q.orTerms.toList())
        assertEquals(listOf(1), q.kinds.toList())
        // from:vitor → authorNames since it's not a valid npub
        assertEquals(listOf("vitor"), q.authorNames.toList())
    }

    @Test
    fun orTermsCappedAtThree() {
        val q = QueryParser.parse("a OR b OR c OR d OR e")
        assertEquals(3, q.orTerms.size)
        assertEquals(listOf("a", "b", "c"), q.orTerms.toList())
    }

    @Test
    fun orphanedOr() {
        val q = QueryParser.parse("OR")
        assertEquals("OR", q.text)
        assertTrue(q.orTerms.isEmpty())
    }

    @Test
    fun languageOperator() {
        val q = QueryParser.parse("lang:en bitcoin")
        assertEquals("en", q.language)
        assertEquals("bitcoin", q.text)
    }

    @Test
    fun domainOperator() {
        val q = QueryParser.parse("domain:nostr.com bitcoin")
        assertEquals("nostr.com", q.domain)
        assertEquals("bitcoin", q.text)
    }

    @Test
    fun combinedQuery() {
        val q = QueryParser.parse("kind:note since:2025-01-01 #bitcoin -spam lightning")
        assertEquals(listOf(1), q.kinds.toList())
        assertEquals(1735689600L, q.since)
        assertEquals(listOf("bitcoin"), q.hashtags.toList())
        assertEquals(listOf("spam"), q.excludeTerms.toList())
        assertEquals("lightning", q.text)
    }

    @Test
    fun caseInsensitiveOperators() {
        val q = QueryParser.parse("FROM:vitor KIND:Note")
        assertEquals(listOf("vitor"), q.authorNames.toList())
        assertEquals(listOf(1), q.kinds.toList())
    }

    @Test
    fun operatorWithNoValue() {
        val q = QueryParser.parse("from:")
        // Malformed → treated as text
        assertEquals("from:", q.text)
        assertTrue(q.authors.isEmpty())
    }

    @Test
    fun fromWithAuthorName() {
        val q = QueryParser.parse("from:vitor")
        assertEquals(listOf("vitor"), q.authorNames.toList())
        assertTrue(q.authors.isEmpty())
    }

    @Test
    fun multipleFromAuthors() {
        val q = QueryParser.parse("from:alice from:bob")
        assertEquals(listOf("alice", "bob"), q.authorNames.toList())
    }

    @Test
    fun duplicateAuthorsDeduped() {
        val q = QueryParser.parse("from:vitor from:vitor")
        assertEquals(1, q.authorNames.size)
    }

    @Test
    fun parseDateToTimestamp_validDates() {
        // 1970-01-01 = 0
        assertEquals(0L, QueryParser.parseDateToTimestamp("1970-01-01"))
        // 2000-01-01
        assertEquals(946684800L, QueryParser.parseDateToTimestamp("2000-01-01"))
    }

    @Test
    fun parseDateToTimestamp_invalidDates() {
        assertNull(QueryParser.parseDateToTimestamp("not-a-date"))
        assertNull(QueryParser.parseDateToTimestamp("1800-01-01"))
        assertNull(QueryParser.parseDateToTimestamp("2025-13-01"))
        assertNull(QueryParser.parseDateToTimestamp("2025-01-32"))
    }

    @Test
    fun unicodeInFreeText() {
        val q = QueryParser.parse("bitcoin 日本語 🚀")
        assertFalse(q.isEmpty)
        assertTrue(q.text.contains("日本語"))
        assertTrue(q.text.contains("🚀"))
    }

    @Test
    fun veryLongQuery() {
        val longText = "word ".repeat(100).trim()
        val q = QueryParser.parse(longText)
        assertFalse(q.isEmpty)
    }
}
