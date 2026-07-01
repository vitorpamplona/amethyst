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
package com.vitorpamplona.quartz.nipXXPodcasting20

import com.vitorpamplona.quartz.nipXXPodcasting20.trailer.Podcasting20TrailerEvent
import com.vitorpamplona.quartz.utils.DeterministicSigner
import com.vitorpamplona.quartz.utils.nsecToKeyPair
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class Podcasting20TrailerEventTest {
    private val signer =
        DeterministicSigner(
            "nsec10g0wheggqn9dawlc0yuv6adnat6n09anr7eyykevw2dm8xa5fffs0wsdsr".nsecToKeyPair(),
        )

    @Test
    fun `kind is 30055`() {
        assertEquals(30055, Podcasting20TrailerEvent.KIND)
    }

    @Test
    fun `build round-trips fields`() {
        val template =
            Podcasting20TrailerEvent.build(
                dTag = "trailer-1699123456-xyz789abc",
                title = "Season 2 Preview",
                url = "https://example.com/trailers/season-2-preview.mp3",
                pubdate = "Thu, 04 Nov 2023 12:00:00 GMT",
                lengthInBytes = 1024000,
                mimeType = "audio/mpeg",
                season = 2,
            )
        val event = signer.sign<Podcasting20TrailerEvent>(template)

        assertEquals("trailer-1699123456-xyz789abc", event.dTag())
        assertEquals("Season 2 Preview", event.title())
        assertEquals("https://example.com/trailers/season-2-preview.mp3", event.url())
        assertEquals("Thu, 04 Nov 2023 12:00:00 GMT", event.pubDate())
        assertEquals(1024000L, event.lengthInBytes())
        assertEquals("audio/mpeg", event.mimeType())
        assertEquals(2, event.season())
        // Per spec the content SHOULD carry the trailer title.
        assertEquals("Season 2 Preview", event.content)
    }

    @Test
    fun `optional fields default to null`() {
        val template =
            Podcasting20TrailerEvent.build(
                dTag = "trailer-min",
                title = "Teaser",
                url = "https://example.com/teaser.mp3",
                pubdate = "Thu, 04 Nov 2023 12:00:00 GMT",
            )
        val event = signer.sign<Podcasting20TrailerEvent>(template)

        assertNull(event.lengthInBytes())
        assertNull(event.mimeType())
        assertNull(event.season())
    }
}
