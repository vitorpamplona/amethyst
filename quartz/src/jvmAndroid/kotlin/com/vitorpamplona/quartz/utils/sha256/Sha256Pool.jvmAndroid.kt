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

import com.vitorpamplona.quartz.utils.Log
import java.io.InputStream
import java.util.concurrent.ArrayBlockingQueue

class Sha256Pool(
    size: Int,
) {
    private val pool = ArrayBlockingQueue<Sha256Hasher>(size)

    init {
        repeat(size) {
            pool.add(Sha256Hasher())
        }
    }

    fun acquire(): Sha256Hasher {
        if (pool.isEmpty()) {
            Log.w("SHA256Pool", "Pool running low in available digests")
        }
        return pool.take()
    }

    fun release(digest: Sha256Hasher) {
        digest.reset()
        pool.put(digest)
    }

    fun hash(byteArray: ByteArray): ByteArray {
        val hasher = acquire()
        try {
            return hasher.digest(byteArray)
        } finally {
            release(hasher)
        }
    }

    inline fun hasher(action: (hasher: Sha256Hasher) -> ByteArray): ByteArray {
        val hasher = acquire()
        try {
            return action(hasher)
        } finally {
            release(hasher)
        }
    }

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
        val hasher = acquire()
        try {
            return hasher.hashStream(inputStream, bufferSize)
        } finally {
            release(hasher)
        }
    }
}
