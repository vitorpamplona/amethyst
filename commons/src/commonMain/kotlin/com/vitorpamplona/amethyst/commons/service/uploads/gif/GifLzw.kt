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
package com.vitorpamplona.amethyst.commons.service.uploads.gif

/**
 * GIF's LZW: variable-width codes (from `minCodeSize + 1` up to 12 bits), packed least significant
 * bit first, with a clear code and an end code. Both directions, for [GifReader] and [GifWriter].
 */
internal object GifLzw {
    private const val MAX_BITS = 12
    private const val MAX_CODES = 1 shl MAX_BITS

    /**
     * Decodes [data] into color indices, at most [pixelCount] of them (a short stream leaves the
     * rest unwritten and returns how many it wrote).
     */
    fun decode(
        data: ByteArray,
        minCodeSize: Int,
        out: ByteArray,
        pixelCount: Int,
    ): Int {
        if (minCodeSize !in 1..11) return 0
        val clear = 1 shl minCodeSize
        val end = clear + 1
        val prefix = IntArray(MAX_CODES)
        val suffix = ByteArray(MAX_CODES)
        val stack = ByteArray(MAX_CODES + 1)
        for (i in 0 until clear) suffix[i] = i.toByte()

        var codeSize = minCodeSize + 1
        var next = clear + 2
        var old = -1
        var first = 0
        var written = 0

        var bitBuffer = 0
        var bitCount = 0
        var at = 0
        while (written < pixelCount) {
            while (bitCount < codeSize) {
                if (at >= data.size) return written
                bitBuffer = bitBuffer or ((data[at++].toInt() and 0xFF) shl bitCount)
                bitCount += 8
            }
            var code = bitBuffer and ((1 shl codeSize) - 1)
            bitBuffer = bitBuffer ushr codeSize
            bitCount -= codeSize

            if (code == clear) {
                codeSize = minCodeSize + 1
                next = clear + 2
                old = -1
                continue
            }
            if (code == end) break
            if (old == -1) {
                if (code >= clear) return written
                out[written++] = code.toByte()
                old = code
                first = code
                continue
            }
            val incoming = code
            var top = 0
            if (code >= next) {
                // The code being defined right now: the previous string plus its own first byte.
                stack[top++] = first.toByte()
                code = old
            }
            while (code >= clear) {
                if (code >= MAX_CODES || top >= stack.size) return written
                stack[top++] = suffix[code]
                code = prefix[code]
            }
            first = suffix[code].toInt() and 0xFF
            stack[top++] = first.toByte()
            while (top > 0 && written < pixelCount) out[written++] = stack[--top]
            if (next < MAX_CODES) {
                prefix[next] = old
                suffix[next] = first.toByte()
                next++
                if (next == (1 shl codeSize) && codeSize < MAX_BITS) codeSize++
            }
            old = incoming
        }
        return written
    }

    /** Encodes [indices] (each below `1 shl minCodeSize`) as one image's LZW stream, not yet split into sub-blocks. */
    fun encode(
        indices: ByteArray,
        count: Int,
        minCodeSize: Int,
    ): ByteArray = Encoder(minCodeSize).run(indices, count)

    // The classic GIF encoder's scheme (a hashed string table, a full table answered with a clear
    // code), so its code-width changes match what every decoder expects.
    private class Encoder(
        minCodeSize: Int,
    ) {
        private val initBits = minCodeSize + 1
        private val clearCode = 1 shl minCodeSize
        private val endCode = clearCode + 1
        private val keys = IntArray(HASH_SIZE) { -1 }
        private val codes = IntArray(HASH_SIZE)
        private var bits = initBits
        private var maxCode = (1 shl bits) - 1
        private var nextCode = clearCode + 2
        private var clearPending = false

        private var accumulator = 0
        private var accumulated = 0
        private val out = ByteBuffer()

        fun run(
            indices: ByteArray,
            count: Int,
        ): ByteArray {
            output(clearCode)
            if (count > 0) {
                var prefix = indices[0].toInt() and 0xFF
                for (p in 1 until count) {
                    val c = indices[p].toInt() and 0xFF
                    val key = (c shl MAX_BITS) + prefix
                    var i = (c shl HASH_SHIFT) xor prefix
                    if (keys[i] == key) {
                        prefix = codes[i]
                        continue
                    }
                    if (keys[i] >= 0) {
                        val step = if (i == 0) 1 else HASH_SIZE - i
                        var found = false
                        while (true) {
                            i -= step
                            if (i < 0) i += HASH_SIZE
                            if (keys[i] == key) {
                                prefix = codes[i]
                                found = true
                                break
                            }
                            if (keys[i] < 0) break
                        }
                        if (found) continue
                    }
                    output(prefix)
                    prefix = c
                    if (nextCode < MAX_CODES) {
                        codes[i] = nextCode++
                        keys[i] = key
                    } else {
                        keys.fill(-1)
                        nextCode = clearCode + 2
                        clearPending = true
                        output(clearCode)
                    }
                }
                output(prefix)
            }
            output(endCode)
            return out.toByteArray()
        }

        private fun output(code: Int) {
            accumulator = (accumulator and ((1 shl accumulated) - 1)) or (code shl accumulated)
            accumulated += bits
            while (accumulated >= 8) {
                out.add(accumulator and 0xFF)
                accumulator = accumulator ushr 8
                accumulated -= 8
            }
            if (nextCode > maxCode || clearPending) {
                if (clearPending) {
                    bits = initBits
                    maxCode = (1 shl bits) - 1
                    clearPending = false
                } else {
                    bits++
                    maxCode = if (bits == MAX_BITS) MAX_CODES else (1 shl bits) - 1
                }
            }
            if (code == endCode) {
                while (accumulated > 0) {
                    out.add(accumulator and 0xFF)
                    accumulator = accumulator ushr 8
                    accumulated -= 8
                }
            }
        }
    }

    private const val HASH_SIZE = 5003
    private const val HASH_SHIFT = 4
}

/** A growable byte array, for the codec's output. */
internal class ByteBuffer(
    capacity: Int = 1024,
) {
    private var bytes = ByteArray(capacity)
    var size = 0
        private set

    fun add(value: Int) {
        if (size == bytes.size) bytes = bytes.copyOf(bytes.size * 2)
        bytes[size++] = value.toByte()
    }

    fun add(values: ByteArray) {
        if (size + values.size > bytes.size) bytes = bytes.copyOf(maxOf(bytes.size * 2, size + values.size))
        values.copyInto(bytes, size)
        size += values.size
    }

    fun addShort(value: Int) {
        add(value and 0xFF)
        add((value ushr 8) and 0xFF)
    }

    fun toByteArray(): ByteArray = bytes.copyOf(size)
}
