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
package com.vitorpamplona.nestsclient

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class NestsRoomInfoTest {
    @Test
    fun parses_full_payload() {
        val info =
            NestsRoomInfo.parse(
                """{"endpoint":"https://relay.example.com/moq","token":"abc.def","codec":"opus","sample_rate":48000,"transport":"webtransport"}""",
            )
        assertEquals("https://relay.example.com/moq", info.endpoint)
        assertEquals("abc.def", info.token)
        assertEquals("opus", info.codec)
        assertEquals(48000, info.sampleRate)
        assertEquals("webtransport", info.transport)
    }

    @Test
    fun tolerates_minimal_payload() {
        val info = NestsRoomInfo.parse("""{"endpoint":"https://r.example.com/moq"}""")
        assertEquals("https://r.example.com/moq", info.endpoint)
        assertNull(info.token)
        assertNull(info.codec)
        assertNull(info.sampleRate)
    }

    @Test
    fun ignores_unknown_fields() {
        val info =
            NestsRoomInfo.parse(
                """{"endpoint":"https://r.example.com/moq","token":"t","future_knob":"future_value"}""",
            )
        assertEquals("t", info.token)
    }

    @Test
    fun rejects_missing_endpoint() {
        assertFailsWith<Exception> {
            NestsRoomInfo.parse("""{"token":"t"}""")
        }
    }

    @Test
    fun builds_room_info_url_from_service_and_room_id() {
        assertEquals(
            "https://nostrnests.com/api/v1/nests/abc-123",
            nestsRoomInfoUrl("https://nostrnests.com/api/v1/nests", "abc-123"),
        )
    }

    @Test
    fun trims_trailing_slash_from_service_base() {
        assertEquals(
            "https://nostrnests.com/api/v1/nests/xyz",
            nestsRoomInfoUrl("https://nostrnests.com/api/v1/nests/", "xyz"),
        )
    }

    @Test
    fun trims_whitespace_from_room_id() {
        assertEquals(
            "https://a.example.com/api/v1/nests/room",
            nestsRoomInfoUrl("https://a.example.com/api/v1/nests", "  room  "),
        )
    }
}
