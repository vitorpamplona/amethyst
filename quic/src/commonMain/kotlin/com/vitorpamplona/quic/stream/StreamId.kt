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
package com.vitorpamplona.quic.stream

/**
 * Helpers for QUIC stream-id semantics per RFC 9000 §2.1.
 *
 * The two low bits of a stream id encode:
 *   bit 0: 0 = client-initiated, 1 = server-initiated
 *   bit 1: 0 = bidirectional,    1 = unidirectional
 */
object StreamId {
    fun isClientInitiated(id: Long) = (id and 0x01L) == 0L

    fun isBidirectional(id: Long) = (id and 0x02L) == 0L

    fun isUnidirectional(id: Long) = (id and 0x02L) != 0L

    fun isServerInitiated(id: Long) = (id and 0x01L) != 0L

    enum class Kind {
        CLIENT_BIDI,
        SERVER_BIDI,
        CLIENT_UNI,
        SERVER_UNI,
    }

    fun kindOf(id: Long): Kind =
        when (id and 0x03L) {
            0x00L -> Kind.CLIENT_BIDI
            0x01L -> Kind.SERVER_BIDI
            0x02L -> Kind.CLIENT_UNI
            0x03L -> Kind.SERVER_UNI
            else -> error("unreachable")
        }

    /** Build the n-th stream id of [kind] (n starts at 0). */
    fun build(
        kind: Kind,
        index: Long,
    ): Long =
        when (kind) {
            Kind.CLIENT_BIDI -> index shl 2
            Kind.SERVER_BIDI -> (index shl 2) or 0x01L
            Kind.CLIENT_UNI -> (index shl 2) or 0x02L
            Kind.SERVER_UNI -> (index shl 2) or 0x03L
        }
}
