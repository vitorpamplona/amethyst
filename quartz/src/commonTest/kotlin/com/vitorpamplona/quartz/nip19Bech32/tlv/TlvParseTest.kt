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
package com.vitorpamplona.quartz.nip19Bech32.tlv

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

// Regression for security review 2026-04-24 §2.5 / Finding #10.
// Pre-fix `Tlv.parse` threw `IndexOutOfBoundsException` whenever:
//   - input had a trailing single byte (read `rest[1]` with size==1)
//   - a TLV entry declared length > remaining bytes (slice walked off the end)
// Both are reachable from a hostile naddr/nevent/nprofile/nrelay relay hint.
class TlvParseTest {
    private fun bytes(vararg ints: Int) = ByteArray(ints.size) { ints[it].toByte() }

    @Test
    fun parsesEmptyInput() {
        val tlv = Tlv.parse(bytes())
        assertEquals(null, tlv.firstAsString(0))
    }

    @Test
    fun singleTrailingByteDoesNotThrow() {
        // Pre-fix: rest[1] threw IndexOutOfBoundsException.
        val tlv = Tlv.parse(bytes(0x05))
        assertEquals(null, tlv.firstAsString(5))
    }

    @Test
    fun parsesSingleEntry() {
        // type=1, length=3, value=[0xa, 0xb, 0xc]
        val tlv = Tlv.parse(bytes(1, 3, 0xa, 0xb, 0xc))
        assertContentEquals(byteArrayOf(0xa, 0xb, 0xc), tlv.data[1.toByte()]?.first())
    }

    @Test
    fun parsesEntryWithEmptyValue() {
        // type=2, length=0
        val tlv = Tlv.parse(bytes(2, 0))
        assertContentEquals(byteArrayOf(), tlv.data[2.toByte()]?.first())
    }

    @Test
    fun parsesMultipleEntries() {
        // (1,2,[a,b]) (3,1,[c])
        val tlv = Tlv.parse(bytes(1, 2, 0xa, 0xb, 3, 1, 0xc))
        assertContentEquals(byteArrayOf(0xa, 0xb), tlv.data[1.toByte()]?.first())
        assertContentEquals(byteArrayOf(0xc), tlv.data[3.toByte()]?.first())
    }

    @Test
    fun groupsRepeatedTypes() {
        // (5,1,[a]) (5,1,[b]) — two entries under the same type get appended
        val tlv = Tlv.parse(bytes(5, 1, 0xa, 5, 1, 0xb))
        val entries = tlv.data[5.toByte()]
        assertEquals(2, entries?.size)
        assertContentEquals(byteArrayOf(0xa), entries?.get(0))
        assertContentEquals(byteArrayOf(0xb), entries?.get(1))
    }

    @Test
    fun truncatedDeclaredLengthIsSkippedNotThrown() {
        // type=1, length=5, but only 2 value bytes follow → truncated, must be skipped.
        // Pre-fix: sliceArray(IntRange(2, 6)) on a 4-byte array threw.
        val tlv = Tlv.parse(bytes(1, 5, 0xa, 0xb))
        assertEquals(null, tlv.data[1.toByte()])
    }

    @Test
    fun keepsValidPrefixWhenLaterTupleIsTruncated() {
        // First (1,2,[a,b]) is well-formed, then (2,5,[c,d]) is truncated.
        val tlv = Tlv.parse(bytes(1, 2, 0xa, 0xb, 2, 5, 0xc, 0xd))
        assertContentEquals(byteArrayOf(0xa, 0xb), tlv.data[1.toByte()]?.first())
        assertEquals(null, tlv.data[2.toByte()])
    }

    @Test
    fun handlesUnsignedLengthByte() {
        // length byte 0xFF must be treated as 255 (unsigned), not -1.
        val payload = ByteArray(255) { 0x42 }
        val tlv = Tlv.parse(byteArrayOf(7, 0xFF.toByte()) + payload)
        assertContentEquals(payload, tlv.data[7.toByte()]?.first())
    }

    @Test
    fun arbitraryFuzzInputDoesNotThrow() {
        // 200 random byte sequences of varying lengths up to 64 bytes. Pre-fix this
        // would have hit IndexOutOfBoundsException on a meaningful fraction of inputs.
        // Implicit assertion: parse must not throw on any input.
        val rng = Random(0xC0FFEE)
        repeat(200) {
            val len = rng.nextInt(0, 65)
            val data = ByteArray(len) { rng.nextInt().toByte() }
            Tlv.parse(data)
        }
    }
}
