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

import com.vitorpamplona.quartz.nipBEBle.BleConfig
import com.vitorpamplona.quartz.utils.Deflate

/**
 * Handles NIP-BE message chunking and compression for BLE transport.
 *
 * NIP-BE messages are DEFLATE-compressed and split into chunks with the format:
 * ```
 * [chunk index (1 byte)][payload (up to chunkSize bytes)][total chunks (1 byte)]
 * ```
 *
 * Each chunk carries a 1-byte index prefix and a 1-byte total count suffix
 * (2 bytes overhead), matching the reference implementation in KoalaSat/samiz.
 */
object BleMessageChunker {
    /** Per-chunk overhead: 1 byte index + 1 byte total count. */
    const val CHUNK_OVERHEAD = 2

    /**
     * Compresses and splits a NIP-01 JSON message into BLE-sized chunks.
     *
     * @param message The JSON message string (e.g., `["EVENT", {...}]`).
     * @param chunkSize The maximum payload size per chunk in bytes (default 500).
     *   The actual transmitted chunk size is `payload + 2` (index + total count).
     * @return Array of byte arrays, each representing one chunk.
     * @throws IllegalArgumentException if compressed message exceeds 64KB.
     */
    fun splitIntoChunks(
        message: String,
        chunkSize: Int = BleConfig.DEFAULT_CHUNK_SIZE,
    ): Array<ByteArray> {
        val compressed = Deflate.compress(message.encodeToByteArray())

        require(compressed.size <= BleConfig.MAX_MESSAGE_SIZE) {
            "Compressed message size ${compressed.size} exceeds maximum ${BleConfig.MAX_MESSAGE_SIZE} bytes"
        }

        require(chunkSize > 0) { "chunkSize must be positive" }

        val numChunks = (compressed.size + chunkSize - 1) / chunkSize

        require(numChunks <= 255) {
            "Message requires $numChunks chunks but maximum is 255"
        }

        return Array(numChunks) { i ->
            val start = i * chunkSize
            val end = minOf((i + 1) * chunkSize, compressed.size)
            val payload = compressed.copyOfRange(start, end)

            // [1-byte index][payload][1-byte total]
            val chunk = ByteArray(payload.size + CHUNK_OVERHEAD)
            chunk[0] = i.toByte()
            payload.copyInto(chunk, 1)
            chunk[chunk.size - 1] = numChunks.toByte()

            chunk
        }
    }

    /**
     * Reassembles and decompresses chunks back into a NIP-01 JSON message.
     *
     * Chunks can arrive in any order; they are sorted by their index byte.
     *
     * @param chunks The received chunks.
     * @return The original JSON message string.
     */
    fun joinChunks(chunks: Array<ByteArray>): String {
        val sorted = chunks.sortedBy { chunkIndex(it) }

        var reassembled = ByteArray(0)
        for (chunk in sorted) {
            val payload = chunk.copyOfRange(1, chunk.size - 1)
            val newArray = ByteArray(reassembled.size + payload.size)
            reassembled.copyInto(newArray)
            payload.copyInto(newArray, reassembled.size)
            reassembled = newArray
        }

        return Deflate.decompress(reassembled).decodeToString()
    }

    /**
     * Extracts the 1-byte chunk index from a chunk.
     */
    fun chunkIndex(chunk: ByteArray): Int = chunk[0].toInt() and 0xFF

    /**
     * Extracts the total number of expected chunks from a chunk's last byte.
     */
    fun totalChunks(chunk: ByteArray): Int = chunk[chunk.size - 1].toInt() and 0xFF

    /**
     * Checks whether all chunks for a complete message have been received.
     */
    fun isComplete(chunks: List<ByteArray>): Boolean {
        if (chunks.isEmpty()) return false
        val expected = totalChunks(chunks.first())
        return chunks.size >= expected
    }
}
