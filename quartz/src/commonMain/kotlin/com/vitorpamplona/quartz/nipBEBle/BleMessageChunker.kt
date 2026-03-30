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

import com.vitorpamplona.quartz.utils.Deflate

/**
 * Handles NIP-BE message chunking and compression for BLE transport.
 *
 * NIP-BE messages are DEFLATE-compressed and split into chunks with the format:
 * ```
 * [batch index (first 2 bytes)][payload][total batches (last byte)]
 * ```
 *
 * Each chunk carries a 2-byte index prefix and a 1-byte total count suffix,
 * so the payload per chunk is `chunkSize - 3` bytes of compressed data.
 */
object BleMessageChunker {
    /**
     * Compresses and splits a NIP-01 JSON message into BLE-sized chunks.
     *
     * @param message The JSON message string (e.g., `["EVENT", {...}]`).
     * @param chunkSize The maximum size of each chunk in bytes (default 500).
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

        // Overhead per chunk: 2 bytes index prefix + 1 byte total count suffix
        val payloadSize = chunkSize - 3
        require(payloadSize > 0) { "chunkSize must be at least 4 bytes" }

        val numChunks = (compressed.size + payloadSize - 1) / payloadSize

        require(numChunks <= 255) {
            "Message requires $numChunks chunks but maximum is 255"
        }

        return Array(numChunks) { i ->
            val start = i * payloadSize
            val end = minOf((i + 1) * payloadSize, compressed.size)
            val payload = compressed.copyOfRange(start, end)

            // [2-byte index][payload][1-byte total]
            val chunk = ByteArray(payload.size + 3)
            chunk[0] = (i shr 8).toByte()
            chunk[1] = (i and 0xFF).toByte()
            payload.copyInto(chunk, 2)
            chunk[chunk.size - 1] = numChunks.toByte()

            chunk
        }
    }

    /**
     * Reassembles and decompresses chunks back into a NIP-01 JSON message.
     *
     * Chunks can arrive in any order; they are sorted by their index prefix.
     *
     * @param chunks The received chunks.
     * @return The original JSON message string.
     */
    fun joinChunks(chunks: Array<ByteArray>): String {
        val sorted = chunks.sortedBy { chunkIndex(it) }

        var reassembled = ByteArray(0)
        for (chunk in sorted) {
            val payload = chunk.copyOfRange(2, chunk.size - 1)
            val newArray = ByteArray(reassembled.size + payload.size)
            reassembled.copyInto(newArray)
            payload.copyInto(newArray, reassembled.size)
            reassembled = newArray
        }

        return Deflate.decompress(reassembled).decodeToString()
    }

    /**
     * Extracts the 2-byte chunk index from a chunk.
     */
    fun chunkIndex(chunk: ByteArray): Int = ((chunk[0].toInt() and 0xFF) shl 8) or (chunk[1].toInt() and 0xFF)

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
