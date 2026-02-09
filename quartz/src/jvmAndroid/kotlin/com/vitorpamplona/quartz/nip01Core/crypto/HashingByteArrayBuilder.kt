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
package com.vitorpamplona.quartz.nip01Core.crypto

import com.fasterxml.jackson.core.util.BufferRecycler
import com.vitorpamplona.quartz.utils.sha256.Sha256Pool
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Computes hashes of the byte array produced by Jackson
 * while reusing the largest buffer it ended up being.
 *
 * This was based on the ByteArrayBuilder form Jackson
 */
class HashingByteArrayBuilder(
    private val bufferRecycler: BufferRecycler,
    private val hashPool: Sha256Pool,
) : OutputStream() {
    var buf: ByteArray = bufferRecycler.allocByteBuffer(BufferRecycler.BYTE_WRITE_CONCAT_BUFFER)
    var bufLength: Int = 0

    // lazy minimizes lock conflicts with the pool
    val hasher by lazy {
        hashPool.acquire()
    }

    fun release() {
        bufLength = 0
        bufferRecycler.releaseByteBuffer(BufferRecycler.BYTE_WRITE_CONCAT_BUFFER, buf)
        hashPool.release(hasher)
        buf = EMPTY_ARRAY
    }

    fun sha256(): ByteArray {
        if (bufLength > 0) {
            hasher.update(buf, 0, bufLength)
        }

        return hasher.digest()
    }

    override fun write(b: ByteArray) = write(b, 0, b.size)

    override fun write(
        b: ByteArray,
        off: Int,
        len: Int,
    ) {
        var off = off
        var len = len
        while (true) {
            val toCopy = min(buf.size - bufLength, len)
            if (toCopy > 0) {
                b.copyInto(buf, bufLength, off, off + toCopy)
                off += toCopy
                this.bufLength += toCopy
                len -= toCopy
            }
            if (len <= 0) break
            allocMore()
        }
    }

    override fun write(b: Int) {
        if (this.bufLength >= buf.size) {
            allocMore()
        }
        this.buf[this.bufLength++] = b.toByte()
    }

    override fun close() {}

    override fun flush() {
        hasher.update(buf, 0, bufLength)
        bufLength = 0
    }

    private fun allocMore() {
        flush()
        buf = ByteArray(max((buf.size shr 1), 1000))
    }

    companion object {
        val EMPTY_ARRAY = ByteArray(0)
    }
}
