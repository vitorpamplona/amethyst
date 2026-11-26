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
package com.vitorpamplona.amethyst.commons.video.cache

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoContentTypeTest {
    private fun bytes(vararg parts: Any): ByteArray =
        parts
            .flatMap { part ->
                when (part) {
                    is String -> part.encodeToByteArray().toList()
                    is Int -> listOf(part.toByte())
                    else -> error("bad part")
                }
            }.toByteArray()

    private val mp4Head = bytes(0, 0, 0, 0x20, "ftypisom")
    private val movHead = bytes(0, 0, 0, 0x14, "ftypqt  ")

    @Test
    fun theServersAudiovisualTypeWins() {
        assertEquals("video/quicktime", VideoContentType.of("video/quicktime; charset=x", "video/mp4", "https://h/a.mp4", mp4Head))
    }

    @Test
    fun anOctetStreamFallsBackToTheEventThenTheBytes() {
        assertEquals("video/webm", VideoContentType.of("application/octet-stream", "video/webm", "https://h/a", mp4Head))
        assertEquals("video/quicktime", VideoContentType.of("application/octet-stream", null, "https://h/abc.mp4", movHead))
        assertEquals("audio/mpeg", VideoContentType.of(null, null, "https://h/abc", bytes("ID3", 3, 0)))
    }

    @Test
    fun withNothingElseTheExtensionThenMp4() {
        assertEquals("video/quicktime", VideoContentType.of(null, null, "https://h/clip.MOV?x=a.mp3", null))
        assertEquals("audio/mp4", VideoContentType.of(null, null, "https://h/song.m4a", bytes("garbage!")))
        assertEquals(VideoContentType.DEFAULT, VideoContentType.of(null, null, "https://h/0123abcd", null))
    }

    @Test
    fun anHlsTypeIsNeverAnnouncedAsAFile() {
        assertEquals("video/mp4", VideoContentType.of("application/vnd.apple.mpegurl", null, "https://h/a.mp4", null))
    }

    @Test
    fun sniffing() {
        assertEquals("video/mp4", VideoContentType.sniff(mp4Head))
        assertEquals("video/webm", VideoContentType.sniff(bytes(0x1A, 0x45, 0xDF, 0xA3, 0)))
        assertEquals("audio/wav", VideoContentType.sniff(bytes("RIFF", 0, 0, 0, 0, "WAVE")))
        assertEquals("audio/mpeg", VideoContentType.sniff(bytes(0xFF, 0xFB, 0x90)))
        assertEquals(null, VideoContentType.sniff(bytes("<html>")))
        assertTrue(VideoContentType.isPlaylist(bytes("#EXTM3U\n#EXT-X-VERSION:3")))
        assertFalse(VideoContentType.isPlaylist(mp4Head))
    }
}
