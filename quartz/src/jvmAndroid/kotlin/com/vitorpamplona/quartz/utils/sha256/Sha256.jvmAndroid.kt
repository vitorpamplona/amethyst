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
package com.vitorpamplona.quartz.utils.sha256

import java.io.InputStream
import java.security.MessageDigest

/**
 * Thread-local MessageDigest for lock-free SHA256 hashing.
 *
 * The previous ArrayBlockingQueue pool added ~10µs of synchronization overhead per call
 * (lock acquire + release) for ~2µs of actual hashing. ThreadLocal eliminates all locking
 * since each thread gets its own MessageDigest instance. digest() implicitly resets state.
 */
val threadLocalDigest =
    ThreadLocal.withInitial {
        MessageDigest.getInstance("SHA-256")
    }

actual fun sha256(data: ByteArray): ByteArray = threadLocalDigest.get().digest(data)

actual fun sha256Into(
    out: ByteArray,
    data: ByteArray,
    len: Int,
): ByteArray {
    val md = threadLocalDigest.get()
    md.update(data, 0, len)
    md.digest(out, 0, 32)
    return out
}

/**
 * Calculate SHA256 hash while counting bytes read from the stream.
 * Returns both the hash and the number of bytes processed.
 * This is more efficient than reading the stream twice.
 *
 * @param inputStream The input stream to hash
 * @param bufferSize Size of chunks to read (default 8KB)
 * @return Pair of (hash bytes, bytes read count)
 */
fun sha256StreamWithCount(
    inputStream: InputStream,
    bufferSize: Int = 8192,
): Pair<ByteArray, Long> {
    val countingStream = CountingInputStream(inputStream)
    val digest = threadLocalDigest.get()
    try {
        val buffer = ByteArray(bufferSize)
        var bytesRead: Int
        while (countingStream.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
        return Pair(digest.digest(), countingStream.bytesRead)
    } catch (e: Exception) {
        digest.reset() // Clean up state on failure
        throw e
    }
}
