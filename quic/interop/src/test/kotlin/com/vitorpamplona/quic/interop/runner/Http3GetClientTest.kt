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
package com.vitorpamplona.quic.interop.runner

import com.vitorpamplona.quic.http3.Http3Frame
import com.vitorpamplona.quic.http3.Http3FrameReader
import com.vitorpamplona.quic.qpack.QpackDecoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Http3GetClientTest {
    @Test
    fun `encodeRequest produces a single HEADERS frame with the four pseudo-headers`() {
        val bytes = encodeRequest(authority = "example.com", path = "/file.bin")

        val reader = Http3FrameReader().apply { push(bytes) }
        val frame = reader.next()
        assertTrue(frame is Http3Frame.Headers, "first frame must be HEADERS, got $frame")
        assertEquals(null, reader.next(), "should be exactly one frame")

        val fields = QpackDecoder().decodeFieldSection(frame.qpackPayload)
        // QPACK emits headers in order; confirm pseudo-headers come first
        // and carry the expected values.
        val map = fields.associate { it.first to it.second }
        assertEquals("GET", map[":method"])
        assertEquals("https", map[":scheme"])
        assertEquals("example.com", map[":authority"])
        assertEquals("/file.bin", map[":path"])
    }

    @Test
    fun `encodeRequest survives an authority with a non-default port`() {
        val bytes = encodeRequest(authority = "example.com:8443", path = "/")
        val frame = Http3FrameReader().apply { push(bytes) }.next()
        require(frame is Http3Frame.Headers)
        val map = QpackDecoder().decodeFieldSection(frame.qpackPayload).associate { it.first to it.second }
        assertEquals("example.com:8443", map[":authority"])
        assertEquals("/", map[":path"])
    }
}
