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
package com.vitorpamplona.quartz.nipBEBle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BleMessageChunkerTest {
    @Test
    fun roundTripShortMessage() {
        val message = """["EVENT",{"id":"abc","kind":1,"content":"hello"}]"""
        val chunks = BleMessageChunker.splitIntoChunks(message)
        val result = BleMessageChunker.joinChunks(chunks)
        assertEquals(message, result)
    }

    @Test
    fun roundTripMultiChunkMessage() {
        // Use a small chunk size to force multiple chunks even with compressed data
        val message = """["EVENT",{"id":"abc","kind":1,"content":"hello world from nostr ble"}]"""
        val chunks = BleMessageChunker.splitIntoChunks(message, chunkSize = 10)
        assertTrue(chunks.size > 1, "Should produce multiple chunks with chunkSize=10")
        val result = BleMessageChunker.joinChunks(chunks)
        assertEquals(message, result)
    }

    @Test
    fun chunksHaveCorrectFormat() {
        val message = """["EVENT",{"id":"abc","kind":1,"content":"hello world"}]"""
        val chunks = BleMessageChunker.splitIntoChunks(message, chunkSize = 20)

        for ((i, chunk) in chunks.withIndex()) {
            // First 2 bytes are the index
            assertEquals(i, BleMessageChunker.chunkIndex(chunk))
            // Last byte is the total count
            assertEquals(chunks.size, BleMessageChunker.totalChunks(chunk))
            // Each chunk should be at most chunkSize
            assertTrue(chunk.size <= 20, "Chunk size ${chunk.size} exceeds max 20")
        }
    }

    @Test
    fun isCompleteReturnsFalseForPartialChunks() {
        val message = """["EVENT",{"id":"abc","kind":1,"content":"hello world from nostr ble"}]"""
        val chunks = BleMessageChunker.splitIntoChunks(message, chunkSize = 10)
        assertTrue(chunks.size > 1, "Need multiple chunks for this test")

        val partial = listOf(chunks[0])
        assertFalse(BleMessageChunker.isComplete(partial))
    }

    @Test
    fun isCompleteReturnsTrueWhenAllChunksReceived() {
        val message = """["EVENT",{"id":"abc","kind":1,"content":"hello"}]"""
        val chunks = BleMessageChunker.splitIntoChunks(message, chunkSize = 50)
        assertTrue(BleMessageChunker.isComplete(chunks.toList()))
    }

    @Test
    fun joinChunksHandlesOutOfOrderDelivery() {
        val message = """["EVENT",{"id":"abc123","kind":1,"content":"nostr ble mesh networking"}]"""
        val chunks = BleMessageChunker.splitIntoChunks(message, chunkSize = 10)
        assertTrue(chunks.size > 2, "Need 3+ chunks for out-of-order test")

        // Reverse the order
        val reversed = chunks.reversed().toTypedArray()
        val result = BleMessageChunker.joinChunks(reversed)
        assertEquals(message, result)
    }

    @Test
    fun singleChunkForSmallMessage() {
        val message = """["OK"]"""
        val chunks = BleMessageChunker.splitIntoChunks(message, chunkSize = 500)
        assertEquals(1, chunks.size)
        assertEquals(0, BleMessageChunker.chunkIndex(chunks[0]))
        assertEquals(1, BleMessageChunker.totalChunks(chunks[0]))
    }

    @Test
    fun rejectsOversizedMessage() {
        // Generate data that compresses poorly by using all 256 byte values in a
        // pseudo-random pattern. A 512KB input with this pattern stays above 64KB
        // after DEFLATE compression.
        val size = 512 * 1024
        val sb = StringBuilder(size)
        var state = 7919L // prime seed
        for (i in 0 until size) {
            // Simple LCG to produce varied characters
            state = (state * 1103515245L + 12345L) and 0x7FFFFFFF
            sb.append(('!' + (state % 94).toInt())) // printable ASCII range
        }
        val message = sb.toString()
        assertFails {
            BleMessageChunker.splitIntoChunks(message)
        }
    }

    @Test
    fun customChunkSize() {
        val message = """["EVENT",{"content":"${"a".repeat(100)}"}]"""
        val smallChunks = BleMessageChunker.splitIntoChunks(message, chunkSize = 10)
        val largeChunks = BleMessageChunker.splitIntoChunks(message, chunkSize = 500)
        assertTrue(smallChunks.size > largeChunks.size)

        // Both should produce the same result
        assertEquals(
            BleMessageChunker.joinChunks(smallChunks),
            BleMessageChunker.joinChunks(largeChunks),
        )
    }
}
