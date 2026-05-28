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
package com.vitorpamplona.quartz.nip18Reposts.quotes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuotedEventAuthorHintsTest {
    private val eventA = "434cc91f17c18cfd985a7e7272ee1c8ee3c1e7b852ebc018de15a473d6c05c95"
    private val authorA = "ac0231309b8abb0f6429d6a2e543de55b53f4d40f7ade1b7458ccfae59d0007c"
    private val eventB = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    private val authorB = "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210"

    @Test
    fun pairsSingleEMentionWithSinglePMention() {
        val tags =
            arrayOf(
                arrayOf("e", eventA, "", "mention"),
                arrayOf("p", authorA, "", "mention"),
            )
        val hints = QuotedEventAuthorHints.collect(tags)
        assertEquals(1, hints.size)
        assertEquals(eventA, hints[0].eventId)
        assertEquals(authorA, hints[0].authorPubKey)
    }

    @Test
    fun doesNotPairWhenMultipleEMentions() {
        // Multiple e-mentions are ambiguous — positional pairing produces false
        // attributions (e.g. cc-style p-mentions), so we emit nothing.
        val tags =
            arrayOf(
                arrayOf("e", eventA, "", "mention"),
                arrayOf("e", eventB, "", "mention"),
                arrayOf("p", authorA, "", "mention"),
                arrayOf("p", authorB, "", "mention"),
            )
        assertTrue(QuotedEventAuthorHints.collect(tags).isEmpty())
    }

    @Test
    fun doesNotPairWhenMultiplePMentions() {
        // Single e-mention but two p-mentions (one quoted author, one cc'd) —
        // ambiguous, emit nothing.
        val tags =
            arrayOf(
                arrayOf("e", eventA, "", "mention"),
                arrayOf("p", authorA, "", "mention"),
                arrayOf("p", authorB, "", "mention"),
            )
        assertTrue(QuotedEventAuthorHints.collect(tags).isEmpty())
    }

    @Test
    fun multipleQuotesStillResolveViaQTagsOrInlinePubkeys() {
        // The unambiguous paths (q-tag, inline e-mention pubkey) still emit
        // multiple hints in the same event — only positional NIP-10 pairing is
        // restricted to the 1+1 case.
        val tags =
            arrayOf(
                arrayOf("q", eventA, "", authorA),
                arrayOf("e", eventB, "", "mention", authorB),
            )
        val hints = QuotedEventAuthorHints.collect(tags).associate { it.eventId to it.authorPubKey }
        assertEquals(2, hints.size)
        assertEquals(authorA, hints[eventA])
        assertEquals(authorB, hints[eventB])
    }

    @Test
    fun emptyWhenOnlyEMentions() {
        val tags = arrayOf(arrayOf("e", eventA, "", "mention"))
        assertTrue(QuotedEventAuthorHints.collect(tags).isEmpty())
    }

    @Test
    fun emptyWhenOnlyPMentions() {
        val tags = arrayOf(arrayOf("p", authorA, "", "mention"))
        assertTrue(QuotedEventAuthorHints.collect(tags).isEmpty())
    }

    @Test
    fun ignoresNonMentionMarkers() {
        val tags =
            arrayOf(
                arrayOf("e", eventA, "", "reply"),
                arrayOf("p", authorA, ""),
            )
        assertTrue(QuotedEventAuthorHints.collect(tags).isEmpty())
    }

    @Test
    fun extractsAuthorFromQTag() {
        val tags = arrayOf(arrayOf("q", eventA, "wss://r.example/", authorA))
        val hints = QuotedEventAuthorHints.collect(tags)
        assertEquals(1, hints.size)
        assertEquals(eventA, hints[0].eventId)
        assertEquals(authorA, hints[0].authorPubKey)
    }

    @Test
    fun qTagWithoutInlineAuthorEmitsNothing() {
        val tags = arrayOf(arrayOf("q", eventA, "wss://r.example/"))
        assertTrue(QuotedEventAuthorHints.collect(tags).isEmpty())
    }

    @Test
    fun extractsAuthorFromEMentionWithInlinePubkey() {
        val tags = arrayOf(arrayOf("e", eventA, "wss://r.example/", "mention", authorA))
        val hints = QuotedEventAuthorHints.collect(tags)
        assertEquals(1, hints.size)
        assertEquals(eventA, hints[0].eventId)
        assertEquals(authorA, hints[0].authorPubKey)
    }

    @Test
    fun extractsAuthorFromEMentionSwappedPubkeyMarkerLayout() {
        // NIP-10 allows the pubkey at position 3 and the marker at position 4
        // (the swapped variant — see MarkedETag.pickAuthor / pickMarker).
        // The collector must recognise it to avoid silently dropping hints
        // from clients that emit this layout.
        val tags = arrayOf(arrayOf("e", eventA, "wss://r.example/", authorA, "mention"))
        val hints = QuotedEventAuthorHints.collect(tags)
        assertEquals(1, hints.size)
        assertEquals(eventA, hints[0].eventId)
        assertEquals(authorA, hints[0].authorPubKey)
    }

    @Test
    fun qTagTakesPriorityOverPairedMention() {
        val tags =
            arrayOf(
                arrayOf("q", eventA, "", authorA),
                arrayOf("e", eventA, "", "mention"),
                arrayOf("p", authorB, "", "mention"),
            )
        val hints = QuotedEventAuthorHints.collect(tags)
        assertEquals(1, hints.size)
        assertEquals(authorA, hints[0].authorPubKey)
    }

    @Test
    fun inlineEMentionPubkeyTakesPriorityOverPairedPMention() {
        val tags =
            arrayOf(
                arrayOf("e", eventA, "", "mention", authorA),
                arrayOf("p", authorB, "", "mention"),
            )
        val hints = QuotedEventAuthorHints.collect(tags)
        assertEquals(1, hints.size)
        assertEquals(authorA, hints[0].authorPubKey)
    }

    @Test
    fun deduplicatesByEventIdFirstWriteWins() {
        val tags =
            arrayOf(
                arrayOf("q", eventA, "", authorA),
                arrayOf("q", eventA, "", authorB),
            )
        val hints = QuotedEventAuthorHints.collect(tags)
        assertEquals(1, hints.size)
        assertEquals(authorA, hints[0].authorPubKey)
    }

    @Test
    fun unusualWhalesBridgeShape() {
        // Reproduces the outer kind-1 tag set from the bug report (a Momostr-bridged
        // quote post). The bech32 embedded in content has no relay/author, so this
        // paired e/p mention is the only carrier of the quoted author.
        val tags =
            arrayOf(
                arrayOf("e", "434cc91f17c18cfd985a7e7272ee1c8ee3c1e7b852ebc018de15a473d6c05c95", "", "mention"),
                arrayOf("p", "ac0231309b8abb0f6429d6a2e543de55b53f4d40f7ade1b7458ccfae59d0007c", "", "mention"),
            )
        val hints = QuotedEventAuthorHints.collect(tags)
        assertEquals(1, hints.size)
        assertEquals("434cc91f17c18cfd985a7e7272ee1c8ee3c1e7b852ebc018de15a473d6c05c95", hints[0].eventId)
        assertEquals("ac0231309b8abb0f6429d6a2e543de55b53f4d40f7ade1b7458ccfae59d0007c", hints[0].authorPubKey)
    }

    @Test
    fun rejectsMalformedTagShapes() {
        val tags =
            arrayOf(
                arrayOf("e"),
                arrayOf("e", "shorthex", "", "mention"),
                arrayOf("q", eventA, "", "shortauthor"),
                arrayOf("e", eventA, "", "mention", "shortauthor"),
            )
        assertTrue(QuotedEventAuthorHints.collect(tags).isEmpty())
    }

    @Test
    fun rejectsSixtyFourCharNonHex() {
        // 64-char strings that aren't hex would crash downstream callers that
        // `require(isValidHex(...))`. The collector must validate hex, not just
        // length.
        val nonHexId = "z".repeat(64)
        val nonHexAuthor = "g".repeat(64)
        val tags =
            arrayOf(
                arrayOf("q", nonHexId, "", authorA),
                arrayOf("q", eventA, "", nonHexAuthor),
                arrayOf("e", nonHexId, "", "mention", authorA),
                arrayOf("e", eventA, "", "mention", nonHexAuthor),
                arrayOf("e", nonHexId, "", "mention"),
                arrayOf("p", nonHexAuthor, "", "mention"),
            )
        assertTrue(QuotedEventAuthorHints.collect(tags).isEmpty())
    }
}
