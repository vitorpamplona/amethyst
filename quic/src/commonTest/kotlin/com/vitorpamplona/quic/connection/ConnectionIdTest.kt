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
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class ConnectionIdTest {
    @Test
    fun random_default_length_is_8() {
        assertEquals(8, ConnectionId.random().length)
    }

    @Test
    fun random_returns_distinct_ids() {
        val a = ConnectionId.random()
        val b = ConnectionId.random()
        assertNotEquals(a, b)
    }

    @Test
    fun length_zero_is_legal() {
        val id = ConnectionId(ByteArray(0))
        assertEquals(0, id.length)
        assertEquals("", id.toHex())
    }

    @Test
    fun length_over_20_is_rejected() {
        assertFailsWith<IllegalArgumentException> { ConnectionId(ByteArray(21)) }
    }

    @Test
    fun equals_compares_bytes() {
        val a = ConnectionId(byteArrayOf(0x01, 0x02, 0x03))
        val b = ConnectionId(byteArrayOf(0x01, 0x02, 0x03))
        val c = ConnectionId(byteArrayOf(0x01, 0x02, 0x04))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
    }

    @Test
    fun toHex_lowercase_padded() {
        val id = ConnectionId(byteArrayOf(0x00, 0x0A, 0xFF.toByte()))
        assertEquals("000aff", id.toHex())
    }
}
