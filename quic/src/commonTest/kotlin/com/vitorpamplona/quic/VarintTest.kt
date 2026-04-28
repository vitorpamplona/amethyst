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
package com.vitorpamplona.quic

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class VarintTest {
    @Test
    fun rfc9000_sample_151288809941952652() {
        val value = 151288809941952652L
        val encoded = Varint.encode(value)
        assertContentEquals(
            byteArrayOf(0xC2.toByte(), 0x19, 0x7C, 0x5E, 0xFF.toByte(), 0x14, 0xE8.toByte(), 0x8C.toByte()),
            encoded,
        )
        assertEquals(value, Varint.decode(encoded)!!.value)
    }

    @Test
    fun rfc9000_sample_494878333() {
        val value = 494878333L
        val encoded = Varint.encode(value)
        assertContentEquals(
            byteArrayOf(0x9D.toByte(), 0x7F.toByte(), 0x3E, 0x7D.toByte()),
            encoded,
        )
        assertEquals(value, Varint.decode(encoded)!!.value)
    }

    @Test
    fun rfc9000_sample_15293() {
        val value = 15293L
        val encoded = Varint.encode(value)
        assertContentEquals(byteArrayOf(0x7B.toByte(), 0xBD.toByte()), encoded)
        assertEquals(value, Varint.decode(encoded)!!.value)
    }

    @Test
    fun rfc9000_sample_37() {
        val encoded = Varint.encode(37L)
        assertContentEquals(byteArrayOf(0x25), encoded)
        assertEquals(37L, Varint.decode(encoded)!!.value)
    }

    @Test
    fun boundary_values_round_trip() {
        for (v in listOf(0L, 63L, 64L, 16_383L, 16_384L, 1_073_741_823L, 1_073_741_824L, Varint.MAX_VALUE)) {
            val encoded = Varint.encode(v)
            assertEquals(v, Varint.decode(encoded)!!.value, "round-trip for $v")
        }
    }

    @Test
    fun size_matches_encoding_length() {
        for (v in listOf(0L, 63L, 64L, 16_383L, 16_384L, 1_073_741_823L, 1_073_741_824L, Varint.MAX_VALUE)) {
            assertEquals(Varint.size(v), Varint.encode(v).size)
        }
    }

    @Test
    fun negative_value_is_rejected() {
        assertFailsWith<IllegalArgumentException> { Varint.encode(-1L) }
    }

    @Test
    fun overflow_value_is_rejected() {
        assertFailsWith<IllegalArgumentException> { Varint.encode(Varint.MAX_VALUE + 1) }
    }

    @Test
    fun short_buffer_returns_null_so_caller_can_buffer_more() {
        assertNull(Varint.decode(ByteArray(0)))
        assertNull(Varint.decode(byteArrayOf(0x9D.toByte(), 0x7F.toByte(), 0x3E), 0))
    }

    @Test
    fun bytesConsumed_reflects_actual_length() {
        assertEquals(1, Varint.decode(byteArrayOf(0x25))!!.bytesConsumed)
        assertEquals(2, Varint.decode(byteArrayOf(0x7B.toByte(), 0xBD.toByte()))!!.bytesConsumed)
        assertEquals(4, Varint.decode(byteArrayOf(0x9D.toByte(), 0x7F.toByte(), 0x3E, 0x7D.toByte()))!!.bytesConsumed)
    }
}
