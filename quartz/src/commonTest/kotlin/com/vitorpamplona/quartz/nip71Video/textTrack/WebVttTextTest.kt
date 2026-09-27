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
package com.vitorpamplona.quartz.nip71Video.textTrack

import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebVttTextTest {
    private val document =
        """
        WEBVTT - the whole header line is skipped
        Kind: captions

        STYLE
        ::cue { color: yellow }

        NOTE this block is a comment
        and so is this line

        intro
        00:00:00.000 --> 00:00:02.000 align:start position:10%
        <v Roger Bingham>We are in New York City

        00:00:02.500 --> 00:00:04.000
        <i>Really</i>? Tom &amp; Jerry&#x21;
        <c.loud>on <00:00:03.000>two lines</c>

        00:00:05.000 --> 00:00:06.000
        <b></b>
        """.trimIndent()

    @Test
    fun keepsOnlyTheWordsOfEachCue() {
        assertEquals("We are in New York City\nReally? Tom & Jerry!\non two lines", WebVttText.cueText(document))
    }

    @Test
    fun theVisitedLinesRejoinToTheCueText() {
        val lines = mutableListOf<String>()
        assertTrue(WebVttText.forEachCueLine(document, IndexableFieldVisitor { lines.add(it!!) }))
        assertEquals(WebVttText.cueText(document), lines.joinToString("\n"))
    }

    @Test
    fun aVisitorCanStopTheWalk() {
        val lines = mutableListOf<String>()
        assertFalse(
            WebVttText.forEachCueLine(document, IndexableFieldVisitor { lines.add(it!!) && false }),
        )
        assertEquals(listOf("We are in New York City"), lines)
    }

    @Test
    fun crlfAndSrtAreReadTheSameWay() {
        val srt = "1\r\n00:00:01,000 --> 00:00:02,000\r\nHello\r\n\r\n2\r\n00:00:03,000 --> 00:00:04,000\r\nworld\r\n"
        assertEquals("Hello\nworld", WebVttText.cueText(srt))
    }

    @Test
    fun unknownReferencesAndStrayAmpersandsStayAsWritten() {
        val vtt = "WEBVTT\n\n00:00.000 --> 00:01.000\nfish & chips &unknown; &#xZZ;"
        assertEquals("fish & chips &unknown; &#xZZ;", WebVttText.cueText(vtt))
    }

    @Test
    fun anUnterminatedTagRunsToTheEndOfTheLine() {
        val vtt = "WEBVTT\n\n00:00.000 --> 00:01.000\nkept <i unterminated\nnext line"
        assertEquals("kept\nnext line", WebVttText.cueText(vtt))
    }

    @Test
    fun plainTextIsNotACaptionFile() {
        assertFalse(WebVttText.hasCues("just a description of the track"))
        assertTrue(WebVttText.hasCues(document))
    }

    @Test
    fun theEventIndexesCueTextAndPassesPlainContentThrough() {
        val tags = arrayOf(arrayOf("d", "subtitles:v1"))
        val track = TextTrackEvent("1".repeat(64), "a1".repeat(32), 1L, tags, document, "")
        assertEquals(WebVttText.cueText(document), track.indexableContent())

        val visited = mutableListOf<String?>()
        track.forEachIndexableField { visited.add(it) }
        assertEquals(track.indexableContent(), visited.joinToString(track.indexableSeparator()))

        val plain = TextTrackEvent("2".repeat(64), "a1".repeat(32), 1L, tags, "no cues here", "")
        assertEquals("no cues here", plain.indexableContent())
    }
}
