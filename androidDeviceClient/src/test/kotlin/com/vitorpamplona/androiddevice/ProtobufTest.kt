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
package com.vitorpamplona.androiddevice

import com.vitorpamplona.androiddevice.grpc.ProtoField
import com.vitorpamplona.androiddevice.grpc.ProtoReader
import com.vitorpamplona.androiddevice.grpc.ProtoWriter
import com.vitorpamplona.androiddevice.grpc.message
import com.vitorpamplona.androiddevice.grpc.string
import com.vitorpamplona.androiddevice.grpc.varint
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProtobufTest {
    @Test
    fun writesTheWireFormatProtobufDoes() {
        // Field 1 = 150 is the protobuf docs' own example: 08 96 01.
        assertContentEquals(byteArrayOf(0x08, 0x96.toByte(), 0x01), ProtoWriter().int(1, 150).bytes())
        // Field 2 = "testing": 12 07 74 65 73 74 69 6e 67.
        assertContentEquals(byteArrayOf(0x12, 0x07) + "testing".toByteArray(), ProtoWriter().string(2, "testing").bytes())
    }

    @Test
    fun skipsDefaultsAndReadsBackNestedMessages() {
        val bytes =
            ProtoWriter()
                .int(1, 0)
                .string(2, "")
                .message(3) {
                    int(1, -1)
                    string(2, "nested")
                }.packedFloats(4, floatArrayOf(1f, 2.5f))
                .bytes()
        val fields = ProtoReader.read(bytes)
        assertEquals(listOf(3, 4), fields.map { it.number })
        val nested = fields.message(3)
        // A negative int32 is written as ten bytes and reads back as the same 64-bit value.
        assertEquals(-1L, nested.varint(1))
        assertEquals("nested", nested.string(2))
        assertTrue(fields[1] is ProtoField.Length)
        assertEquals(8, (fields[1] as ProtoField.Length).value.size)
    }
}
