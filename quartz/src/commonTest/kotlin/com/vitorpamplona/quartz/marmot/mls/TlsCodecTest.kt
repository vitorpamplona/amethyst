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
package com.vitorpamplona.quartz.marmot.mls

import com.vitorpamplona.quartz.marmot.mls.codec.TlsReader
import com.vitorpamplona.quartz.marmot.mls.codec.TlsSerializable
import com.vitorpamplona.quartz.marmot.mls.codec.TlsWriter
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * Tests for TLS presentation language codec (RFC 8446 Section 3).
 *
 * These tests verify wire format compatibility — the encoding must exactly
 * match what OpenMLS and mls-rs produce for interoperability.
 */
class TlsCodecTest {
    @Test
    fun testUint8RoundTrip() {
        val writer = TlsWriter()
        writer.putUint8(0)
        writer.putUint8(127)
        writer.putUint8(255)

        val bytes = writer.toByteArray()
        assertEquals(3, bytes.size)

        val reader = TlsReader(bytes)
        assertEquals(0, reader.readUint8())
        assertEquals(127, reader.readUint8())
        assertEquals(255, reader.readUint8())
        assertFalse(reader.hasRemaining)
    }

    @Test
    fun testUint16RoundTrip() {
        val writer = TlsWriter()
        writer.putUint16(0)
        writer.putUint16(256)
        writer.putUint16(65535)

        val bytes = writer.toByteArray()
        assertEquals(6, bytes.size)

        val reader = TlsReader(bytes)
        assertEquals(0, reader.readUint16())
        assertEquals(256, reader.readUint16())
        assertEquals(65535, reader.readUint16())
    }

    @Test
    fun testUint16BigEndian() {
        val writer = TlsWriter()
        writer.putUint16(0x0102)
        val bytes = writer.toByteArray()
        assertEquals(0x01.toByte(), bytes[0])
        assertEquals(0x02.toByte(), bytes[1])
    }

    @Test
    fun testUint32RoundTrip() {
        val writer = TlsWriter()
        writer.putUint32(0)
        writer.putUint32(0xFFFFFFFFL)

        val bytes = writer.toByteArray()
        assertEquals(8, bytes.size)

        val reader = TlsReader(bytes)
        assertEquals(0L, reader.readUint32())
        assertEquals(0xFFFFFFFFL, reader.readUint32())
    }

    @Test
    fun testUint64RoundTrip() {
        val writer = TlsWriter()
        writer.putUint64(0L)
        writer.putUint64(Long.MAX_VALUE)
        writer.putUint64(-1L) // 0xFFFFFFFFFFFFFFFF as unsigned

        val bytes = writer.toByteArray()
        assertEquals(24, bytes.size)

        val reader = TlsReader(bytes)
        assertEquals(0L, reader.readUint64())
        assertEquals(Long.MAX_VALUE, reader.readUint64())
        assertEquals(-1L, reader.readUint64())
    }

    @Test
    fun testOpaque1RoundTrip() {
        val data = byteArrayOf(0x01, 0x02, 0x03)
        val writer = TlsWriter()
        writer.putOpaque1(data)

        val bytes = writer.toByteArray()
        assertEquals(4, bytes.size) // 1 byte length + 3 bytes data
        assertEquals(3, bytes[0].toInt()) // length prefix

        val reader = TlsReader(bytes)
        assertContentEquals(data, reader.readOpaque1())
    }

    @Test
    fun testOpaque2RoundTrip() {
        val data = ByteArray(300) { it.toByte() }
        val writer = TlsWriter()
        writer.putOpaque2(data)

        val bytes = writer.toByteArray()
        assertEquals(302, bytes.size)

        val reader = TlsReader(bytes)
        assertContentEquals(data, reader.readOpaque2())
    }

    @Test
    fun testOpaque4RoundTrip() {
        val data = byteArrayOf(0xAA.toByte(), 0xBB.toByte())
        val writer = TlsWriter()
        writer.putOpaque4(data)

        val bytes = writer.toByteArray()
        assertEquals(6, bytes.size) // 4 byte length + 2 bytes data

        val reader = TlsReader(bytes)
        assertContentEquals(data, reader.readOpaque4())
    }

    @Test
    fun testEmptyOpaque() {
        val writer = TlsWriter()
        writer.putOpaque1(ByteArray(0))
        writer.putOpaque2(ByteArray(0))
        writer.putOpaque4(ByteArray(0))

        val reader = TlsReader(writer.toByteArray())
        assertContentEquals(ByteArray(0), reader.readOpaque1())
        assertContentEquals(ByteArray(0), reader.readOpaque2())
        assertContentEquals(ByteArray(0), reader.readOpaque4())
    }

    @Test
    fun testVectorOfStructs() {
        data class TestStruct(
            val a: Int,
            val b: Int,
        ) : TlsSerializable {
            override fun encodeTls(writer: TlsWriter) {
                writer.putUint8(a)
                writer.putUint16(b)
            }
        }

        val items = listOf(TestStruct(1, 100), TestStruct(2, 200))
        val writer = TlsWriter()
        writer.putVector2(items)

        val reader = TlsReader(writer.toByteArray())
        val decoded =
            reader.readVector2 { r ->
                val a = r.readUint8()
                val b = r.readUint16()
                TestStruct(a, b)
            }

        assertEquals(2, decoded.size)
        assertEquals(1, decoded[0].a)
        assertEquals(100, decoded[0].b)
        assertEquals(2, decoded[1].a)
        assertEquals(200, decoded[1].b)
    }

    @Test
    fun testOptionalPresent() {
        data class Inner(
            val x: Int,
        ) : TlsSerializable {
            override fun encodeTls(writer: TlsWriter) {
                writer.putUint16(x)
            }
        }

        val writer = TlsWriter()
        writer.putOptional(Inner(42))

        val reader = TlsReader(writer.toByteArray())
        val result = reader.readOptional { Inner(it.readUint16()) }

        assertEquals(42, result?.x)
    }

    @Test
    fun testOptionalAbsent() {
        val writer = TlsWriter()
        writer.putOptional(null)

        val reader = TlsReader(writer.toByteArray())
        val result = reader.readOptional { it.readUint16() }

        assertNull(result)
    }

    @Test
    fun testSubReader() {
        val writer = TlsWriter()
        writer.putUint8(1)
        writer.putUint8(2)
        writer.putUint8(3)
        writer.putUint8(4)

        val reader = TlsReader(writer.toByteArray())
        assertEquals(1, reader.readUint8())
        val sub = reader.subReader(2)
        assertEquals(2, sub.readUint8())
        assertEquals(3, sub.readUint8())
        assertFalse(sub.hasRemaining)
        assertEquals(4, reader.readUint8())
    }

    @Test
    fun testWriterGrowsAutomatically() {
        val writer = TlsWriter(initialCapacity = 4)
        // Write more than initial capacity
        for (i in 0 until 100) {
            writer.putUint8(i)
        }
        assertEquals(100, writer.size)
        val reader = TlsReader(writer.toByteArray())
        for (i in 0 until 100) {
            assertEquals(i, reader.readUint8())
        }
    }
}
