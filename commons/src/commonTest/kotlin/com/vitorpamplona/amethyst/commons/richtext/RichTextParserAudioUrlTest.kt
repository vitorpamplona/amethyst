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

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RichTextParserAudioUrlTest {
    @Test
    fun mp3IsAudio() {
        assertTrue(RichTextParser.isAudioUrl("https://haven.sdbitcoiners.com/f28a5a2e.mp3"))
    }

    @Test
    fun everyAudioContainerIsAudio() {
        RichTextParser.audioExt.forEach {
            assertTrue(RichTextParser.isAudioUrl("https://example.com/a.$it"), it)
        }
    }

    @Test
    fun uppercaseIsAudio() {
        assertTrue(RichTextParser.isAudioUrl("https://example.com/A.MP3"))
    }

    @Test
    fun queryParamsAndFragmentsAreIgnored() {
        assertTrue(RichTextParser.isAudioUrl("https://example.com/a.mp3?x=1"))
        assertTrue(RichTextParser.isAudioUrl("https://example.com/a.mp3#t=10"))
    }

    @Test
    fun videoIsNotAudio() {
        assertFalse(RichTextParser.isAudioUrl("https://example.com/a.mp4"))
        assertFalse(RichTextParser.isAudioUrl("https://example.com/a.webm"))
        assertFalse(RichTextParser.isAudioUrl("https://example.com/a.mov"))
    }

    @Test
    fun hlsPlaylistIsNotAudio() {
        // A .m3u8 carries either, and a live stream is the reason the 16:9 default exists.
        assertFalse(RichTextParser.isAudioUrl("https://example.com/stream.m3u8"))
    }

    @Test
    fun imagesAndUnknownAreNotAudio() {
        assertFalse(RichTextParser.isAudioUrl("https://example.com/a.jpg"))
        assertFalse(RichTextParser.isAudioUrl("https://example.com/nothing"))
    }

    @Test
    fun mimeTypeIsAuthoritativeOverTheUrl() {
        assertTrue(RichTextParser.isAudioContent("audio/mpeg", "https://example.com/download?id=7"))
        assertFalse(RichTextParser.isAudioContent("video/mp4", "https://example.com/a.mp3"))
    }

    @Test
    fun urlExtensionIsTheFallbackWithoutAMimeType() {
        assertTrue(RichTextParser.isAudioContent(null, "https://example.com/a.mp3"))
        assertFalse(RichTextParser.isAudioContent(null, "https://example.com/a.mp4"))
    }
}
