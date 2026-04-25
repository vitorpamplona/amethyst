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
package com.vitorpamplona.quic.packet

import com.vitorpamplona.quic.connection.PacketNumberSpace

/** QUIC v1 long-header packet types per RFC 9000 §17.2. */
enum class LongHeaderType(
    val code: Int,
    val space: PacketNumberSpace,
) {
    INITIAL(0x00, PacketNumberSpace.INITIAL),
    ZERO_RTT(0x01, PacketNumberSpace.APPLICATION),
    HANDSHAKE(0x02, PacketNumberSpace.HANDSHAKE),
    RETRY(0x03, PacketNumberSpace.INITIAL), // retry has no PN space, INITIAL is a placeholder
    ;

    companion object {
        fun fromTypeBits(bits: Int): LongHeaderType =
            entries.firstOrNull { it.code == bits } ?: error("unknown long-header type bits: $bits")
    }
}

/** QUIC v1 versions we recognise. */
object QuicVersion {
    const val V1: Int = 0x00000001
    const val VERSION_NEGOTIATION: Int = 0x00000000
}
