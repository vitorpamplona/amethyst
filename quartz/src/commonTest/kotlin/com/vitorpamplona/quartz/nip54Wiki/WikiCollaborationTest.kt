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
package com.vitorpamplona.quartz.nip54Wiki

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WikiCollaborationTest {
    private val destination = "d".repeat(64)
    private val fork = "f".repeat(64)
    private val base = "b".repeat(64)
    private val merged = "e".repeat(64)
    private val request = "a".repeat(64)

    private fun mergeRequest(vararg tags: Array<String>) = WikiMergeRequestEvent("id", "pk", 0L, arrayOf(*tags), "why", "sig")

    private fun coordinate() = "${WikiNoteEvent.KIND}:$destination:hot-ice-creams"

    // ---- kind 818 --------------------------------------------------------------------------

    @Test
    fun kindsAreRegisteredWithTheEventFactory() {
        assertTrue(EventFactory.isKnownKind(WikiMergeRequestEvent.KIND))
        assertTrue(EventFactory.isKnownKind(WikiMergeAcceptanceEvent.KIND))
        assertTrue(EventFactory.isKnownKind(WikiRedirectEvent.KIND))
    }

    @Test
    fun parsesTheSpecExampleShape() {
        val event =
            mergeRequest(
                arrayOf("a", coordinate(), "wss://relay.example.com"),
                arrayOf("e", base, "wss://relay.example.com"),
                arrayOf("p", destination),
                arrayOf("e", fork, "wss://relay.example.com", "source"),
            )

        assertEquals("hot-ice-creams", event.targetArticle()?.dTag)
        assertEquals(destination, event.destinationAuthor())
        assertEquals(fork, event.mergeSource())
        assertEquals(base, event.baseVersion())
        assertTrue(event.hasMergeSource())
    }

    @Test
    fun acceptsTheForkMarkerThePublishersActuallyEmit() {
        // NIP-54 writes this marker as `source`; jumble writes `fork`. Reading only the spec's
        // word turns every merge request in the wild into one with nothing to merge.
        val event =
            mergeRequest(
                arrayOf("a", coordinate()),
                arrayOf("p", destination),
                arrayOf("e", fork, "", "fork"),
            )

        assertEquals(fork, event.mergeSource())
        assertTrue(event.hasMergeSource())
    }

    @Test
    fun bothMarkersAreRecognised() {
        assertEquals(setOf("source", "fork"), WikiMergeRequestEvent.MERGE_SOURCE_MARKERS)
    }

    @Test
    fun aRequestWithoutASourceHasNothingToMerge() {
        val event = mergeRequest(arrayOf("a", coordinate()), arrayOf("p", destination))

        assertNull(event.mergeSource())
        assertTrue(!event.hasMergeSource())
    }

    @Test
    fun theBaseVersionIsTheUnmarkedEventNotTheSource() {
        val event =
            mergeRequest(
                arrayOf("e", fork, "", "fork"),
                arrayOf("e", base),
            )

        assertEquals(fork, event.mergeSource())
        assertEquals(base, event.baseVersion())
    }

    @Test
    fun thereIsNoBaseVersionWhenOnlyTheSourceIsTagged() {
        val event = mergeRequest(arrayOf("e", fork, "", "source"))

        assertNull(event.baseVersion())
    }

    @Test
    fun buildsWithTheSpecMarker() {
        val template =
            WikiMergeRequestEvent.build(
                targetArticle = Address(WikiNoteEvent.KIND, destination, "hot-ice-creams"),
                destinationAuthor = destination,
                mergeSourceId = fork,
                explanation = "I added a section",
                baseVersionId = base,
            )

        val sourceTag = template.tags.first { it[0] == "e" && it.size > 3 && it[3].isNotEmpty() }
        assertEquals(WikiMergeRequestEvent.SPEC_MERGE_SOURCE_MARKER, sourceTag[3])
        assertEquals(fork, sourceTag[1])
        assertEquals("I added a section", template.content)
    }

    // ---- kind 819 --------------------------------------------------------------------------

    @Test
    fun parsesAnAcceptance() {
        val event =
            WikiMergeAcceptanceEvent(
                "id",
                "pk",
                0L,
                arrayOf(
                    arrayOf("e", merged, "", "result"),
                    arrayOf("e", request, "", "request"),
                    arrayOf("p", destination),
                ),
                "",
                "sig",
            )

        assertEquals(merged, event.result())
        assertEquals(request, event.request())
        assertEquals(destination, event.requester())
        assertTrue(event.hasRequest())
    }

    @Test
    fun anAcceptanceWithoutARequestIsNotAttributable() {
        val event = WikiMergeAcceptanceEvent("id", "pk", 0L, arrayOf(arrayOf("e", merged, "", "result")), "", "sig")

        assertNull(event.request())
        assertTrue(!event.hasRequest())
    }

    @Test
    fun buildsAnAcceptance() {
        val template = WikiMergeAcceptanceEvent.build(request, destination, merged)
        val markers = template.tags.filter { it[0] == "e" }.associate { it[3] to it[1] }

        assertEquals(merged, markers["result"])
        assertEquals(request, markers["request"])
    }

    // ---- kind 30819 ------------------------------------------------------------------------

    @Test
    fun parsesARedirect() {
        val event =
            WikiRedirectEvent(
                "id",
                "pk",
                0L,
                arrayOf(
                    arrayOf("d", "shell-structure"),
                    arrayOf("a", "${WikiNoteEvent.KIND}:$destination:thin-shell-structure"),
                ),
                "",
                "sig",
            )

        assertEquals("shell-structure", event.fromSlug())
        assertEquals("thin-shell-structure", event.target()?.dTag)
        assertTrue(event.hasTarget())
    }

    @Test
    fun aRedirectWithNoDestinationCannotBeFollowed() {
        val event = WikiRedirectEvent("id", "pk", 0L, arrayOf(arrayOf("d", "orphan")), "", "sig")

        assertNull(event.target())
        assertTrue(!event.hasTarget())
    }

    @Test
    fun normalizesSlugs() {
        assertEquals("shell-structure", WikiRedirectEvent.normalizeSlug("Shell Structure"))
        assertEquals("hot-ice-creams", WikiRedirectEvent.normalizeSlug("  Hot Ice-Creams  "))
        assertEquals("c-programming", WikiRedirectEvent.normalizeSlug("C++ Programming"))
        assertEquals("", WikiRedirectEvent.normalizeSlug("!!!"))
    }

    @Test
    fun dropsPunctuationRatherThanFoldingItToDashes() {
        // Folding would give `c--programming`, which is a different d-tag from the one the rest
        // of the network computes — a redirect published under it would match nothing.
        assertEquals("c-programming", WikiRedirectEvent.normalizeSlug("C++ Programming"))
        assertEquals("whats-new", WikiRedirectEvent.normalizeSlug("What's New"))
        assertEquals("ai-ml", WikiRedirectEvent.normalizeSlug("AI / ML"))
    }

    @Test
    fun treatsDotsAndUnderscoresAsSeparators() {
        assertEquals("a-b-c", WikiRedirectEvent.normalizeSlug("a.b_c"))
        assertEquals("a-b", WikiRedirectEvent.normalizeSlug("a___b"))
    }

    @Test
    fun keepsNonLatinLetters() {
        // Letters are Unicode-wide, so a non-Latin title keeps its words instead of vanishing.
        assertEquals("кот", WikiRedirectEvent.normalizeSlug("Кот"))
        assertEquals("日本語-wiki", WikiRedirectEvent.normalizeSlug("日本語 Wiki"))
    }

    @Test
    fun buildsARedirectWithANormalizedSlug() {
        val template =
            WikiRedirectEvent.build("Shell Structure", Address(WikiNoteEvent.KIND, destination, "thin-shell-structure"))
        val tags = template.tags.associate { it[0] to it[1] }

        assertEquals("shell-structure", tags["d"])
        assertEquals("${WikiNoteEvent.KIND}:$destination:thin-shell-structure", tags["a"])
        assertEquals("", template.content)
    }
}
