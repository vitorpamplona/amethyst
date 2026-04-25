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

import com.vitorpamplona.quic.QuicReader
import com.vitorpamplona.quic.QuicWriter
import com.vitorpamplona.quic.Varint

/**
 * WebTransport datagram framing per RFC 9297 + draft-ietf-webtrans-http3.
 *
 * An HTTP/3 datagram payload starts with a varint *quarter stream id* — the
 * stream id of the WT session's CONNECT bidi divided by 4. Followed by the
 * application-level WT datagram bytes.
 */
object WtDatagram {
    fun encode(
        connectStreamId: Long,
        payload: ByteArray,
    ): ByteArray {
        require(connectStreamId % 4 == 0L) {
            "WT session stream id must be a client-initiated bidi (id % 4 == 0): $connectStreamId"
        }
        val quarter = connectStreamId / 4
        val w = QuicWriter()
        w.writeVarint(quarter)
        w.writeBytes(payload)
        return w.toByteArray()
    }

    /** Returns (quarterStreamId * 4, payload). Returns null on truncation. */
    fun decode(bytes: ByteArray): Decoded? {
        val r = Varint.decode(bytes, 0) ?: return null
        if (r.bytesConsumed > bytes.size) return null
        val sessionId = r.value * 4
        val payload = bytes.copyOfRange(r.bytesConsumed, bytes.size)
        return Decoded(sessionId, payload)
    }

    data class Decoded(
        val sessionStreamId: Long,
        val payload: ByteArray,
    )
}

/** WebTransport stream type prefixes (sent as the first varint on the QUIC stream). */
object WtStreamType {
    /** Client-initiated bidirectional WT stream — followed by quarter session id. */
    const val WT_BIDI_STREAM: Long = 0x41
    /** Client-initiated unidirectional WT stream — preceded by HTTP/3 stream-type 0x54 + quarter session id. */
    const val WT_UNI_STREAM_PREFIX: Long = 0x54
}

/** Encode the prefix bytes that go on a freshly-opened WebTransport bidi stream. */
fun encodeWtBidiStreamPrefix(connectStreamId: Long): ByteArray {
    require(connectStreamId % 4 == 0L)
    val w = QuicWriter()
    w.writeVarint(WtStreamType.WT_BIDI_STREAM)
    w.writeVarint(connectStreamId / 4)
    return w.toByteArray()
}

/** Encode the prefix bytes that go on a freshly-opened WebTransport unidi stream. */
fun encodeWtUniStreamPrefix(connectStreamId: Long): ByteArray {
    require(connectStreamId % 4 == 0L)
    val w = QuicWriter()
    w.writeVarint(WtStreamType.WT_UNI_STREAM_PREFIX)
    w.writeVarint(connectStreamId / 4)
    return w.toByteArray()
}
