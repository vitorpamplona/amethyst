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
package com.vitorpamplona.amethyst.commons.playback

import kotlin.test.Test
import kotlin.test.assertEquals

class CaptionFileParserTest {
    @Test
    fun readsWebVtt() {
        val vtt =
            """
            WEBVTT

            NOTE a comment

            intro
            00:00:01.000 --> 00:00:03.500 align:start
            Hello <b>there</b>
            second line

            00:04.250 --> 00:06.000
            <c.yellow>Tom &amp; Jerry</c>
            """.trimIndent()
        val cues = CaptionFileParser.parse(vtt)
        assertEquals(listOf(Cue(1000, 3500, listOf("Hello there", "second line")), Cue(4250, 6000, listOf("Tom & Jerry"))), cues)
        assertEquals(listOf("Hello there", "second line"), CaptionFileParser.linesAt(cues, 2000))
        assertEquals(emptyList(), CaptionFileParser.linesAt(cues, 3500))
    }

    @Test
    fun readsSubRip() {
        val srt = "1\r\n00:00:01,500 --> 00:00:02,000\r\n{\\an8}Top line\r\n\r\n2\r\n01:00:00,000 --> 01:00:01,000\r\nLate\r\n"
        val cues = CaptionFileParser.parse(srt)
        assertEquals(listOf(Cue(1500, 2000, listOf("Top line")), Cue(3_600_000, 3_601_000, listOf("Late"))), cues)
    }

    @Test
    fun skipsBrokenBlocks() {
        assertEquals(emptyList(), CaptionFileParser.parse("WEBVTT\n\n00:00:05.000 --> 00:00:01.000\nbackwards\n\nnot a cue"))
    }
}
