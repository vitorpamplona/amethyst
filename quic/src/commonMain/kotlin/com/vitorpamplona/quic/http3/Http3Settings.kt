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
                // RFC 9114 §7.2.4.1 / RFC 9204 §5: per-id range checks.
                // A peer that advertises e.g. `MAX_FIELD_SECTION_SIZE =
                // 2^60` could otherwise drive the encoder into
                // unbounded heap (we'd emit headers up to that cap in a
                // single allocation). Bounds chosen to comfortably
                // exceed any legitimate value while staying inside
                // [Int.MAX_VALUE] for safe `.toInt()` casts at use
                // sites.
                validateValue(id, value)
                map[id] = value
            }
            return Http3Settings(map)
        }

        /**
         * Reject obviously-malicious SETTINGS values per the relevant
         * RFCs. Negative varints are already impossible (varint is
         * unsigned), but we double-check defensively.
         */
        private fun validateValue(
            id: Long,
            value: Long,
        ) {
            // RFC 9114 §7.2.4.1: SETTINGS identifiers in the HTTP/2 range
            // (0x02, 0x03, 0x04, 0x05) are reserved to prevent confusion
            // with HTTP/2 settings — receipt MUST be a connection error of
            // type H3_SETTINGS_ERROR. Pre-fix we accepted them silently.
            if (id == 0x02L || id == 0x03L || id == 0x04L || id == 0x05L) {
                throw com.vitorpamplona.quic.QuicCodecException(
                    "H3_SETTINGS_ERROR: reserved HTTP/2 SETTINGS id 0x${id.toString(16)}",
                )
            }
            if (value < 0L) {
                throw com.vitorpamplona.quic.QuicCodecException(
                    "negative HTTP/3 SETTINGS value for id 0x${id.toString(16)}: $value",
                )
            }
            // Per-id sanity caps. Unknown ids fall through (RFC 9114
            // §7.2.4.1 says unknown SETTINGS MUST be ignored, but we
            // still bound the value to avoid attacker-controlled
            // long-tail allocation if any consumer ever uses the
            // unknown id directly).
            val cap: Long =
                when (id) {
                    Http3SettingsId.QPACK_MAX_TABLE_CAPACITY -> 1L shl 30

                    // 1 GiB
                    Http3SettingsId.MAX_FIELD_SECTION_SIZE -> 1L shl 30

                    Http3SettingsId.QPACK_BLOCKED_STREAMS -> 65535L

                    Http3SettingsId.ENABLE_CONNECT_PROTOCOL -> 1L

                    Http3SettingsId.H3_DATAGRAM -> 1L

                    Http3SettingsId.ENABLE_WEBTRANSPORT -> 1L

                    else -> 1L shl 32 // generic cap for unknown ids
                }
            if (value > cap) {
                throw com.vitorpamplona.quic.QuicCodecException(
                    "HTTP/3 SETTINGS id 0x${id.toString(16)} value $value exceeds cap $cap",
                )
            }
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
