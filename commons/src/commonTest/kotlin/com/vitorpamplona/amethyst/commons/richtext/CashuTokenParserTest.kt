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
import kotlin.test.assertTrue

class CashuTokenParserTest {
    private val realCashuBToken =
        "cashuBv2FteCJodHRwczovL21pbnQubWluaWJpdHMuY2FzaC9CaXRjb2luYXVjc2F0YWRkVEVTVGF0n79haUgAEHk32wzI" +
            "ZWFwn79hYQJhc3hAZWM1YWI3Yjc1NjViYjBjZTZhNzg2NzBkMDA0OGExMjVlZGQzMjJhYmVjMTEzYWMwZTBmZGVkZmE3NTQ4Mzg3OWFj" +
            "WCED0-ops8Ta6NjKChNJPe_jgIbXLlyxg2KSy2WaSTADo5D_v2FhCGFzeEBkNDZlODU5MDExNjU0NmNjZjAwNTE3ZTQ1NmU0MTY0N2Fm" +
            "ZWUxOWNlMzY2N2IzYTcxODZkMzEwZDY1MjM3OTM4YWNYIQMTDGTY943O4ojhKopoYdemsUE2rSLfzwNBODL8WgOX0v______"

    @Test
    fun detectsCashuBAlone() {
        val state = RichTextParser().parseText(realCashuBToken, EmptyTagList, null)
        val segment = state.paragraphs[0].words[0]
        assertTrue(
            segment is CashuSegment,
            "Expected CashuSegment for cashuB token alone, got ${segment::class.simpleName} (\"${segment.segmentText.take(20)}…\")",
        )
        assertEquals(realCashuBToken, segment.segmentText)
    }

    @Test
    fun detectsCashuBSurroundedByText() {
        val text = "Here is a token: $realCashuBToken — enjoy"
        val state = RichTextParser().parseText(text, EmptyTagList, null)
        val tokenSegment =
            state.paragraphs[0]
                .words
                .firstOrNull { it is CashuSegment }
        assertTrue(
            tokenSegment != null,
            "Expected a CashuSegment in the parsed words; got " +
                state.paragraphs[0]
                    .words
                    .joinToString { (it::class.simpleName ?: "?") + "(\"${it.segmentText.take(12)}…\")" },
        )
        assertEquals(realCashuBToken, tokenSegment.segmentText)
    }

    @Test
    fun detectsCashuBWithLeadingNewline() {
        val text = "Here is a token:\n$realCashuBToken"
        val state = RichTextParser().parseText(text, EmptyTagList, null)
        val tokenSegment =
            state.paragraphs
                .flatMap { it.words }
                .firstOrNull { it is CashuSegment }
        assertTrue(
            tokenSegment != null,
            "Expected a CashuSegment after a newline; got " +
                state.paragraphs.flatMap { it.words }.joinToString { it::class.simpleName ?: "?" },
        )
        assertEquals(realCashuBToken, tokenSegment.segmentText)
    }
}
