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
package com.vitorpamplona.quic.http3

import com.vitorpamplona.quic.webtransport.encodeHeadersFrame
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Http3FrameReaderTest {
    @Test
    fun parses_settings_frame_round_trip() {
        val settings = buildClientWebTransportSettings()
        val r = Http3FrameReader()
        r.push(settings.encodeFrame())
        val first = r.next()
        assertTrue(first is Http3Frame.Settings)
        assertEquals(1L, first.settings.settings[Http3SettingsId.ENABLE_CONNECT_PROTOCOL])
        assertNull(r.next())
    }

    @Test
    fun parses_headers_frame_round_trip() {
        val headers = listOf(":status" to "200", "server" to "nests")
        val frame = encodeHeadersFrame(headers)
        val r = Http3FrameReader()
        r.push(frame)
        val first = r.next()
        assertTrue(first is Http3Frame.Headers)
    }

    @Test
    fun split_frame_across_pushes_reassembles() {
        val frame = encodeHeadersFrame(listOf(":status" to "200"))
        val r = Http3FrameReader()
        r.push(frame.copyOfRange(0, 1))
        assertNull(r.next(), "no full frame yet")
        r.push(frame.copyOfRange(1, frame.size))
        assertTrue(r.next() is Http3Frame.Headers)
    }

    @Test
    fun unknown_frame_type_is_surfaced_for_caller_to_skip() {
        // Build a frame with type 0x21 (reserved/unknown) and a 3-byte body.
        val r = Http3FrameReader()
        r.push(byteArrayOf(0x21, 0x03, 0x01, 0x02, 0x03))
        val first = r.next()
        assertTrue(first is Http3Frame.Unknown)
        assertEquals(0x21L, first.type)
        assertContentEquals(byteArrayOf(0x01, 0x02, 0x03), first.body)
    }

    @Test
    fun multiple_frames_in_one_push() {
        val a = encodeHeadersFrame(listOf(":status" to "200"))
        val b =
            run {
                val w = com.vitorpamplona.quic.QuicWriter()
                w.writeVarint(Http3FrameType.DATA)
                w.writeVarint(4L)
                w.writeBytes(byteArrayOf(0x01, 0x02, 0x03, 0x04))
                w.toByteArray()
            }
        val r = Http3FrameReader()
        r.push(a + b)
        val first = r.next()
        assertTrue(first is Http3Frame.Headers)
        val second = r.next()
        assertTrue(second is Http3Frame.Data)
        assertContentEquals(byteArrayOf(0x01, 0x02, 0x03, 0x04), second.body)
        assertNull(r.next())
    }
}
