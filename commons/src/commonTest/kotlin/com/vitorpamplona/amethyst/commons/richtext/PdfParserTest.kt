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
import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PdfParserTest {
    @Test
    fun detectsPdfByExtension() {
        val url = "https://example.com/docs/paper.pdf"
        val state = RichTextParser().parseText(url, EmptyTagList, null)

        val pdfMedia = state.mediaForPager[url]
        assertTrue(pdfMedia is MediaUrlPdf, "Expected MediaUrlPdf for .pdf URL")

        val segment = state.paragraphs[0].words[0]
        assertTrue(segment is PdfSegment, "Expected PdfSegment for .pdf URL, got ${segment::class.simpleName}")
        assertEquals(url, segment.segmentText)
    }

    @Test
    fun detectsPdfFromImetaMimeTypeWithoutExtension() {
        val url = "https://files.example.com/abcd1234"
        val tags =
            ImmutableListOfLists(
                arrayOf(
                    arrayOf("imeta", "url $url", "m application/pdf"),
                ),
            )

        val state = RichTextParser().parseText(url, tags, null)

        val pdfMedia = state.mediaForPager[url]
        assertTrue(pdfMedia is MediaUrlPdf, "Expected MediaUrlPdf from imeta MIME tag")
        assertEquals("application/pdf", (pdfMedia as MediaUrlPdf).mimeType)

        val segment = state.paragraphs[0].words[0]
        assertTrue(segment is PdfSegment, "Expected PdfSegment from imeta MIME tag, got ${segment::class.simpleName}")
    }

    @Test
    fun isPdfUrlHelperMatchesPdfExtension() {
        assertTrue(RichTextParser.isPdfUrl("https://example.com/doc.pdf"))
        assertTrue(RichTextParser.isPdfUrl("https://example.com/doc.PDF"))
        assertTrue(RichTextParser.isPdfUrl("https://example.com/doc.pdf?sig=abc"))
    }
}
