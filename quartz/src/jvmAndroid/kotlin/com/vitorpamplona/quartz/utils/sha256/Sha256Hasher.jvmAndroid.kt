/**
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
package com.vitorpamplona.quartz.utils.sha256

import java.io.InputStream
import java.security.MessageDigest

class Sha256Hasher {
    val digest = MessageDigest.getInstance("SHA-256")

    fun hash(byteArray: ByteArray) = digest.digest(byteArray).also { digest.reset() }

    fun update(
        input: ByteArray,
        offset: Int,
        length: Int,
    ) = digest.update(input, offset, length)

    fun digest(byteArray: ByteArray) = digest.digest(byteArray)

    fun digest() = digest.digest()

    fun reset() = digest.reset()

    /**
     * Calculate SHA256 hash by streaming the input in chunks.
     * This avoids loading the entire input into memory at once.
     *
     * @param inputStream The input stream to hash
     * @param bufferSize Size of chunks to read (default 8KB)
     * @return SHA256 hash bytes
     */
    fun hashStream(
        inputStream: InputStream,
        bufferSize: Int = 8192,
    ): ByteArray {
        val buffer = ByteArray(bufferSize)
        try {
            var bytesRead: Int

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }

            return digest.digest()
        } finally {
            // Always reset digest to prevent contaminating the pool on exceptions
            digest.reset()
        }
    }
}
