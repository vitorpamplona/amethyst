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
package com.vitorpamplona.quartz.nip10Notes.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Behavioural coverage for the two content scans that run on every ingested note.
 *
 * Both scans jump between `#` positions with `indexOf` and apply their regex
 * anchored there, so the cases below deliberately cover what decides a match:
 * what precedes the `#`, where it sits in the string, and what terminates the tag.
 *
 * In `commonTest` on purpose — these are `commonMain` parsers and the scans use
 * `matchAt`, so they must behave identically on JVM, Android, Apple and native.
 */
class ContentScanTest {
    // ---------- findHashtags ----------

    @Test
    fun hashtagAtStartOfContent() {
        assertEquals(listOf("bitcoin"), findHashtags("#bitcoin"))
    }

    @Test
    fun hashtagAfterEachKindOfWhitespace() {
        assertEquals(listOf("a"), findHashtags("x #a"))
        assertEquals(listOf("b"), findHashtags("x\n#b"))
        assertEquals(listOf("c"), findHashtags("x\t#c"))
        assertEquals(listOf("d"), findHashtags("x\r\n#d"))
    }

    @Test
    fun hashtagMustBePrecededByWhitespaceOrStart() {
        // glued to a word: not a hashtag
        assertEquals(emptyList(), findHashtags("word#nottag"))
        assertEquals(emptyList(), findHashtags("a#b"))
        // a non-breaking space is not \s, so it does not open a hashtag either
        assertEquals(emptyList(), findHashtags("x\u00a0#nope"))
    }

    @Test
    fun hashWithNoTagBodyIsNotAHashtag() {
        assertEquals(emptyList(), findHashtags("#"))
        assertEquals(emptyList(), findHashtags(" #"))
        assertEquals(emptyList(), findHashtags("text # more"))
        // the second '#' is an excluded char, so it cannot open the tag body
        assertEquals(emptyList(), findHashtags("##tag"))
    }

    @Test
    fun punctuationTerminatesTheTag() {
        assertEquals(listOf("tag"), findHashtags("#tag,"))
        assertEquals(listOf("tag"), findHashtags("#tag."))
        assertEquals(listOf("tag"), findHashtags("#tag!"))
        assertEquals(listOf("tag"), findHashtags("#tag)"))
        assertEquals(listOf("tag"), findHashtags("#tag;"))
    }

    @Test
    fun tagsKeepCharactersOutsideTheExcludedSet() {
        assertEquals(listOf("caf\u00e9"), findHashtags("#caf\u00e9"))
        assertEquals(listOf("123"), findHashtags("#123"))
        assertEquals(listOf("a-b_c"), findHashtags("#a-b_c"))
        assertEquals(listOf("\u65e5\u672c"), findHashtags("#\u65e5\u672c"))
    }

    @Test
    fun multipleHashtagsAndDeduplication() {
        assertEquals(listOf("a", "b", "c"), findHashtags("#a #b #c").sorted())
        assertEquals(listOf("dup"), findHashtags("#dup #dup #dup"))
    }

    @Test
    fun hashtagAtVeryEndOfContent() {
        assertEquals(listOf("end"), findHashtags("something #end"))
    }

    @Test
    fun blankContentReturnsEmpty() {
        assertEquals(emptyList(), findHashtags(""))
        assertEquals(emptyList(), findHashtags("   "))
        assertEquals(emptyList(), findHashtags("\n\t "))
    }

    @Test
    fun callerSuppliedOutputSetIsReusedAndAccumulates() {
        val shared = mutableSetOf<String>()
        findHashtags("#one", shared)
        findHashtags("#two", shared)
        assertEquals(listOf("one", "two"), shared.toList().sorted())
    }

    @Test
    fun scanDoesNotStopAtTheFirstNonMatchingHash() {
        // a glued '#' must not hide a later real hashtag
        assertEquals(listOf("real"), findHashtags("word#nottag #real"))
    }

    // ---------- IndexedTags ----------

    private val tags =
        arrayOf(
            arrayOf("p", "pubkey0"),
            arrayOf("e", "event1"),
            arrayOf("a", "30023:author:slug"),
            arrayOf("p", "pubkey3"),
            arrayOf("t", "topic"),
            arrayOf("p"),
        )

