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
import com.vitorpamplona.quic.http3.Http3FrameType
import com.vitorpamplona.quic.qpack.QpackEncoder

/**
 * Build the headers for a WebTransport Extended CONNECT request per
 * RFC 9220 / draft-ietf-webtrans-http3.
 *
 *   :method = CONNECT
 *   :protocol = webtransport
 *   :scheme = https
 *   :authority = host[:port]
 *   :path = path
 *   [optional] authorization = Bearer <token>
 */
fun buildExtendedConnectHeaders(
    authority: String,
    path: String,
    bearerToken: String? = null,
    extra: List<Pair<String, String>> = emptyList(),
): List<Pair<String, String>> {
    val headers =
        mutableListOf(
            ":method" to "CONNECT",
            ":protocol" to "webtransport",
            ":scheme" to "https",
            ":authority" to authority,
            ":path" to path,
        )
    if (bearerToken != null) {
        headers += "authorization" to "Bearer $bearerToken"
    }
    headers += extra
    return headers
}

/** Encode a HEADERS frame body containing the given header list as QPACK bytes. */
fun encodeHeadersFrame(headers: List<Pair<String, String>>): ByteArray {
    val qpack = QpackEncoder().encodeFieldSection(headers)
    val w = QuicWriter()
    w.writeVarint(Http3FrameType.HEADERS)
    w.writeVarint(qpack.size.toLong())
    w.writeBytes(qpack)
    return w.toByteArray()
}
