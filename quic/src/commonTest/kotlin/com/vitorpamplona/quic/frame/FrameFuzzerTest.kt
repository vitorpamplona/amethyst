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
package com.vitorpamplona.quic.frame

import com.vitorpamplona.quic.QuicCodecException
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.fail

/**
 * Property-based fuzzing of [decodeFrames]. The contract is simple:
 *
 *   For ANY byte sequence, [decodeFrames] must either succeed or throw
 *   [QuicCodecException]. It MUST NOT crash with OOM, infinite-loop,
 *   IndexOutOfBoundsException, NumberFormatException, etc.
 *
 * A QUIC client receives whatever bytes a malicious or buggy peer sends
 * after AEAD authentication. RFC 9000 §10 / §12.4 mandates closing with
 * FRAME_ENCODING_ERROR / PROTOCOL_VIOLATION on any framing fault — but
 * the codec itself must surface the fault as a typed exception, never as
 * a JVM crash.
 *
 * The fuzzer is deterministic (fixed seed) so any failure can be replayed.
 */
class FrameFuzzerTest {
    @Test
    fun random_bytes_never_crash() {
        val rng = Random(0xCAFEBABEL)
        repeat(2000) { iteration ->
            val len = rng.nextInt(0, 4096)
            val payload = ByteArray(len).also { rng.nextBytes(it) }
            try {
                decodeFrames(payload)
            } catch (_: QuicCodecException) {
                // expected on malformed input
            } catch (t: Throwable) {
                fail("decodeFrames($len bytes) on iteration $iteration threw ${t::class.simpleName}: ${t.message}")
            }
        }
    }

    @Test
    fun frame_with_oversized_length_is_rejected() {
        // STREAM frame (type 0x0a = OFF|LEN, no FIN) with stream_id=0,
        // offset=0, length=2^62-1, then no body bytes. Must throw, not OOM.
        val w = com.vitorpamplona.quic.QuicWriter()
        w.writeByte(0x0e) // STREAM with OFF + LEN + FIN bits set (0x0e = 0x08+0x06)
        w.writeVarint(0L) // streamId
        w.writeVarint(0L) // offset
        w.writeVarint(com.vitorpamplona.quic.Varint.MAX_VALUE) // length: ~4.6 quintillion
        // intentionally no body bytes
        try {
            decodeFrames(w.toByteArray())
            fail("expected QuicCodecException on oversized STREAM length")
        } catch (_: QuicCodecException) {
            // good
        }
    }

    @Test
    fun crypto_frame_with_oversized_length_is_rejected() {
        val w = com.vitorpamplona.quic.QuicWriter()
        w.writeByte(0x06) // CRYPTO
        w.writeVarint(0L) // offset
        w.writeVarint(0x4000_0000L) // length 1 GiB but only 0 bytes follow
        try {
            decodeFrames(w.toByteArray())
            fail("expected QuicCodecException on oversized CRYPTO length")
        } catch (_: QuicCodecException) {
            // good
        }
    }

    @Test
    fun ack_frame_with_excessive_range_count_is_rejected() {
        val w = com.vitorpamplona.quic.QuicWriter()
        w.writeByte(0x02) // ACK
        w.writeVarint(0L) // largest_acknowledged
        w.writeVarint(0L) // ack_delay
        w.writeVarint(0x4000_0000L) // numRanges = 1B (would allocate gigabytes)
        w.writeVarint(0L) // first_ack_range
        try {
            decodeFrames(w.toByteArray())
            fail("expected QuicCodecException on excessive ACK range count")
        } catch (_: QuicCodecException) {
            // good
        }
    }

    @Test
    fun new_connection_id_with_invalid_cid_length_is_rejected() {
        // RFC 9000 §19.15: cidLen MUST be 1..20.
        val w = com.vitorpamplona.quic.QuicWriter()
        w.writeByte(0x18) // NEW_CONNECTION_ID
        w.writeVarint(1L) // sequence
        w.writeVarint(0L) // retire_prior_to
        w.writeByte(0xFF) // cidLen = 255 (illegal)
        // Buffer remaining bytes too short to satisfy the CID claim — we expect rejection.
        try {
            decodeFrames(w.toByteArray())
            fail("expected QuicCodecException on illegal NCID length")
        } catch (_: QuicCodecException) {
            // good
        }
    }

    @Test
    fun connection_close_with_oversized_reason_length_is_rejected() {
        val w = com.vitorpamplona.quic.QuicWriter()
        w.writeByte(0x1c) // CONNECTION_CLOSE_TRANSPORT
        w.writeVarint(0L) // err
        w.writeVarint(0L) // frameType
        w.writeVarint(0x4000_0000L) // reason length 1GB but no body
        try {
            decodeFrames(w.toByteArray())
            fail("expected QuicCodecException on oversized CONNECTION_CLOSE reason")
        } catch (_: QuicCodecException) {
            // good
        }
    }

    @Test
    fun datagram_len_with_oversized_length_is_rejected() {
        val w = com.vitorpamplona.quic.QuicWriter()
        w.writeByte(0x31) // DATAGRAM_LEN
        w.writeVarint(0x4000_0000L) // 1GB
        try {
            decodeFrames(w.toByteArray())
            fail("expected QuicCodecException on oversized DATAGRAM length")
        } catch (_: QuicCodecException) {
            // good
        }
    }

    @Test
    fun valid_frame_followed_by_garbage_throws_on_garbage() {
        // Valid PING (0x01) then a byte that's not a valid frame type at all
        // (0x40 with no continuation — actually a 2-byte varint; let's try 0x60 = unknown 1-byte type).
        val payload = byteArrayOf(0x01, 0x60)
        try {
            decodeFrames(payload)
            fail("expected QuicCodecException on unknown frame type")
        } catch (_: QuicCodecException) {
            // good
        }
    }
}
