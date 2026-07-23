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
package com.vitorpamplona.quartz.nipXXBolt12Zaps.bolt12

import com.vitorpamplona.quartz.utils.Hex
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TlvTest {
    private fun hex(bytes: ByteArray) = Hex.encode(bytes)

    @Test
    fun bigSizeEncodesTheFourFormsAtTheirBoundaries() {
        assertEquals("00", hex(BigSize.encode(0)))
        assertEquals("fc", hex(BigSize.encode(0xfc)))
        assertEquals("fd00fd", hex(BigSize.encode(0xfd)))
        assertEquals("fdffff", hex(BigSize.encode(0xffff)))
        assertEquals("fe00010000", hex(BigSize.encode(0x10000)))
        assertEquals("feffffffff", hex(BigSize.encode(0xffffffffL)))
        assertEquals("ff0000000100000000", hex(BigSize.encode(0x100000000L)))

        assertEquals(1, BigSize.encodedSize(0xfc))
        assertEquals(3, BigSize.encodedSize(0xfd))
        assertEquals(5, BigSize.encodedSize(0x10000))
        assertEquals(9, BigSize.encodedSize(0x100000000L))
    }

    @Test
    fun bigSizeRoundTripsThroughTheReader() {
        for (v in listOf(0L, 1L, 0xfcL, 0xfdL, 0x1234L, 0xffffL, 0x10000L, 0xdeadbeefL, 9736L, 1001L)) {
            val reader = TlvReader(BigSize.encode(v))
            assertEquals(v, reader.readBigSize())
            assertEquals(0, reader.remaining())
        }
    }

    @Test
    fun tu64StripsAndRestoresLeadingZeroes() {
        assertEquals(0, Bolt12Values.tu64ToBytes(0).size)
        assertContentEquals(byteArrayOf(0x03, 0xe8.toByte()), Bolt12Values.tu64ToBytes(1000))
        for (v in listOf(0L, 1L, 21_000L, 0xffffffL, Long.MAX_VALUE)) {
            assertEquals(v, Bolt12Values.tu64(Bolt12Values.tu64ToBytes(v)))
        }
    }

    @Test
    fun tlvStreamRoundTrips() {
        val records =
            listOf(
                TlvRecord(8, Bolt12Values.tu64ToBytes(21_000)),
                TlvRecord(22, ByteArray(33) { it.toByte() }),
                TlvRecord(1001, ByteArray(32) { (it + 1).toByte() }),
            )
        val stream = TlvStream(records)
        val decoded = TlvStream.read(stream.encode())

        assertEquals(records.map { it.type }, decoded.records.map { it.type })
        assertEquals(21_000L, decoded.tu64(8))
        assertContentEquals(records[1].value, decoded.value(22))
        assertNull(decoded.get(99))
    }

    @Test
    fun tlvStreamRejectsNonAscendingTypes() {
        val outOfOrder = TlvRecord(22, byteArrayOf(1)).encoded + TlvRecord(8, byteArrayOf(2)).encoded
        assertFailsWith<IllegalArgumentException> { TlvStream.read(outOfOrder) }
    }

    @Test
    fun signatureElementRangeIsRecognized() {
        assertEquals(false, TlvRecord(176, byteArrayOf()).isSignatureElement())
        assertEquals(true, TlvRecord(240, byteArrayOf()).isSignatureElement())
        assertEquals(true, TlvRecord(241, byteArrayOf()).isSignatureElement())
        assertEquals(true, TlvRecord(1000, byteArrayOf()).isSignatureElement())
        assertEquals(false, TlvRecord(1001, byteArrayOf()).isSignatureElement())
    }
}
