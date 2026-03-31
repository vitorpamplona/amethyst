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
package com.vitorpamplona.quartz.nipBEBle.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BleChunkAssemblerTest {
    @Test
    fun singleChunkMessage() {
        val assembler = BleChunkAssembler()
        val message = """["OK","abc",true]"""
        val chunks = BleMessageChunker.splitIntoChunks(message)
        assertEquals(1, chunks.size)

        val result = assembler.addChunk(chunks[0])
        assertNotNull(result)
        assertEquals(message, result)
    }

    @Test
    fun multiChunkMessage() {
        val assembler = BleChunkAssembler()
        val message = """["EVENT",{"content":"hello world from nostr ble mesh networking"}]"""
        val chunks = BleMessageChunker.splitIntoChunks(message, chunkSize = 10)
        assertTrue(chunks.size > 1)

        for (i in 0 until chunks.size - 1) {
            val result = assembler.addChunk(chunks[i])
            assertNull(result, "Should not produce a message until all chunks received")
        }

        val result = assembler.addChunk(chunks.last())
        assertNotNull(result)
        assertEquals(message, result)
    }

    @Test
    fun resetClearsPartialChunks() {
        val assembler = BleChunkAssembler()
        val message = """["EVENT",{"content":"hello nostr ble mesh networking data"}]"""
        val chunks = BleMessageChunker.splitIntoChunks(message, chunkSize = 10)

        assembler.addChunk(chunks[0])
        assembler.reset()

        // After reset, should accept a new message cleanly
        val newMessage = """["OK"]"""
        val newChunks = BleMessageChunker.splitIntoChunks(newMessage)
        val result = assembler.addChunk(newChunks[0])
        assertNotNull(result)
        assertEquals(newMessage, result)
    }
}
