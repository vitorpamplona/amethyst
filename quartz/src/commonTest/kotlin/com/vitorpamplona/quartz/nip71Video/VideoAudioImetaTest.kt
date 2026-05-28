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
package com.vitorpamplona.quartz.nip71Video

import com.vitorpamplona.quartz.nip71Video.tags.LanguageImetaTag
import com.vitorpamplona.quartz.nip92IMeta.IMetaTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VideoAudioImetaTest {
    /**
     * NIP-71 PR #2255 spec example for an audio-only imeta variant.
     */
    @Test
    fun audioImetaRoundTripMatchesSpecExample() {
        val waveform =
            listOf(
                0f,
                7f,
                35f,
                8f,
                100f,
                100f,
                49f,
                8f,
                4f,
                16f,
                8f,
                10f,
                7f,
                2f,
                20f,
                10f,
                100f,
                100f,
                100f,
                100f,
            )

        val original =
            VideoMeta(
                url = "https://myaudio.com/audio/en/12345.mp3",
                mimeType = "audio/mp3",
                hash = "b2e0a7a82ac9f3f3a71f1d9a78c381d5be9d1cf19dce258765c17c8a76287c93",
                service = "nip96",
                fallback =
                    listOf(
                        "https://myotherserver.com/audio/en/12345.mp3",
                        "https://andanotherserver.com/audio/en/12345.mp3",
                    ),
                bitrate = 320000,
                duration = 29.24,
                waveform = waveform,
                language = LanguageImetaTag("en", "ISO-639-1", originalVersion = true),
            )

        val tag = original.toIMetaArray()
        val parsed = IMetaTag.parse(tag)?.firstOrNull()
        assertNotNull(parsed)
        val round = VideoMeta.parse(parsed)

        assertEquals(original.url, round.url)
        assertEquals("audio/mp3", round.mimeType)
        assertEquals(original.hash, round.hash)
        assertEquals("nip96", round.service)
        assertEquals(original.fallback, round.fallback)
        assertEquals(320000, round.bitrate)
        assertEquals(29.24, round.duration)
        assertEquals(waveform, round.waveform)
        assertEquals(LanguageImetaTag("en", "ISO-639-1", originalVersion = true), round.language)
        assertTrue(round.isAudio)
    }

    @Test
    fun audioAndVideoTracksAreSplitByMimeType() {
        val video =
            VideoMeta(
                url = "https://example.com/video.mp4",
                mimeType = "video/mp4",
            )
        val audio =
            VideoMeta(
                url = "https://example.com/audio.mp3",
                mimeType = "audio/mp3",
                language = LanguageImetaTag("en"),
            )

        assertTrue(video.isVideo)
        assertTrue(audio.isAudio)
    }

    @Test
    fun languageImetaTagParsesOriginalVersionFlag() {
        val parsed = LanguageImetaTag.parseValue("en ISO-639-1 ov")
        assertNotNull(parsed)
        assertEquals("en", parsed.code)
        assertEquals("ISO-639-1", parsed.standard)
        assertTrue(parsed.originalVersion)
    }

    @Test
    fun languageImetaTagDefaultsStandardWhenMissing() {
        val parsed = LanguageImetaTag.parseValue("pt")
        assertNotNull(parsed)
        assertEquals("pt", parsed.code)
        assertEquals("ISO-639-1", parsed.standard)
        assertEquals(false, parsed.originalVersion)
    }
}
