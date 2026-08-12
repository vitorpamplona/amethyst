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

import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
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

    @Test
    fun stripExtensionsRemovesTokens() {
        assertEquals("bitcoin", SearchQuery.stripExtensions("bitcoin include:spam"))
        assertEquals("best nostr apps", SearchQuery.stripExtensions("best domain:example.com nostr language:en apps"))
        // Unknown extension keys are stripped too — the store can't apply them either.
        assertEquals("hello", SearchQuery.stripExtensions("hello foo:bar"))
    }

    @Test
    fun stripExtensionsOnExtensionsOnlyYieldsEmpty() {
        // Empty, not null: an empty search imposes no constraint (NIP-50
        // says unsupported extensions are ignored, not match-nothing).
        assertEquals("", SearchQuery.stripExtensions("include:spam nsfw:false"))
    }

    @Test
    fun stripExtensionsPassesPlainQueriesThrough() {
        assertNull(SearchQuery.stripExtensions(null))
        assertEquals("", SearchQuery.stripExtensions(""))
        assertEquals("  ", SearchQuery.stripExtensions("  "))
        assertEquals("best nostr apps", SearchQuery.stripExtensions("best nostr apps"))
        // URLs and uppercase-key tokens are terms, not extensions.
        assertEquals("check https://example.com out", SearchQuery.stripExtensions("check https://example.com out"))
        assertEquals("NASA:cool stuff", SearchQuery.stripExtensions("NASA:cool stuff"))
    }

    @Test
    fun filterStrippingSearchExtensions() {
        val filter = Filter(kinds = listOf(1), search = "bitcoin include:spam")
        val stripped = filter.strippingSearchExtensions()
        assertEquals("bitcoin", stripped.search)
        assertEquals(listOf(1), stripped.kinds)

        // Nothing to strip -> same instance, no copy.
        val plain = Filter(kinds = listOf(1), search = "bitcoin")
        assertSame(plain, plain.strippingSearchExtensions())
        val noSearch = Filter(kinds = listOf(1))
        assertSame(noSearch, noSearch.strippingSearchExtensions())
    }

    @Test
    fun filterListStrippingSearchExtensions() {
        val plain = Filter(search = "bitcoin")
        val extended = Filter(search = "bitcoin language:en")

        val untouched = listOf(plain, Filter(kinds = listOf(1)))
        assertSame(untouched, untouched.strippingSearchExtensions())

        val mixed = listOf(plain, extended).strippingSearchExtensions()
        assertSame(plain, mixed[0])
        assertEquals("bitcoin", mixed[1].search)
    }

    // ---- quoted phrases and -word exclusions --------------------------------

    @Test
    fun quotedSpanBecomesPhrase() {
        val q = SearchQuery.parse("best \"nostr apps\" today")
        assertEquals("best today", q.terms)
        assertEquals(listOf("nostr apps"), q.phrases)
        assertTrue(q.notPhrases.isEmpty())
        assertTrue(q.hasText)
    }

    @Test
    fun negatedQuotedSpanBecomesPhraseExclusion() {
        val q = SearchQuery.parse("pizza -\"pineapple pizza\"")
        assertEquals("pizza", q.terms)
        assertEquals(listOf("pineapple pizza"), q.notPhrases)
        assertTrue(q.phrases.isEmpty())
    }

    @Test
    fun minusWordBecomesExclusion() {
        val q = SearchQuery.parse("pizza -pineapple")
        assertEquals("pizza", q.terms)
        assertEquals(listOf("pineapple"), q.notTerms)
        // Exclusions alone are not required text.
        assertFalse(SearchQuery.parse("-pineapple").hasText)
    }

    @Test
    fun loneMinusStaysATerm() {
        val q = SearchQuery.parse("a - b")
        assertEquals("a - b", q.terms)
        assertTrue(q.notTerms.isEmpty())
    }

    @Test
    fun allLeadingDashesAreStripped() {
        assertEquals(listOf("word"), SearchQuery.parse("--word").notTerms)
    }

    @Test
    fun quotesProtectExtensionShapedTokens() {
        // The quote pass runs BEFORE the extension pass, so a quoted
        // extension-shaped token is a phrase, not an extension.
        val q = SearchQuery.parse("\"include:spam\"")
        assertEquals(listOf("include:spam"), q.phrases)
        assertFalse(q.includeSpam)
        assertTrue(q.extensions.isEmpty())
    }

    @Test
    fun spanEndingInExtensionKeepsTrailingExclusion() {
        // Quote-blind extension parsing would eat the closing quote of
        // "pizza include:spam" and swallow the trailing -word; the quote-first
        // order keeps the exclusion an exclusion.
        val q = SearchQuery.parse("\"pizza include:spam\" -pineapple")
        assertEquals(listOf("pizza include:spam"), q.phrases)
        assertEquals(listOf("pineapple"), q.notTerms)
        assertTrue(q.extensions.isEmpty())
    }

    @Test
    fun minusOnExtensionShapedTokenExcludesTheLiteral() {
        // There is no `-extension` syntax: keys are strictly a-z, so the `-`
        // makes the whole token an excluded literal.
        val q = SearchQuery.parse("pizza -include:spam")
        assertEquals(listOf("include:spam"), q.notTerms)
        assertFalse(q.includeSpam)
    }

    @Test
    fun unclosedQuoteRunsToEnd() {
        val q = SearchQuery.parse("\"nostr apps today")
        assertEquals(listOf("nostr apps today"), q.phrases)
        assertEquals("", q.terms)
    }

    @Test
    fun midTokenQuoteStaysOrdinary() {
        val q = SearchQuery.parse("don\"t panic")
        assertEquals("don\"t panic", q.terms)
        assertTrue(q.phrases.isEmpty())
    }

    @Test
    fun emptySpansAreDropped() {
        val q = SearchQuery.parse("a \"\" b -\"\"")
        assertEquals("a b", q.terms)
        assertTrue(q.phrases.isEmpty())
        assertTrue(q.notPhrases.isEmpty())
    }

    @Test
    fun phrasesComposeWithExtensions() {
        val q = SearchQuery.parse("\"nostr apps\" best domain:example.com -spam")
        assertEquals("best", q.terms)
        assertEquals(listOf("nostr apps"), q.phrases)
        assertEquals(listOf("spam"), q.notTerms)
        assertEquals("example.com", q.domain)
    }

    @Test
    fun toSearchStringRoundTripsFullGrammar() {
        val q = SearchQuery.parse("best \"nostr apps\" -spam -\"bad phrase\" domain:example.com")
        assertEquals("best \"nostr apps\" -spam -\"bad phrase\" domain:example.com", q.toSearchString())
    }

    @Test
    fun allDashTokensAreDroppedNotEmptied() {
        // "--" strips to nothing; surfacing an empty exclusion would
        // round-trip into a required "-" term.
        val q = SearchQuery.parse("a --")
        assertEquals("a", q.terms)
        assertTrue(q.notTerms.isEmpty())
        assertEquals("a", SearchQuery.parse(q.toSearchString()).terms)
        assertEquals("", SearchQuery.stripExtensions("-- include:spam"))
    }

    @Test
    fun consecutiveSpansEachLift() {
        val q = SearchQuery.parse("\"a\"\"b\" -\"c\"")
        assertEquals(listOf("a", "b"), q.phrases)
        assertEquals(listOf("c"), q.notPhrases)
        assertEquals("", q.terms)
    }

    @Test
    fun textAfterClosingQuoteIsItsOwnTerm() {
        // The lifted span's place stays a token boundary for what follows.
        val q = SearchQuery.parse("\"a b\"c")
        assertEquals(listOf("a b"), q.phrases)
        assertEquals("c", q.terms)
    }

    @Test
    fun stripExtensionsKeepsPhrasesAndExclusions() {
        assertEquals(
            "best \"nostr apps\" -spam",
            SearchQuery.stripExtensions("best \"nostr apps\" -spam language:en"),
        )
        // No extensions -> the original string comes back untouched.
        assertEquals("best \"nostr apps\" -spam", SearchQuery.stripExtensions("best \"nostr apps\" -spam"))
    }
}
