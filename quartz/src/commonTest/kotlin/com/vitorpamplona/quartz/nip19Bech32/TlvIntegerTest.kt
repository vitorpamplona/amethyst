/**
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
package com.vitorpamplona.quartz.nip19Bech32

import com.vitorpamplona.quartz.nip19Bech32.tlv.to32BitByteArray
import com.vitorpamplona.quartz.nip19Bech32.tlv.toInt32
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TlvIntegerTest {
    fun to_int_32_length_smaller_than_4() {
        assertNull(byteArrayOfInts(1, 2, 3).toInt32())
    }

    fun to_int_32_length_bigger_than_4() {
        assertNull(byteArrayOfInts(1, 2, 3, 4, 5).toInt32())
    }

    @Test()
    fun to_int_32_length_4() {
        val actual = byteArrayOfInts(1, 2, 3, 4).toInt32()

        assertEquals(16909060, actual)
    }

    @Test()
    fun backAndForth() {
        assertEquals(234, 234.to32BitByteArray().toInt32())
        assertEquals(1, 1.to32BitByteArray().toInt32())
        assertEquals(0, 0.to32BitByteArray().toInt32())
        assertEquals(1000, 1000.to32BitByteArray().toInt32())

        assertEquals(-234, (-234).to32BitByteArray().toInt32())
        assertEquals(-1, (-1).to32BitByteArray().toInt32())
        assertEquals(-0, (-0).to32BitByteArray().toInt32())
        assertEquals(-1000, (-1000).to32BitByteArray().toInt32())
    }

    private fun byteArrayOfInts(vararg ints: Int) = ByteArray(ints.size) { pos -> ints[pos].toByte() }
}
