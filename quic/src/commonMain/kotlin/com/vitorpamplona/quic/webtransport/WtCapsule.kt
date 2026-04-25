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

import com.vitorpamplona.quic.QuicWriter

/** WebTransport capsule type identifiers (draft-ietf-webtrans-http3 / RFC 9220). */
object WtCapsuleType {
    /** WT_CLOSE_SESSION — graceful close on the CONNECT bidi. */
    const val WT_CLOSE_SESSION: Long = 0x2843

    /** WT_DRAIN_SESSION — peer signals it will not open new streams. */
    const val WT_DRAIN_SESSION: Long = 0x78AE
}

/** Encode a `(type, body)` capsule per the HTTP capsule protocol (draft-ietf-httpbis-h3-datagram). */
fun encodeCapsule(
    type: Long,
    body: ByteArray,
): ByteArray {
    val w = QuicWriter()
    w.writeVarint(type)
    w.writeVarint(body.size.toLong())
    w.writeBytes(body)
    return w.toByteArray()
}

/** Encode a WT_CLOSE_SESSION capsule with optional application error code + reason. */
fun encodeCloseSessionCapsule(
    errorCode: Int = 0,
    reason: String = "",
): ByteArray {
    val body = QuicWriter()
    body.writeUint32(errorCode)
    body.writeBytes(reason.encodeToByteArray())
    return encodeCapsule(WtCapsuleType.WT_CLOSE_SESSION, body.toByteArray())
}
