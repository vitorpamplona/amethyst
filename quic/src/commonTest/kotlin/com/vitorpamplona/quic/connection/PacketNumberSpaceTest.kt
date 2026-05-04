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
package com.vitorpamplona.quic.connection

import kotlin.test.Test
import kotlin.test.assertEquals

class PacketNumberSpaceTest {
    @Test
    fun rfc9000_a3_decode_example() {
        // RFC 9000 Appendix A.3: largest received = 0xa82f30ea, truncated wire = 0x9b32, len=2
        // Expected decoded value: 0xa82f9b32
        assertEquals(
            0xa82f9b32L,
            PacketNumberSpaceState.decodePacketNumber(
                largestReceived = 0xa82f30eaL,
                truncatedPn = 0x9b32L,
                pnLen = 2,
            ),
        )
    }

    @Test
    fun decode_first_packet_starts_at_truncated() {
        // No packets received → largestReceived = -1 → expected pn = 0
        // Server sends pn=0, on the wire as 1-byte 0x00
        assertEquals(
            0L,
            PacketNumberSpaceState.decodePacketNumber(
                largestReceived = -1L,
                truncatedPn = 0L,
                pnLen = 1,
            ),
        )
        // Then pn=1
        assertEquals(
            1L,
            PacketNumberSpaceState.decodePacketNumber(
                largestReceived = 0L,
                truncatedPn = 1L,
                pnLen = 1,
            ),
        )
    }

    @Test
    fun outbound_allocation_is_monotonic() {
        val s = PacketNumberSpaceState()
        assertEquals(0L, s.allocateOutbound())
        assertEquals(1L, s.allocateOutbound())
        assertEquals(2L, s.allocateOutbound())
        assertEquals(3L, s.nextPacketNumber)
    }

    @Test
    fun inbound_observation_tracks_max() {
        val s = PacketNumberSpaceState()
        s.observeInbound(5L, 100L)
        assertEquals(5L, s.largestReceived)
        s.observeInbound(3L, 200L)
        assertEquals(5L, s.largestReceived) // out of order, no update
        assertEquals(100L, s.largestReceivedTime)
        s.observeInbound(7L, 300L)
        assertEquals(7L, s.largestReceived)
        assertEquals(300L, s.largestReceivedTime)
    }

    @Test
    fun encodeLength_picks_minimum() {
        // First packet, no acks: needs at least 1 byte
        assertEquals(1, PacketNumberSpaceState.encodeLength(0L, -1L))
        assertEquals(1, PacketNumberSpaceState.encodeLength(127L, -1L))
        // 2 bytes when 8-bit window not enough
        assertEquals(2, PacketNumberSpaceState.encodeLength(256L, -1L))
        // 3 bytes when 16-bit window not enough (needs 17+ bits for 2× margin)
        assertEquals(3, PacketNumberSpaceState.encodeLength(0xFFFFL, 0L))
        // 4 bytes for very large gaps
        assertEquals(4, PacketNumberSpaceState.encodeLength(0x80_00_00_00L, -1L))
    }
}
