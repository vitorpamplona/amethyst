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
package com.vitorpamplona.quartz.nip19Bech32

import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Behavioural coverage for [Nip19Parser.parseAll] — the whole-content scan run on
 * every ingested note.
 *
 * `NIP19ParserTest` covers [Nip19Parser.uriToRoute] (one entity, already isolated).
 * This covers the other half: finding entities *inside* arbitrary text — where they
 * may sit, what may precede them, and what must NOT be mistaken for one.
 *
 * In `commonTest` on purpose: [Nip19Parser] is `commonMain` and the scan uses
 * `matchAt`/`regionMatches`, so it must behave identically on every target.
 */
class Nip19ScanTest {
    companion object {
        const val NPUB = "npub1hv7k2s755n697sptva8vkh9jz40lzfzklnwj6ekewfmxp5crwdjs27007y"
        const val NPUB_HEX = "bb3d6543d4a4f45f402b674ecb5cb2155ff12456fcdd2d66d9727660d3037365"
        const val NOTE = "note1stqea6wmwezg9x6yyr6qkukw95ewtdukyaztycws65l8wppjmtpscawevv"
        const val NEVENT = "nevent1qqs0tsw8hjacs4fppgdg7f5yhgwwfkyua4xcs3re9wwkpkk2qeu6mhql22rcy"
    }

    @Test
    fun findsBareEntity() {
        val found = Nip19Parser.parseAll(NPUB)
        assertEquals(1, found.size)
        assertEquals(NPUB_HEX, (found[0] as NPub).hex)
    }

    @Test
    fun findsEntityWithNostrScheme() {
        val found = Nip19Parser.parseAll("hello nostr:$NPUB world")
        assertEquals(1, found.size)
        assertEquals(NPUB_HEX, (found[0] as NPub).hex)
    }

    @Test
    fun findsEntityWithAtPrefix() {
        val found = Nip19Parser.parseAll("cc @$NPUB thanks")
        assertEquals(1, found.size)
        assertEquals(NPUB_HEX, (found[0] as NPub).hex)
    }

    @Test
    fun findsEntityAtStartMiddleAndEndOfContent() {
        assertEquals(1, Nip19Parser.parseAll("$NPUB trailing text").size)
        assertEquals(1, Nip19Parser.parseAll("leading $NPUB trailing").size)
        assertEquals(1, Nip19Parser.parseAll("leading text $NPUB").size)
    }

    @Test
    fun findsDifferentEntityTypes() {
        assertTrue(Nip19Parser.parseAll("see $NOTE")[0] is NNote)
        assertTrue(Nip19Parser.parseAll("see $NEVENT")[0] is NEvent)
    }

    @Test
    fun findsSeveralEntitiesInOneNote() {
        val found = Nip19Parser.parseAll("$NPUB then $NOTE and nostr:$NEVENT")
        assertEquals(3, found.size)
    }

    @Test
    fun entityGluedToAPrecedingWordIsStillFound() {
        // the regex has no leading whitespace requirement
        assertEquals(1, Nip19Parser.parseAll("x$NPUB").size)
    }

    @Test
    fun uppercaseEntityIsFound() {
        // the regex is IGNORE_CASE; bech32 decode is case-insensitive too
        assertEquals(1, Nip19Parser.parseAll("NOSTR:${NPUB.uppercase()}").size)
    }

    @Test
    fun invalidChecksumIsNotReturned() {
        val broken = NPUB.dropLast(6) + "qqqqqq"
        assertEquals(emptyList(), Nip19Parser.parseAll(broken))
    }

    @Test
    fun tooShortNpubIsNotMatched() {
        assertEquals(emptyList(), Nip19Parser.parseAll("npub1tooshort"))
        assertEquals(emptyList(), Nip19Parser.parseAll("npub1"))
    }

    @Test
    fun proseWithoutEntitiesReturnsNothing() {
        assertEquals(emptyList(), Nip19Parser.parseAll("no entities in this note at all"))
        // words that start like a prefix but are not one
        assertEquals(emptyList(), Nip19Parser.parseAll("nostrich nope never nan note nsec nprofile"))
    }

    @Test
    fun contentEndingInNDoesNotReadPastTheEnd() {
        // the candidate scan dispatches on the character AFTER 'n'
        assertEquals(emptyList(), Nip19Parser.parseAll("n"))
        assertEquals(emptyList(), Nip19Parser.parseAll("N"))
        assertEquals(emptyList(), Nip19Parser.parseAll("ends with n"))
        assertEquals(emptyList(), Nip19Parser.parseAll("trailing N"))
        listOf("np", "ne", "na", "nr", "ns", "no", "nn").forEach {
            assertEquals(emptyList(), Nip19Parser.parseAll(it), "for '$it'")
            assertEquals(emptyList(), Nip19Parser.parseAll("text $it"), "for 'text $it'")
        }
    }

    @Test
    fun emptyContentReturnsNothing() {
        assertEquals(emptyList(), Nip19Parser.parseAll(""))
        assertEquals(emptyList(), Nip19Parser.parseAll("   "))
    }

    @Test
    fun adjacentEntitiesWithNoSeparatorYieldOne() {
        // the trailing ([\S]*) group is greedy, so it swallows the second entity —
        // documented behaviour, pinned here so the scan rewrite cannot change it
        assertEquals(1, Nip19Parser.parseAll(NPUB + NOTE).size)
        // separated by a space, both are found
        assertEquals(2, Nip19Parser.parseAll("$NPUB $NOTE").size)
    }

    @Test
    fun trailingPunctuationDoesNotBreakTheEntity() {
        assertEquals(1, Nip19Parser.parseAll("thanks nostr:$NPUB!").size)
        assertEquals(1, Nip19Parser.parseAll("(nostr:$NPUB)").size)
    }

    @Test
    fun entityInsideMultilineContent() {
        assertEquals(1, Nip19Parser.parseAll("line one\nnostr:$NPUB\nline three").size)
    }

    @Test
    fun scanDoesNotStopAtTheFirstNonEntityCandidate() {
        // "nostrich" and "nevermind" are 'n' candidates that fail; a real entity follows
        assertEquals(1, Nip19Parser.parseAll("nostrich nevermind nap $NPUB").size)
    }

    @Test
    fun longProseWithNoEntitiesTerminates() {
        val prose = "the nimble nocturnal nightingale never naps near noon ".repeat(2_000)
        assertEquals(emptyList(), Nip19Parser.parseAll(prose))
    }
}
