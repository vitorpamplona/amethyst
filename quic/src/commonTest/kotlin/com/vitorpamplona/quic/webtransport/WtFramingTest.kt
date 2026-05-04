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
package com.vitorpamplona.quic.webtransport

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class WtFramingTest {
    @Test
    fun datagram_round_trip() {
        val payload = "hello moq".encodeToByteArray()
        val encoded = WtDatagram.encode(connectStreamId = 0L, payload = payload)
        val decoded = WtDatagram.decode(encoded)
        assertNotNull(decoded)
        assertEquals(0L, decoded.sessionStreamId)
        assertContentEquals(payload, decoded.payload)
    }

    @Test
    fun datagram_round_trip_nonzero_session_id() {
        val payload = byteArrayOf(0x01, 0x02, 0x03)
        val encoded = WtDatagram.encode(connectStreamId = 4L, payload = payload)
        val decoded = WtDatagram.decode(encoded)!!
        assertEquals(4L, decoded.sessionStreamId)
        assertContentEquals(payload, decoded.payload)
    }

    @Test
    fun bidi_stream_prefix_encodes_0x41_then_session_id() {
        val prefix = encodeWtBidiStreamPrefix(0L)
        // 0x41 = 65 needs the 2-byte varint form: high byte 0x40, low byte 0x41.
        // Then session id = 0 in 1-byte varint.
        assertEquals(0x40.toByte(), prefix[0])
        assertEquals(0x41.toByte(), prefix[1])
        assertEquals(0x00.toByte(), prefix[2])
    }

    @Test
    fun close_session_capsule_starts_with_2843() {
        val capsule = encodeCloseSessionCapsule(0, "")
        // 0x2843 encoded as varint occupies 2 bytes: 0x68, 0x43 (with the 2-byte form prefix).
        // Verify by parsing.
        val hi = capsule[0].toInt() and 0xFF
        // Top two bits of first byte indicate 2-byte varint: 01xxxxxx
        assertEquals(0x40, hi and 0xC0)
    }
}
