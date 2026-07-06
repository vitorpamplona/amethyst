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

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HexEncodingTest {
    val testHex = "48a72b485d38338627ec9d427583551f9af4f016c739b8ec0d6313540a8b12cf"

    @Test
    fun testHexEncodeDecodeOurs() {
        assertEquals(
            testHex,
            Hex.encode(
                Hex.decode(testHex),
            ),
        )
    }

    @Test
    fun testIsHex() {
        assertFalse(Hex.isHex("/0"), "/0")
        assertFalse(Hex.isHex("/."), "/.")
        assertFalse(Hex.isHex("::"), "::")
        assertFalse(Hex.isHex("!!"), "!!")
        assertFalse(Hex.isHex("@@"), "@@")
        assertFalse(Hex.isHex("GG"), "GG")
        assertFalse(Hex.isHex("FG"), "FG")
        assertFalse(Hex.isHex("`a"), "`a")
        assertFalse(Hex.isHex("gg"), "gg")
        assertFalse(Hex.isHex("fg"), "fg")
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun testRandomsIsHex() {
        for (i in 0..10000) {
            val bytes = Random.nextBytes(32)
            val hex = bytes.toHexString(HexFormat.Default)
            assertTrue(Hex.isHex(hex), hex)
            val hexUpper = bytes.toHexString(HexFormat.UpperCase)
            assertTrue(Hex.isHex(hexUpper), hexUpper)
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun testRandomsUppercase() {
        for (i in 0..1000) {
            val bytes = Random.nextBytes(32)
            val hex = bytes.toHexString(HexFormat.UpperCase)
            assertEquals(
                bytes.toList(),
                Hex.decode(hex).toList(),
            )
        }
    }

    @Test
    fun testToLong64() {
        assertEquals(0x48a72b485d383386uL.toLong(), Hex.toLong64(testHex))
        assertEquals(0L, Hex.toLong64("0000000000000000"))
        assertEquals(-1L, Hex.toLong64("ffffffffffffffff"))
        // uppercase reads the same
        assertEquals(0x48a72b485d383386uL.toLong(), Hex.toLong64(testHex.uppercase()))
    }

    @Test
    fun testToLong128() {
        val longs = Hex.toLong128(testHex)
        assertEquals(2, longs.size)
        assertEquals(0x48a72b485d383386uL.toLong(), longs[0])
        assertEquals(0x27ec9d427583551fuL.toLong(), longs[1])
    }

    @Test
    fun testToLong256() {
        val longs = Hex.toLong256(testHex)
        assertEquals(4, longs.size)
        assertEquals(0x48a72b485d383386uL.toLong(), longs[0])
        assertEquals(0x27ec9d427583551fuL.toLong(), longs[1])
        assertEquals(0x9af4f016c739b8ecuL.toLong(), longs[2])
        assertEquals(0x0d6313540a8b12cfuL.toLong(), longs[3])
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun testToLongMatchesDecodedBytes() {
        for (i in 0..1000) {
            val bytes = Random.nextBytes(32)
            val hex = bytes.toHexString(HexFormat.Default)

            // the four longs must reconstruct exactly the 32 decoded bytes, big-endian
            val longs = Hex.toLong256(hex)
            for (word in 0 until 4) {
                for (b in 0 until 8) {
                    val expected = bytes[word * 8 + b].toLong() and 0xFF
                    val actual = (longs[word] ushr ((7 - b) * 8)) and 0xFF
                    assertEquals(expected, actual, hex)
                }
            }

            assertEquals(longs[0], Hex.toLong64(hex))
            assertEquals(longs.take(2), Hex.toLong128(hex).toList())
        }
    }
}
