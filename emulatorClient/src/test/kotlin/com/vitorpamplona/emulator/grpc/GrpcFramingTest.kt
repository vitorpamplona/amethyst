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
package com.vitorpamplona.emulator.grpc

import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class GrpcFramingTest {
    /** Hands out at most [chunk] bytes per read, as HTTP/2 DATA frames may split a message. */
    private class Trickle(
        bytes: ByteArray,
        private val chunk: Long,
    ) : Source {
        private val data = Buffer().write(bytes)

        override fun read(
            sink: Buffer,
            byteCount: Long,
        ): Long {
            if (data.size == 0L) return -1
            return data.read(sink, minOf(byteCount, chunk))
        }

        override fun timeout() = Timeout.NONE

        override fun close() {}
    }

    @Test
    fun frameIsFlagThenBigEndianLength() {
        assertContentEquals(byteArrayOf(0, 0, 0, 0, 3, 7, 8, 9), GrpcFraming.frame(byteArrayOf(7, 8, 9)))
        assertContentEquals(byteArrayOf(0, 0, 0, 0, 0), GrpcFraming.frame(ByteArray(0)))
    }

    @Test
    fun messagesSplitAcrossReadsAreReassembled() {
        val big = ByteArray(70_000) { (it % 251).toByte() }
        val messages = listOf(byteArrayOf(1), ByteArray(0), big, byteArrayOf(4, 5))
        for (chunk in listOf(1L, 3L, 5L, 4096L)) {
            val source = Trickle(GrpcFraming.frameAll(messages), chunk).buffer()
            val read = generateSequence { GrpcFraming.readMessage(source) }.toList()
            assertEquals(messages.size, read.size, "chunk $chunk")
            messages.zip(read).forEach { (expected, actual) -> assertContentEquals(expected, actual) }
        }
    }

    @Test
    fun cleanEndBetweenMessagesIsNull() {
        assertNull(GrpcFraming.readMessage(Buffer()))
    }

    @Test
    fun streamEndingInsideAMessageFails() {
        val framed = GrpcFraming.frame(ByteArray(10))
        assertFailsWith<IOException> { GrpcFraming.readMessage(Buffer().write(framed.copyOf(3))) }
        assertFailsWith<IOException> { GrpcFraming.readMessage(Buffer().write(framed.copyOf(8))) }
    }

    @Test
    fun compressedOrHugeMessagesFail() {
        assertFailsWith<IOException> { GrpcFraming.readMessage(Buffer().write(byteArrayOf(1, 0, 0, 0, 1, 9))) }
        assertFailsWith<IOException> { GrpcFraming.readMessage(Buffer().write(byteArrayOf(0, 0x7f, 0, 0, 0))) }
    }

    @Test
    fun grpcMessageIsPercentDecoded() {
        assertEquals("bad token: ü", GrpcFraming.decodeGrpcMessage("bad%20token%3A%20%C3%BC"))
        assertEquals("a+b", GrpcFraming.decodeGrpcMessage("a+b"))
        assertEquals("100%", GrpcFraming.decodeGrpcMessage("100%"))
        assertNull(GrpcFraming.decodeGrpcMessage(null))
    }
}
