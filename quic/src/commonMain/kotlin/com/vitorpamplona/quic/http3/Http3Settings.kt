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

import com.vitorpamplona.quic.QuicReader
import com.vitorpamplona.quic.QuicWriter

/**
 * HTTP/3 SETTINGS frame body — a sequence of `(id, value)` varint pairs per
 * RFC 9114 §7.2.4. Frame type 0x04, length-prefixed payload.
 */
data class Http3Settings(
    val settings: Map<Long, Long>,
) {
    fun encodeBody(): ByteArray {
        val w = QuicWriter()
        for ((id, value) in settings) {
            w.writeVarint(id)
            w.writeVarint(value)
        }
        return w.toByteArray()
    }

    fun encodeFrame(): ByteArray {
        val body = encodeBody()
        val w = QuicWriter()
        w.writeVarint(Http3FrameType.SETTINGS)
        w.writeVarint(body.size.toLong())
        w.writeBytes(body)
        return w.toByteArray()
    }

    companion object {
        fun decodeBody(body: ByteArray): Http3Settings {
            val map = mutableMapOf<Long, Long>()
            val r = QuicReader(body)
            while (r.hasMore()) {
                val id = r.readVarint()
                val value = r.readVarint()
                // Audit-4 #18: RFC 9114 §7.2.4.1 — duplicate SETTINGS ids
                // MUST cause a connection error of type H3_SETTINGS_ERROR.
                // Pre-fix the second value silently overwrote the first.
                if (map.containsKey(id)) {
                    throw com.vitorpamplona.quic.QuicCodecException(
                        "duplicate HTTP/3 SETTINGS id 0x${id.toString(16)}",
                    )
                }
                map[id] = value
            }
            return Http3Settings(map)
        }
    }
}

/**
 * Build the SETTINGS frame a WebTransport-over-HTTP/3 client sends on its
 * control stream, advertising:
 *   - SETTINGS_ENABLE_CONNECT_PROTOCOL = 1 (RFC 8441)
 *   - SETTINGS_H3_DATAGRAM = 1 (RFC 9297)
 *   - SETTINGS_ENABLE_WEBTRANSPORT = 1 (RFC 9220 / draft)
 */
fun buildClientWebTransportSettings(): Http3Settings =
    Http3Settings(
        mapOf(
            Http3SettingsId.ENABLE_CONNECT_PROTOCOL to 1L,
            Http3SettingsId.H3_DATAGRAM to 1L,
            Http3SettingsId.ENABLE_WEBTRANSPORT to 1L,
        ),
    )
