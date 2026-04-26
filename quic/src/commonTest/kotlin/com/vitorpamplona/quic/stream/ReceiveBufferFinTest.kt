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

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Audit-4 #4 regression: closing the consumer-facing channel on FIN-frame
 * arrival without checking that the contiguous read frontier reached the
 * FIN offset would silently drop later-arriving fill chunks. The fix is the
 * `isFullyRead()` helper on [ReceiveBuffer] that the parser now consults
 * before calling `closeIncoming`.
 *
 * These tests pin the bookkeeping so a future refactor can't accidentally
 * collapse the "FIN seen" and "everything delivered" states back together.
 */
class ReceiveBufferFinTest {
    @Test
    fun isFullyRead_is_false_when_fin_arrives_before_gap_is_filled() {
        val buf = ReceiveBuffer()
        // Bytes 0..4
        buf.insert(0L, byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04), fin = false)
        // FIN-bearing frame at offset 10..14 — 5..9 still missing.
        buf.insert(10L, byteArrayOf(0x10, 0x11, 0x12, 0x13, 0x14), fin = true)

        assertTrue(buf.finReceived, "FIN flag must be observed")
        assertEquals(15L, buf.finOffset, "finOffset = offset + data.size of the FIN-bearing frame")
        // Drain available contiguous bytes (only 0..4).
        assertContentEquals(byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04), buf.readContiguous())
        // Pre-fix: parser would call closeIncoming here because finReceived
        // is true. isFullyRead() now correctly says no — the 5..9 chunks
        // are still pending.
        assertFalse(buf.isFullyRead(), "FIN-with-gap must not be reported as fully read")
        assertEquals(5L, buf.contiguousEnd())
    }

    @Test
    fun isFullyRead_becomes_true_only_after_gap_fills_and_data_is_drained() {
        val buf = ReceiveBuffer()
        buf.insert(0L, byteArrayOf(0x00), fin = false)
        buf.insert(2L, byteArrayOf(0x02), fin = true)
        assertFalse(buf.isFullyRead(), "gap at offset 1 keeps stream not fully read")
        // Fill the gap.
        buf.insert(1L, byteArrayOf(0x01), fin = false)
        // Drain everything — readContiguous walks chunks one at a time.
        val drained = mutableListOf<Byte>()
        while (true) {
            val chunk = buf.readContiguous()
            if (chunk.isEmpty()) break
            drained += chunk.toList()
        }
        assertContentEquals(byteArrayOf(0x00, 0x01, 0x02), drained.toByteArray())
        assertTrue(buf.isFullyRead(), "after draining contiguous bytes up to FIN offset, fully read")
    }

    @Test
    fun isFullyRead_is_false_when_no_fin_seen_yet() {
        val buf = ReceiveBuffer()
        buf.insert(0L, byteArrayOf(0x00, 0x01), fin = false)
        buf.readContiguous()
        // Even though everything delivered to date is drained, no FIN has
        // arrived yet — we don't know whether more bytes are coming.
        assertFalse(buf.isFullyRead())
    }

    @Test
    fun fin_only_zero_byte_frame_at_correct_offset_marks_fully_read() {
        val buf = ReceiveBuffer()
        // Real bytes 0..3
        buf.insert(0L, byteArrayOf(0x00, 0x01, 0x02, 0x03), fin = false)
        buf.readContiguous()
        // FIN-only frame at offset 4 with zero data — finOffset == 4 ==
        // current readOffset.
        buf.insert(4L, ByteArray(0), fin = true)
        assertTrue(buf.isFullyRead(), "zero-length FIN frame at exact end marks the stream complete")
    }

    @Test
    fun finOffset_is_pinned_at_first_observation_and_does_not_change() {
        // RFC 9000 §4.5: once set, the final size MUST NOT change. We don't
        // currently surface a violation as a connection error (that's a
        // future hardening item), but the buffer's own bookkeeping must be
        // immune to later FIN frames carrying a different offset.
        val buf = ReceiveBuffer()
        buf.insert(0L, byteArrayOf(0x00, 0x01, 0x02), fin = true)
        assertEquals(3L, buf.finOffset)
        // Second FIN-bearing frame with a different (and impossible) final
        // size — the buffer keeps the first observation.
        buf.insert(0L, byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04), fin = true)
        assertEquals(3L, buf.finOffset, "first finOffset must be authoritative")
    }
}
