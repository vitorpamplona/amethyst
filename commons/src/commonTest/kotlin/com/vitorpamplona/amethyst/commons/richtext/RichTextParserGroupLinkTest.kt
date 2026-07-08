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
package com.vitorpamplona.amethyst.commons.richtext

import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * End-to-end coverage for inline NIP-29 group invite links (`<relay>'<groupId>[?code]`)
 * flowing through the full parse pipeline: UrlParser detection → fixMissingSpaces (must
 * keep the span atomic) → wordIdentifier (must emit [RelayGroupLinkSegment]). Also proves
 * the change doesn't disturb plain relay URLs or ordinary prose apostrophes.
 */
class RichTextParserGroupLinkTest {
    private fun groupSegmentsOf(text: String): List<RelayGroupLinkSegment> =
        RichTextParser()
            .parseText(text, EmptyTagList, null)
            .paragraphs
            .flatMap { it.words }
            .filterIsInstance<RelayGroupLinkSegment>()

    @Test
    fun detectsBareGroupLink() {
        val links = UrlParser().parseValidUrls("join wss://groups.0xchat.com'abc123 now")
        assertEquals(setOf("wss://groups.0xchat.com'abc123"), links.groupLinks)
    }

    @Test
    fun detectsGroupLinkWithCode() {
        val links = UrlParser().parseValidUrls("join wss://chat.wisp.talk'grp?code=xyz789 please")
        assertEquals(setOf("wss://chat.wisp.talk'grp?code=xyz789"), links.groupLinks)
    }

    @Test
    fun rendersGroupLinkInTheMiddleOfAPost() {
        // The whole `<relay>'<groupId>` must survive fixMissingSpaces as ONE token —
        // no space wedged in at the apostrophe — and come out as a group-link segment.
        val segs = groupSegmentsOf("come chat at wss://groups.0xchat.com'abc123 with us")
        assertEquals(1, segs.size)
        assertEquals("wss://groups.0xchat.com'abc123", segs.first().segmentText)
    }

    @Test
    fun rendersGroupLinkGluedToText() {
        // No spaces around the link at all — fixMissingSpaces must still split it out whole.
        val segs = groupSegmentsOf("here:wss://groups.0xchat.com'abc123!")
        assertEquals(1, segs.size)
        assertEquals("wss://groups.0xchat.com'abc123", segs.first().segmentText)
    }

    @Test
    fun rendersGroupLinkWithCode() {
        val segs = groupSegmentsOf("invite: wss://chat.wisp.talk'grp?code=xyz789 join up")
        assertEquals(1, segs.size)
        assertEquals("wss://chat.wisp.talk'grp?code=xyz789", segs.first().segmentText)
    }

    @Test
    fun plainRelayUrlIsNotAGroupLink() {
        val text = "my relay is wss://relay.damus.io for now"
        val links = UrlParser().parseValidUrls(text)
        assertTrue(links.groupLinks.isEmpty())
        assertTrue(links.relayUrls.contains("wss://relay.damus.io"))
        assertTrue(groupSegmentsOf(text).isEmpty())
    }

    @Test
    fun proseApostropheAfterHttpUrlIsNotAGroupLink() {
        // A possessive after a normal URL must not be mistaken for a group link (and it
        // isn't a relay URL either, so this exercises the "only relay URLs are peeked" path).
        val links = UrlParser().parseValidUrls("read example.com's blog")
        assertTrue(links.groupLinks.isEmpty())
    }

    @Test
    fun relayUrlWithTrailingApostropheButNoGroupIdIsNotALink() {
        val links = UrlParser().parseValidUrls("odd wss://relay.damus.io' end")
        assertTrue(links.groupLinks.isEmpty())
        assertNull(groupSegmentsOf("odd wss://relay.damus.io' end").firstOrNull())
    }
}
