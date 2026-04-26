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

import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Regression coverage for hostile-input handling at the packet layer.
 * Each test pins a class of bug an audit caught — if any of these starts
 * passing through, that's a re-regression.
 */
class HostilePacketInputTest {
    /**
     * RFC 9000 §17.2 caps CID length at 20 bytes. A hostile peer with
     * dcidLen=0xFF would, before the fix, cause `r.readBytes(255)` to
     * either crash or read 255 bytes of arbitrary buffer state.
     */
    @Test
    fun retry_packet_with_oversized_dcid_len_is_rejected() {
        // Build a "Retry" with dcidLen=255, scidLen=8 (legal), then 16 bytes of random tag.
        val w = com.vitorpamplona.quic.QuicWriter()
        w.writeByte(0xff)
        w.writeUint32(0x00000001) // version
        w.writeByte(0xff) // dcidLen = 255 — illegal
        w.writeBytes(ByteArray(8)) // not enough bytes for a 255-byte CID
        // Padding so we don't hit other underflow checks before the CID one.
        w.writeBytes(ByteArray(40))
        val parsed = RetryPacket.parse(w.toByteArray())
        assertNull(parsed, "Retry with dcidLen=255 must be rejected")
    }

    @Test
    fun retry_packet_with_oversized_scid_len_is_rejected() {
        val w = com.vitorpamplona.quic.QuicWriter()
        w.writeByte(0xff)
        w.writeUint32(0x00000001)
        w.writeByte(0)
        w.writeByte(0xff) // scidLen = 255 — illegal
        w.writeBytes(ByteArray(40))
        val parsed = RetryPacket.parse(w.toByteArray())
        assertNull(parsed)
    }

    /**
     * RFC 9001 §5.5: a peer that sends one bad packet inside a coalesced
     * datagram must NOT cause subsequent packets to be dropped. Before the
     * fix, [feedDatagram]'s `?: break` discarded everything after the first
     * unparseable header.
     *
     * We simulate by feeding a datagram whose Initial packet has bogus
     * DCID-len followed by an unrelated trailing packet. peekHeader returns
     * null on the bogus first byte → outer loop breaks.
     *
     * Specifically, this test asserts the post-fix behavior: when peekHeader
     * succeeds but decrypt fails, the loop advances by [PeekedHeader.totalLength]
     * rather than breaking. We can't easily fake "decrypt-fails-but-peek-succeeds"
     * in a unit test without crypto, so this tests the structural invariant
     * via [LongHeaderPacket.peekHeader] directly.
     */
    @Test
    fun peek_header_on_oversized_dcid_returns_null_so_caller_breaks() {
        val w = com.vitorpamplona.quic.QuicWriter()
        // Long-header type INITIAL, version 1, dcidLen = 21 (illegal: max 20)
        w.writeByte(0xC0)
        w.writeUint32(0x00000001)
        w.writeByte(21)
        w.writeBytes(ByteArray(50)) // pretend more bytes
        val peeked = LongHeaderPacket.peekHeader(w.toByteArray(), 0)
        assertNull(peeked, "peekHeader must reject dcidLen out of [0..20]")
    }

    @Test
    fun peek_header_on_oversized_scid_returns_null() {
        val w = com.vitorpamplona.quic.QuicWriter()
        w.writeByte(0xC0)
        w.writeUint32(0x00000001)
        w.writeByte(0) // dcidLen = 0
        w.writeByte(21) // scidLen = 21 — illegal
        w.writeBytes(ByteArray(50))
        val peeked = LongHeaderPacket.peekHeader(w.toByteArray(), 0)
        assertNull(peeked)
    }

    @Test
    fun peek_header_on_initial_with_oversized_token_len_returns_null() {
        val w = com.vitorpamplona.quic.QuicWriter()
        w.writeByte(0xC0)
        w.writeUint32(0x00000001)
        w.writeByte(0) // dcidLen
        w.writeByte(0) // scidLen
        // Token length varint: claim 0x4000_0000 (1 GiB)
        w.writeVarint(0x4000_0000L)
        // No bytes follow — varint length exceeds remaining
        val peeked = LongHeaderPacket.peekHeader(w.toByteArray(), 0)
        assertNull(peeked, "peekHeader must reject token length > remaining")
    }

    @Test
    fun retry_packet_peek_returns_total_length() {
        // A valid Retry: 0xff || 00000001 || 00 || 08 || cid(8) || token(0) || tag(16)
        val w = com.vitorpamplona.quic.QuicWriter()
        w.writeByte(0xff)
        w.writeUint32(0x00000001)
        w.writeByte(0)
        w.writeByte(8)
        w.writeBytes(ByteArray(8))
        // Retry token + tag = 16 bytes total (token is empty here for simplicity)
        w.writeBytes(ByteArray(16))
        val peeked = LongHeaderPacket.peekHeader(w.toByteArray(), 0)
        assertTrue(peeked != null, "peekHeader must accept a structurally-valid Retry")
        // Total length is bytes.size - offset (no `length` field in Retry)
        kotlin.test.assertEquals(w.toByteArray().size, peeked.totalLength)
    }
}
