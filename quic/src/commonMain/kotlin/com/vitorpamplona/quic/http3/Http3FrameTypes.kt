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

/** HTTP/3 frame type identifiers per RFC 9114 §11.2 + WebTransport. */
object Http3FrameType {
    const val DATA: Long = 0x00
    const val HEADERS: Long = 0x01
    const val CANCEL_PUSH: Long = 0x03
    const val SETTINGS: Long = 0x04
    const val PUSH_PROMISE: Long = 0x05
    const val GOAWAY: Long = 0x07
    const val MAX_PUSH_ID: Long = 0x0D

    /** WebTransport BIDI stream prefix (per draft) — appears as a frame on a request stream. */
    const val WEBTRANSPORT_BIDI_STREAM: Long = 0x41
}

/** HTTP/3 unidirectional stream type prefixes per RFC 9114 §6.2. */
object Http3StreamType {
    const val CONTROL: Long = 0x00
    const val PUSH: Long = 0x01
    const val QPACK_ENCODER: Long = 0x02
    const val QPACK_DECODER: Long = 0x03
    /** WebTransport unidirectional stream type. */
    const val WEBTRANSPORT_UNI_STREAM: Long = 0x54
}

/** SETTINGS identifiers we emit / parse. */
object Http3SettingsId {
    const val QPACK_MAX_TABLE_CAPACITY: Long = 0x01
    const val MAX_FIELD_SECTION_SIZE: Long = 0x06
    const val QPACK_BLOCKED_STREAMS: Long = 0x07

    /** RFC 8441 — accept Extended CONNECT (`:protocol`). */
    const val ENABLE_CONNECT_PROTOCOL: Long = 0x08

    /** RFC 9297 — HTTP Datagrams. */
    const val H3_DATAGRAM: Long = 0x33

    /** WebTransport over HTTP/3 (draft / RFC 9220). */
    const val ENABLE_WEBTRANSPORT: Long = 0xc671706a
}
