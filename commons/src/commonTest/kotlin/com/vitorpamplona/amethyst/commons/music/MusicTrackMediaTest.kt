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
package com.vitorpamplona.amethyst.commons.music

import com.vitorpamplona.quartz.experimental.music.track.MusicTrackEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MusicTrackMediaTest {
    private fun track(vararg tags: Array<String>) =
        MusicTrackEvent(
            id = "a".repeat(64),
            pubKey = "4be27446dc3be488e08232c99b8f70a8c60832534902d979d9a3f171a0b200b0",
            createdAt = 1791030750,
            tags = arrayOf(arrayOf("d", "track"), *tags),
            content = "",
            sig = "0".repeat(128),
        )

    @Test
    fun mp4InUrlWithoutVideoTagPlaysAsVideo() {
        // The Nostria track that rendered its 1080p video inside the waveform strip.
        val url = "https://nostr.download/21535ca606b945416afe4e39e46d578290413b393483f0e67be8ec3e3bf78998.mp4"
        val event = track(arrayOf("title", "Aza Song   Podcast 39"), arrayOf("url", url), arrayOf("duration", "204"))

        assertEquals(MusicTrackMedia.Video(url), event.media())
    }

    @Test
    fun audioExtensionsStayAudio() {
        listOf("mp3", "m4a", "flac", "ogg", "opus", "wav").forEach { ext ->
            val url = "https://example.com/song.$ext"
            assertEquals(MusicTrackMedia.Audio(url, null), track(arrayOf("url", url)).media(), ext)
        }
    }

    @Test
    fun extensionlessUrlStaysAudio() {
        val url = "https://blossom.example/5f0c1a5e8b7c0d3f6a2e9b4c1d8e7f6a5b4c3d2e1f0a9b8c7d6e5f4a3b2c1d0e"
        assertEquals(MusicTrackMedia.Audio(url, null), track(arrayOf("url", url)).media())
    }

    @Test
    fun formatTagKeepsAnMp4UrlOnTheAudioPath() {
        val url = "https://example.com/song.mp4"
        assertEquals(MusicTrackMedia.Audio(url, "audio/mp4"), track(arrayOf("url", url), arrayOf("format", "mp4")).media())
    }

    @Test
    fun videoTagWinsOverUrl() {
        val event = track(arrayOf("url", "https://example.com/song.mp3"), arrayOf("video", "https://example.com/clip.mp4"))
        assertEquals(MusicTrackMedia.Video("https://example.com/clip.mp4"), event.media())
    }

    @Test
    fun noUrlNoMedia() {
        assertNull(track(arrayOf("title", "Silence")).media())
    }
}
