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
package com.vitorpamplona.quartz.utils

/**
 * Converted from java.io.ByteArrayOutputStream
 */
class ByteArrayOutputStream(
    size: Int = 32,
) {
    var buf: ByteArray = ByteArray(size)
    var count: Int = 0

    private fun ensureCapacity(minCapacity: Int) {
        if (minCapacity - buf.size > 0) {
            grow(minCapacity)
        }
    }

    private fun grow(minCapacity: Int) {
        val oldCapacity = buf.size
        var newCapacity = oldCapacity shl 1
        if (newCapacity - minCapacity < 0) newCapacity = minCapacity
        if (newCapacity - MAX_ARRAY_SIZE > 0) newCapacity = hugeCapacity(minCapacity)

        buf = buf.copyOf(newCapacity)
    }

    fun write(b: Byte) {
        ensureCapacity(count + 1)
        buf[count] = b
        count += 1
    }

    fun write(
        b: ByteArray,
        off: Int,
        len: Int,
    ) {
        if ((off or len) < 0 || len > b.size - off) {
            throw IllegalArgumentException("Invalid input bounds")
        }

        ensureCapacity(count + len)
        b.copyInto(buf, count, off, off + len)
        count += len
    }

    fun write(b: ByteArray) {
        ensureCapacity(count + b.size)
        b.copyInto(buf, count, 0, b.size)
        count += b.size
    }

    fun reset() {
        count = 0
    }

    fun toByteArray(): ByteArray = buf.copyOf(count)

    fun size(): Int = count

    override fun toString(): String = buf.decodeToString(0, count)

    companion object {
        private val MAX_ARRAY_SIZE: Int = Int.MAX_VALUE - 8

        private fun hugeCapacity(minCapacity: Int): Int {
            // overflow
            if (minCapacity < 0) throw RuntimeException("Buffer overflow new size $minCapacity")
            return if (minCapacity > MAX_ARRAY_SIZE) Int.MAX_VALUE else MAX_ARRAY_SIZE
        }
    }
}
