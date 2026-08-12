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
package com.vitorpamplona.quartz.utils

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class HexExactSizeTest {
    val id64 = "48a72b485d38338627ec9d427583551f9af4f016c739b8ec0d6313540a8b12cf"
    val sig128 = id64 + "b0635d6a9851d3aed0cd6c495b282167acf761729078d975fc341b22650b07b9"

    @Test
    fun decode64RoundTrip() {
        assertEquals(id64, Hex.encode64(Hex.decode64(id64)))
        assertContentEquals(Hex.decode(id64), Hex.decode64(id64))
        assertContentEquals(Hex.decode(id64), Hex.decode64OrNull(id64))
    }

    @Test
    fun decode64AcceptsUpperCase() {
        assertContentEquals(Hex.decode(id64), Hex.decode64(id64.uppercase()))
    }

    @Test
    fun decode64RejectsWrongLengths() {
        assertFailsWith<IllegalArgumentException> { Hex.decode64("") }
        assertFailsWith<IllegalArgumentException> { Hex.decode64(id64.drop(1)) }
        assertFailsWith<IllegalArgumentException> { Hex.decode64(id64.drop(2)) }
        assertFailsWith<IllegalArgumentException> { Hex.decode64(id64 + "ab") }
        assertFailsWith<IllegalArgumentException> { Hex.decode64(sig128) }

        assertNull(Hex.decode64OrNull(""))
        assertNull(Hex.decode64OrNull(id64.drop(2)))
        assertNull(Hex.decode64OrNull(id64 + "ab"))
        assertNull(Hex.decode64OrNull(sig128))
    }

    @Test
    fun decode64RejectsInvalidChars() {
        // every position, both a plain non-hex char and an emoji (code > 0xFF)
        for (i in 0 until 64) {
            val withG = id64.substring(0, i) + "g" + id64.substring(i + 1)
            assertNull(Hex.decode64OrNull(withG), withG)
            assertFailsWith<IllegalArgumentException> { Hex.decode64(withG) }
        }
        val withEmoji = "🥰" + id64.drop(2)
        assertNull(Hex.decode64OrNull(withEmoji))
        assertFailsWith<IllegalArgumentException> { Hex.decode64(withEmoji) }
    }

    @Test
    fun decode128RoundTrip() {
        assertEquals(sig128, Hex.encode128(Hex.decode128(sig128)))
        assertContentEquals(Hex.decode(sig128), Hex.decode128(sig128))
        assertContentEquals(Hex.decode(sig128), Hex.decode128OrNull(sig128.uppercase()))
    }

    @Test
    fun decode128RejectsWrongLengthsAndInvalidChars() {
        assertFailsWith<IllegalArgumentException> { Hex.decode128("") }
        assertFailsWith<IllegalArgumentException> { Hex.decode128(id64) }
        assertFailsWith<IllegalArgumentException> { Hex.decode128(sig128.drop(2)) }
        assertFailsWith<IllegalArgumentException> { Hex.decode128(sig128 + "ab") }

        assertNull(Hex.decode128OrNull(id64))
        assertNull(Hex.decode128OrNull(sig128.dropLast(1) + "x"))
        assertNull(Hex.decode128OrNull("🥰" + sig128.drop(2)))
    }

    @Test
    fun encodeRejectsWrongSizes() {
        assertFailsWith<IllegalArgumentException> { Hex.encode64(ByteArray(31)) }
        assertFailsWith<IllegalArgumentException> { Hex.encode64(ByteArray(33)) }
        assertFailsWith<IllegalArgumentException> { Hex.encode64(ByteArray(64)) }
        assertFailsWith<IllegalArgumentException> { Hex.encode128(ByteArray(32)) }
        assertFailsWith<IllegalArgumentException> { Hex.encode128(ByteArray(63)) }
        assertFailsWith<IllegalArgumentException> { Hex.encode128(ByteArray(65)) }
    }

    @Test
    fun randomsMatchGenericDecode() {
        for (i in 0..1000) {
            val id = RandomInstance.bytes(32)
            assertEquals(Hex.encode(id), Hex.encode64(id))
            assertContentEquals(id, Hex.decode64(Hex.encode64(id)))

            val sig = RandomInstance.bytes(64)
            assertEquals(Hex.encode(sig), Hex.encode128(sig))
            assertContentEquals(sig, Hex.decode128(Hex.encode128(sig)))
        }
    }
}
