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
package com.vitorpamplona.amethyst

import com.vitorpamplona.amethyst.service.CachedRichTextParser
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CachedRichTextParserMarkdownTest {
    private val cashuBToken =
        "cashuBv2FteCJodHRwczovL21pbnQubWluaWJpdHMuY2FzaC9CaXRjb2luYXVjc2F0YWRkVEVTVGF0n79haUgAEHk32wzI" +
            "ZWFwn79hYQJhc3hAZWM1YWI3Yjc1NjViYjBjZTZhNzg2NzBkMDA0OGExMjVlZGQzMjJhYmVjMTEzYWMwZTBmZGVkZmE3NTQ4Mzg3OWFj" +
            "WCED0-ops8Ta6NjKChNJPe_jgIbXLlyxg2KSy2WaSTADo5D_v2FhCGFzeEBkNDZlODU5MDExNjU0NmNjZjAwNTE3ZTQ1NmU0MTY0N2Fm" +
            "ZWUxOWNlMzY2N2IzYTcxODZkMzEwZDY1MjM3OTM4YWNYIQMTDGTY943O4ojhKopoYdemsUE2rSLfzwNBODL8WgOX0v______"

    @Test
    fun cashuBTokenWithTrailingUnderscoresIsNotMarkdown() {
        // Bug repro: the user's token tail is `______`, which the
        // markdown detector tagged as bold via the `contains("__")`
        // check. RenderContentAsMarkdown has no CashuSegment support,
        // so the chat bubble rendered the raw base64 instead of the
        // redeem card. The exemption forces the rich-text path.
        assertFalse(CachedRichTextParser.isMarkdown(cashuBToken))
    }

    @Test
    fun cashuBTokenInLongerMessageIsNotMarkdown() {
        val text = "Here, send to a friend: $cashuBToken"
        assertFalse(CachedRichTextParser.isMarkdown(text))
    }

    @Test
    fun cashuATokenIsNotMarkdown() {
        // cashuA payloads share the base64url alphabet, so the same
        // false-positive risk applies. Pre-empt it.
        val fakeCashuA = "cashuAeyJ0b2tlbiI6W3sicHJvb2ZzIjpbXX1dfQ___"
        assertFalse(CachedRichTextParser.isMarkdown(fakeCashuA))
    }

    @Test
    fun plainBoldStillRecognizedAsMarkdown() {
        // Make sure the exemption is targeted — content without a cashu
        // prefix still triggers the markdown branch.
        assertTrue(CachedRichTextParser.isMarkdown("hello __world__"))
    }
}
