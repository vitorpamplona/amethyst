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
package com.vitorpamplona.nestsclient.moq.lite

import kotlin.test.Test
import kotlin.test.assertEquals

class MoqLiteHangCatalogTest {
    @Test
    fun opusMono48kEmitsCanonicalHangShape() {
        // Byte-exact assertion: `:commons`'s `RoomSpeakerCatalogTest`
        // round-trips this same string against the parser. If either
        // side drifts from the kixelated/hang wire shape, both tests
        // fail at once.
        val expected =
            "{\"audio\":{\"renditions\":{\"audio/data\":{" +
                "\"codec\":\"opus\",\"container\":{\"kind\":\"legacy\"}," +
                "\"sampleRate\":48000,\"numberOfChannels\":1,\"jitter\":20}}}}"
        val actual =
            MoqLiteHangCatalog.opusMono48k("audio/data").encodeJsonBytes().decodeToString()
        assertEquals(expected, actual)
    }

    @Test
    fun renditionKeyMatchesCallerSuppliedTrackName() {
        val actual =
            MoqLiteHangCatalog.opusMono48k("custom/track").encodeJsonBytes().decodeToString()
        // The rendition map MUST be keyed on the caller-supplied track
        // name — the watcher uses this string verbatim as the
        // SUBSCRIBE.track on the audio subscription.
        assertEquals(true, actual.contains("\"custom/track\":{"))
    }
}