    @Test
    fun indexTagResolvesPeople() {
        assertEquals(listOf("pubkey0"), findIndexTagsWithPeople("#[0]", tags))
        assertEquals(listOf("pubkey3"), findIndexTagsWithPeople("hi #[3]", tags))
    }

    @Test
    fun indexTagResolvesEventsAndAddresses() {
        assertEquals(setOf("event1"), findIndexTagsWithEventsOrAddresses("#[1]", tags))
        assertEquals(setOf("30023:author:slug"), findIndexTagsWithEventsOrAddresses("#[2]", tags))
    }

    @Test
    fun indexTagIgnoresWrongTagKinds() {
        // "t" is neither p, e nor a
        assertEquals(emptyList(), findIndexTagsWithPeople("#[4]", tags))
        assertEquals(emptySet(), findIndexTagsWithEventsOrAddresses("#[4]", tags))
        // a "p" tag with no value must not blow up or emit
        assertEquals(emptyList(), findIndexTagsWithPeople("#[5]", tags))
    }

    @Test
    fun indexTagOutOfRangeIsIgnored() {
        assertEquals(emptyList(), findIndexTagsWithPeople("#[99]", tags))
        assertEquals(emptySet(), findIndexTagsWithEventsOrAddresses("#[99]", tags))
    }

    @Test
    fun indexTagWithHugeNumberDoesNotThrow() {
        // does not fit in an Int — must be swallowed, not propagated
        assertEquals(emptyList(), findIndexTagsWithPeople("#[99999999999999999999]", tags))
    }

    @Test
    fun malformedIndexRefsAreIgnored() {
        assertEquals(emptyList(), findIndexTagsWithPeople("#[abc]", tags))
        assertEquals(emptyList(), findIndexTagsWithPeople("#[]", tags))
        assertEquals(emptyList(), findIndexTagsWithPeople("#[0", tags))
        assertEquals(emptyList(), findIndexTagsWithPeople("[0]", tags))
    }

    @Test
    fun indexRefMustBePrecededByWhitespaceOrStart() {
        assertEquals(emptyList(), findIndexTagsWithPeople("word#[0]", tags))
        assertEquals(listOf("pubkey0"), findIndexTagsWithPeople("word #[0]", tags))
    }

    @Test
    fun multipleIndexRefsAndDeduplication() {
        assertEquals(listOf("pubkey0", "pubkey3"), findIndexTagsWithPeople("#[0] #[3]", tags).sorted())
        assertEquals(listOf("pubkey0"), findIndexTagsWithPeople("#[0] #[0]", tags))
        assertEquals(
            setOf("30023:author:slug", "event1"),
            findIndexTagsWithEventsOrAddresses("#[1] #[2]", tags),
        )
    }

    @Test
    fun indexScanDoesNotStopAtTheFirstNonMatchingHash() {
        assertEquals(listOf("pubkey0"), findIndexTagsWithPeople("word#[9] #[0]", tags))
        assertEquals(listOf("pubkey0"), findIndexTagsWithPeople("#hashtag #[0]", tags))
    }

    @Test
    fun emptyContentAndEmptyTagArray() {
        assertEquals(emptyList(), findIndexTagsWithPeople("", tags))
        assertEquals(emptyList(), findIndexTagsWithPeople("#[0]", arrayOf()))
        assertEquals(emptySet(), findIndexTagsWithEventsOrAddresses("#[0]", arrayOf()))
    }

    @Test
    fun hashtagAndIndexRefsCoexistInOneNote() {
        val content = "#intro see #[0] and #[1] about #nostr"
        assertEquals(listOf("intro", "nostr"), findHashtags(content).sorted())
        assertEquals(listOf("pubkey0"), findIndexTagsWithPeople(content, tags))
        assertEquals(setOf("event1"), findIndexTagsWithEventsOrAddresses(content, tags))
    }

    @Test
    fun longContentWithNoMarkersTerminates() {
        val prose = "the quick brown fox jumps over the lazy dog ".repeat(2_000)
        assertTrue(findHashtags(prose).isEmpty())
        assertTrue(findIndexTagsWithPeople(prose, tags).isEmpty())
    }
}
