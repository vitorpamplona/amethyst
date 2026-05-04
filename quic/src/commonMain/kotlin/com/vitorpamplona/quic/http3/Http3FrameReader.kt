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

import com.vitorpamplona.quic.QuicCodecException
import com.vitorpamplona.quic.Varint

/**
 * Stateful HTTP/3 frame reader (RFC 9114 §7).
 *
 * Each HTTP/3 frame on a stream is `(varint type)(varint length)(body[length])`.
 * The reader buffers inbound bytes and yields complete frames; partial frames
 * stay buffered until enough bytes arrive.
 *
 * Use one [Http3FrameReader] per HTTP/3 stream. Feed bytes via [push]; drain
 * frames via [next] until it returns null.
 *
 * Unknown frame types (per RFC 9114 §9 — "frame types in the format and intent
 * not recognized SHOULD be ignored") are surfaced as [Http3Frame.Unknown] so
 * the caller can decide policy. SETTINGS, HEADERS, DATA are typed.
 */
class Http3FrameReader {
    private var buf: ByteArray = ByteArray(0)
    private var pos: Int = 0

    fun push(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        // Amortized compaction: only shift bytes down when the consumed
        // prefix is at least half the buffer. Otherwise we'd do O(N) memcpy
        // on every chunk → O(N²) total over a long stream.
        if (pos * 2 > buf.size) {
            buf = buf.copyOfRange(pos, buf.size)
            pos = 0
        }
        val combined = ByteArray(buf.size + bytes.size)
        buf.copyInto(combined, 0)
        bytes.copyInto(combined, buf.size)
        buf = combined
    }

    /** Pop the next complete frame, or null if the buffer doesn't contain one yet. */
    fun next(): Http3Frame? {
        val typeRes = Varint.decode(buf, pos) ?: return null
        val typeEnd = pos + typeRes.bytesConsumed
        val lenRes = Varint.decode(buf, typeEnd) ?: return null
        val bodyStart = typeEnd + lenRes.bytesConsumed
        val len = lenRes.value
        if (len < 0 || len > Int.MAX_VALUE.toLong()) {
            throw QuicCodecException("HTTP/3 frame length out of range: $len")
        }
        val bodyEnd = bodyStart + len.toInt()
        if (bodyEnd > buf.size) return null // not all body bytes present yet
        val body = buf.copyOfRange(bodyStart, bodyEnd)
        pos = bodyEnd
        return when (typeRes.value) {
            Http3FrameType.DATA -> Http3Frame.Data(body)
            Http3FrameType.HEADERS -> Http3Frame.Headers(body)
            Http3FrameType.SETTINGS -> Http3Frame.Settings(Http3Settings.decodeBody(body))
            Http3FrameType.GOAWAY -> Http3Frame.Goaway(body)
            else -> Http3Frame.Unknown(typeRes.value, body)
        }
    }
}

/** A parsed HTTP/3 frame. */
sealed class Http3Frame {
    /** RFC 9114 §7.2.1 DATA frame body. */
    data class Data(
        val body: ByteArray,
    ) : Http3Frame()

    /** RFC 9114 §7.2.2 HEADERS frame body — QPACK-encoded field section. */
    data class Headers(
        val qpackPayload: ByteArray,
    ) : Http3Frame()

    /** RFC 9114 §7.2.4 SETTINGS frame parsed body. */
    data class Settings(
        val settings: Http3Settings,
    ) : Http3Frame()

    /** RFC 9114 §7.2.6 GOAWAY frame body — single varint stream id (raw). */
    data class Goaway(
        val body: ByteArray,
    ) : Http3Frame()

    /** Frame whose type is unknown to us; per RFC 9114 §9, ignore unless on a reserved type. */
    data class Unknown(
        val type: Long,
        val body: ByteArray,
    ) : Http3Frame()
}
