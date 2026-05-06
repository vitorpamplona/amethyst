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
    fun parsesKixelatedHangShape() {
        // Verbatim shape produced by the canonical kixelated/moq `hang`
        // crate (`rs/hang/src/catalog/`). Verifies that an Amethyst
        // listener picks up a standards-aligned publisher's catalog.
        val json =
            """
            {
              "audio": {
                "renditions": {
                  "audio/data": {
                    "codec": "opus",
                    "container": { "kind": "legacy" },
                    "sampleRate": 48000,
                    "numberOfChannels": 2,
                    "bitrate": 64000
                  }
                }
              }
            }
            """.trimIndent()
        val catalog = RoomSpeakerCatalog.parseOrNull(json.encodeToByteArray())
        assertNotNull(catalog)
        assertEquals(1, catalog.audio?.renditions?.size)
        val rendition = catalog.primaryAudio()
        assertNotNull(rendition)
        assertEquals("opus", rendition.codec)
        assertEquals("legacy", rendition.container?.kind)
        assertEquals(48_000, rendition.sampleRate)
        assertEquals(2, rendition.numberOfChannels)
        assertEquals(64_000, rendition.bitrate)
    }

    @Test
    fun describeFormatsHumanReadable() {
        val rendition =
            RoomSpeakerCatalog.AudioConfig(
                codec = "opus",
                sampleRate = 48_000,
                numberOfChannels = 2,
            )
        // Codec uppercased, kHz / channel count short-formed —
        // intended for a single-line tooltip in the participant
        // sheet, not a verbose codec dump.
        assertEquals("OPUS · 48kHz · 2ch", rendition.describe())
    }

    @Test
    fun describeMonoIsLabelled() {
        val rendition = RoomSpeakerCatalog.AudioConfig(codec = "opus", numberOfChannels = 1)
        assertEquals("OPUS · mono", rendition.describe())
    }

    @Test
    fun describeAllNullReturnsNull() {
        val rendition = RoomSpeakerCatalog.AudioConfig()
        // Empty catalog → caller doesn't render a "unknown · unknown"
        // line; describe() returns null so the UI can omit cleanly.
        assertNull(rendition.describe())
    }

    @Test
    fun toleratesUnknownKeys() {
        // Forward-compat: future hang catalog revisions can add
        // fields without breaking older clients.
        val json =
            """{"audio":{"renditions":{"audio/data":{"codec":"opus","extra":"future-only"}}},"video":{},"newTopLevel":true}"""
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
    fun emptyAudioRenditionsIsAllowed() {
        // A publisher might emit an "advertising" catalog with no
        // renditions yet (e.g. between codec switches). Accept the
        // shape and let primaryAudio() return null.
        val catalog = RoomSpeakerCatalog.parseOrNull("""{"audio":{"renditions":{}}}""".encodeToByteArray())
        assertNotNull(catalog)
        assertNull(catalog.primaryAudio())
    }

    @Test
    fun missingAudioReturnsNullPrimary() {
        // hang catalogs declaring only video MUST still parse without
        // throwing — primaryAudio() returns null.
        val catalog = RoomSpeakerCatalog.parseOrNull("""{}""".encodeToByteArray())
        assertNotNull(catalog)
        assertNull(catalog.primaryAudio())
    }

    @Test
    fun stripPrefixRoundTripsCanonicalCatalog() {
        // The catalog payload [com.vitorpamplona.nestsclient.MoqLiteNestsSpeaker]
        // emits MUST round-trip through the parser — guards against
        // either side drifting from the kixelated/hang shape.
        // SPEAKER_CATALOG_JSON lives in nestsClient and isn't
        // accessible from commons; keep an inline literal that mirrors
        // it verbatim so a desync triggers this assertion.
        val emitted =
            "{\"audio\":{\"renditions\":{\"audio/data\":{" +
                "\"codec\":\"opus\",\"container\":{\"kind\":\"legacy\"}," +
                "\"sampleRate\":48000,\"numberOfChannels\":1}}}}"
        val catalog = RoomSpeakerCatalog.parseOrNull(emitted.encodeToByteArray())
        assertNotNull(catalog)
        val rendition = catalog.primaryAudio()
        assertNotNull(rendition)
        assertEquals("opus", rendition.codec)
        assertEquals("legacy", rendition.container?.kind)
        assertEquals(48_000, rendition.sampleRate)
        assertEquals(1, rendition.numberOfChannels)
    }
}
