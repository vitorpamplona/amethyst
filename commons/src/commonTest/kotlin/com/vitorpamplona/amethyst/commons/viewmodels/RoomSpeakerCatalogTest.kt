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
package com.vitorpamplona.amethyst.commons.viewmodels

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RoomSpeakerCatalogTest {
    @Test
    fun parsesNostrnestsShape() {
        val json =
            """
            {
              "version": 1,
              "audio": [
                {
                  "track": "audio/data",
                  "codec": "opus",
                  "sample_rate": 48000,
                  "channel_count": 2,
                  "bitrate": 64000
                }
              ]
            }
            """.trimIndent()
        val catalog = RoomSpeakerCatalog.parseOrNull(json.encodeToByteArray())
        assertNotNull(catalog)
        assertEquals(1, catalog.version)
        assertEquals(1, catalog.audio.size)
        val track = catalog.primaryAudio()
        assertNotNull(track)
        assertEquals("audio/data", track.track)
        assertEquals("opus", track.codec)
        assertEquals(48_000, track.sampleRate)
        assertEquals(2, track.channelCount)
        assertEquals(64_000, track.bitrate)
    }

    @Test
    fun describeFormatsHumanReadable() {
        val track =
            RoomSpeakerCatalog.AudioTrack(
                codec = "opus",
                sampleRate = 48_000,
                channelCount = 2,
            )
        // Codec uppercased, kHz / channel count short-formed —
        // intended for a single-line tooltip in the participant
        // sheet, not a verbose codec dump.
        assertEquals("OPUS · 48kHz · 2ch", track.describe())
    }

    @Test
    fun describeMonoIsLabelled() {
        val track = RoomSpeakerCatalog.AudioTrack(codec = "opus", channelCount = 1)
        assertEquals("OPUS · mono", track.describe())
    }

    @Test
    fun describeAllNullReturnsNull() {
        val track = RoomSpeakerCatalog.AudioTrack()
        // Empty catalog → caller doesn't render a "unknown · unknown"
        // line; describe() returns null so the UI can omit cleanly.
        assertNull(track.describe())
    }

    @Test
    fun toleratesUnknownKeys() {
        // Forward-compat: future moq-lite catalog revisions can add
        // fields without breaking older clients.
        val json =
            """{"version":2,"audio":[{"track":"audio/data","codec":"opus","extra":"future-only"}],"new_top_level":true}"""
        val catalog = RoomSpeakerCatalog.parseOrNull(json.encodeToByteArray())
        assertNotNull(catalog)
        assertEquals("opus", catalog.primaryAudio()?.codec)
    }

    @Test
    fun garbageBytesReturnNull() {
        // An invalid UTF-8 sequence or non-JSON payload should yield
        // null rather than throw — the catalog channel is best-effort.
        val catalog = RoomSpeakerCatalog.parseOrNull(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00))
        assertNull(catalog)
    }

    @Test
    fun emptyAudioListIsAllowed() {
        // A publisher might emit an "advertising" catalog with no
        // tracks yet (e.g. between codec switches). Accept the shape
        // and let primaryAudio() return null.
        val catalog = RoomSpeakerCatalog.parseOrNull("""{"version":1,"audio":[]}""".encodeToByteArray())
        assertNotNull(catalog)
        assertNull(catalog.primaryAudio())
    }
}
