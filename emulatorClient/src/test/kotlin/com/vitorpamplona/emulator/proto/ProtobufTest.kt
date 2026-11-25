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
package com.vitorpamplona.emulator.proto

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProtobufTest {
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    @Test
    fun varintsMatchTheSpecExamples() {
        // https://protobuf.dev/programming-guides/encoding/: field 1 = 150 is 08 96 01.
        assertEquals("089601", hex(protoMessage { int32(1, 150) }))
        // Negative int32 is sign-extended to ten bytes.
        assertEquals("08ffffffffffffffffff01", hex(protoMessage { int32(1, -1) }))
        // uint32 max stays five bytes.
        assertEquals("08ffffffff0f", hex(protoMessage { uint32(1, -1) }))
        // Defaults are omitted.
        assertEquals(
            "",
            hex(
                protoMessage {
                    int32(1, 0)
                    bool(2, false)
                    string(3, "")
                    uint64(4, 0)
                },
            ),
        )
    }

    @Test
    fun scalarsRoundTrip() {
        val bytes =
            protoMessage {
                int32(1, -42)
                uint32(2, Int.MAX_VALUE)
                uint64(3, Long.MAX_VALUE)
                bool(4, true)
                double(5, -12.5)
                string(6, "héllo")
                bytes(7, byteArrayOf(1, 2, 3))
                packedFloats(8, listOf(1.5f, -90f, 0f))
                message(9) { int32(1, 7) }
            }
        val seen = mutableSetOf<Int>()
        bytes.forEachProtoField { field ->
            seen += field
            when (field) {
                1 -> assertEquals(-42, readInt32())
                2 -> assertEquals(Int.MAX_VALUE, readUInt32())
                3 -> assertEquals(Long.MAX_VALUE, readUInt64())
                4 -> assertTrue(readBool())
                5 -> assertEquals(-12.5, readDouble())
                6 -> assertEquals("héllo", readString())
                7 -> assertContentEquals(byteArrayOf(1, 2, 3), readBytes())
                8 -> {
                    val floats = ArrayList<Float>()
                    readFloats(floats)
                    assertEquals(listOf(1.5f, -90f, 0f), floats)
                }
                9 -> {
                    var inner = 0
                    readMessage().forEachField { f -> if (f == 1) inner = readInt32() else skip() }
                    assertEquals(7, inner)
                }
                else -> skip()
            }
        }
        assertEquals((1..9).toSet(), seen)
    }

    @Test
    fun unpackedFloatsAreAcceptedToo() {
        // field 1, wire type 5 (fixed32), 1.0f = 0x3f800000 little-endian.
        val bytes = byteArrayOf(0x0d, 0x00, 0x00, 0x80.toByte(), 0x3f, 0x0d, 0x00, 0x00, 0x00, 0x40)
        val floats = ArrayList<Float>()
        bytes.forEachProtoField { if (it == 1) readFloats(floats) else skip() }
        assertEquals(listOf(1f, 2f), floats)
    }

    @Test
    fun unknownFieldsOfEveryWireTypeAreSkipped() {
        val bytes =
            byteArrayOf(
                // field 2 varint 300
                0x10,
                0xac.toByte(),
                0x02,
                // field 3 fixed64
                0x19,
                1,
                2,
                3,
                4,
                5,
                6,
                7,
                8,
                // field 4 length-delimited "abc"
                0x22,
                3,
                'a'.code.toByte(),
                'b'.code.toByte(),
                'c'.code.toByte(),
                // field 5 fixed32
                0x2d,
                1,
                2,
                3,
                4,
                // field 6 start group, containing field 1 varint 1, end group 6
                0x33,
                0x08,
                0x01,
                0x34,
                // field 1 varint 99: the one we want
                0x08,
                99,
            )
        var wanted = -1
        bytes.forEachProtoField { field -> if (field == 1) wanted = readInt32() else skip() }
        assertEquals(99, wanted)
    }

    @Test
    fun truncatedInputFails() {
        val full = protoMessage { string(1, "hello") }
        val truncated = full.copyOf(full.size - 2)
        assertFailsWith<ProtobufException> { truncated.forEachProtoField { readString() } }
        assertFailsWith<ProtobufException> { byteArrayOf(0x08, 0x80.toByte()).forEachProtoField { readInt32() } }
    }

    @Test
    fun wrongWireTypeFails() {
        val bytes = protoMessage { string(1, "x") }
        assertFailsWith<ProtobufException> { bytes.forEachProtoField { readInt32() } }
    }

    @Test
    fun emptyMessageHasNoFields() {
        assertFalse(ProtoReader(ByteArray(0)).nextField())
    }
}
