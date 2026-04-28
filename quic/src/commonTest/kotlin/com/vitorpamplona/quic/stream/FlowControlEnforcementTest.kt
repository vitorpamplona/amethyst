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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Asserts the SendBuffer correctly bounds [takeChunk] by `maxBytes` and that
 * unsent bytes remain in the buffer for a later call.
 *
 * The connection-level writer enforces flow control by passing
 * `min(packet_budget, sendCredit - sentOffset)` as `maxBytes`; we verify
 * the buffer respects that contract.
 */
class FlowControlEnforcementTest {
    @Test
    fun take_chunk_respects_max_bytes() {
        val buf = SendBuffer()
        buf.enqueue(ByteArray(100) { it.toByte() })
        // sendCredit acts via maxBytes here.
        val first = buf.takeChunk(maxBytes = 30)
        assertNotNull(first)
        assertEquals(30, first.data.size)
        assertEquals(0L, first.offset)
        assertEquals(70, buf.readableBytes, "unsent bytes remain")
    }

    @Test
    fun take_chunk_zero_with_pending_bytes_returns_null() {
        // The writer passes maxBytes=0 when sendCredit is exhausted.
        val buf = SendBuffer()
        buf.enqueue(ByteArray(50))
        assertNull(buf.takeChunk(maxBytes = 0))
        assertEquals(50, buf.readableBytes, "buffer must not be drained when credit is zero")
    }

    @Test
    fun multiple_takes_accumulate_offset() {
        val buf = SendBuffer()
        buf.enqueue(ByteArray(100))
        val a = buf.takeChunk(maxBytes = 30)!!
        val b = buf.takeChunk(maxBytes = 40)!!
        val c = buf.takeChunk(maxBytes = 100)!!
        assertEquals(0L, a.offset)
        assertEquals(30L, b.offset)
        assertEquals(70L, c.offset)
        assertEquals(30, a.data.size)
        assertEquals(40, b.data.size)
        assertEquals(30, c.data.size, "third take exhausts the buffer")
        assertEquals(100L, buf.sentOffset)
    }

    @Test
    fun chunked_enqueue_preserves_order_across_takes() {
        // Multi-enqueue, multi-take exercises the chunked-queue path that
        // replaced the O(N²) copyOf-on-every-enqueue.
        val buf = SendBuffer()
        buf.enqueue(byteArrayOf(0x01, 0x02, 0x03))
        buf.enqueue(byteArrayOf(0x04, 0x05))
        buf.enqueue(byteArrayOf(0x06, 0x07, 0x08, 0x09))
        // Take in odd sizes to cross chunk boundaries.
        val a = buf.takeChunk(maxBytes = 4)!!
        val b = buf.takeChunk(maxBytes = 4)!!
        val c = buf.takeChunk(maxBytes = 100)!!
        // Concatenate and check the original byte sequence is preserved.
        val all = a.data + b.data + c.data
        assertEquals(9, all.size)
        for (i in 0..8) assertEquals((i + 1).toByte(), all[i], "byte $i mismatch")
    }

    @Test
    fun fin_only_chunk_is_emitted_after_buffer_drained() {
        val buf = SendBuffer()
        buf.enqueue(byteArrayOf(0x01, 0x02))
        buf.finish()
        val a = buf.takeChunk(maxBytes = 10)!!
        assertEquals(true, a.fin, "FIN piggybacks on the final data chunk")
        assertEquals(2, a.data.size)
        assertNull(buf.takeChunk(maxBytes = 10), "no more chunks after FIN+empty")
    }

    @Test
    fun fin_only_chunk_with_separate_takes() {
        val buf = SendBuffer()
        buf.enqueue(byteArrayOf(0x01, 0x02))
        // Take everything, no FIN yet.
        val a = buf.takeChunk(maxBytes = 100)!!
        assertEquals(false, a.fin)
        // Then mark FIN and take.
        buf.finish()
        val b = buf.takeChunk(maxBytes = 100)!!
        assertEquals(true, b.fin)
        assertEquals(0, b.data.size, "final chunk is zero-length when FIN comes after the last data")
    }
}
