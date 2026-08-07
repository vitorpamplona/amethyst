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

import com.vitorpamplona.quartz.nip92IMeta.IMetaTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RichTextParserMalformedMimeTest {
    private val url = "https://blossom.primal.net/c27d7b7be6e58d69b29006cc275d29d67967760b1772e50a79d5b24f60d62fc5.jpg"

    private fun parse(mime: String): MediaUrlContent? =
        RichTextParser().createMediaContent(
            fullUrl = url,
            eventTags =
                mapOf(
                    url to
                        IMetaTag(
                            url = url,
                            properties = mapOf("m" to listOf(mime), "dim" to listOf("960.0x1358.0")),
                        ),
                ),
            description = null,
        )

    @Test
    fun malformedImetaMimeStillRendersAsImage() {
        val content = parse("jpeg")
        assertTrue(content is MediaUrlImage, "bare `m jpeg` must still route to MediaUrlImage")
    }

    @Test
    fun malformedImetaMimeIsNormalizedForSharing() {
        val content = parse("jpeg") as MediaUrlImage
        assertEquals("image/jpeg", content.mimeType)
    }

    @Test
    fun wellFormedImetaMimeIsUntouched() {
        val content = parse("image/jpeg") as MediaUrlImage
        assertEquals("image/jpeg", content.mimeType)
    }
}
