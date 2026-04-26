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
import kotlin.test.assertTrue

class ReceiveBufferTest {
    @Test
    fun in_order_chunks_pass_through() {
        val buf = ReceiveBuffer()
        buf.insert(0, byteArrayOf(1, 2, 3))
        assertContentEquals(byteArrayOf(1, 2, 3), buf.readContiguous())
        buf.insert(3, byteArrayOf(4, 5))
        assertContentEquals(byteArrayOf(4, 5), buf.readContiguous())
        assertEquals(5, buf.contiguousEnd())
    }

    @Test
    fun reordered_chunks_are_buffered_until_filled() {
        val buf = ReceiveBuffer()
        buf.insert(2, byteArrayOf(3, 4, 5))
        // Gap at 0..1; nothing yet.
        assertEquals(0, buf.readContiguous().size)
        buf.insert(0, byteArrayOf(1, 2))
        // Now fully contiguous up to 5.
        assertContentEquals(byteArrayOf(1, 2, 3, 4, 5), buf.readContiguous())
    }

    @Test
    fun overlapping_chunks_are_deduplicated() {
        val buf = ReceiveBuffer()
        buf.insert(0, byteArrayOf(1, 2, 3, 4))
        buf.insert(2, byteArrayOf(3, 4, 5, 6))
        assertContentEquals(byteArrayOf(1, 2, 3, 4, 5, 6), buf.readContiguous())
    }

    @Test
    fun fin_propagates_through_buffer() {
        val buf = ReceiveBuffer()
        buf.insert(0, byteArrayOf(1, 2, 3), fin = true)
        assertTrue(buf.finReceived)
    }

    @Test
    fun later_chunk_preceding_already_consumed_data_is_dropped() {
        val buf = ReceiveBuffer()
        buf.insert(0, byteArrayOf(1, 2, 3))
        buf.readContiguous()
        // Now readOffset = 3; this chunk overlaps with already-consumed 0..2.
        buf.insert(0, byteArrayOf(1, 2, 3, 4, 5))
        // The remaining 4..5 should still come through.
        assertContentEquals(byteArrayOf(4, 5), buf.readContiguous())
    }
}
