/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.quartz.nip01Core.hints.bloom

class MurmurHash3 {
    fun hash(
        data: ByteArray,
        seed: Int,
    ) = hash(data, 0, data.size, seed)

    /**
     * Generates 32 bit hash .
     * @param data the byte array to hash
     * @param offset the start offset of the data in the array (always 0)
     * @param length the length of the data in the array
     * @param seed the seed for the hash (int)
     * @return 32 bit hash of the given array
     */
    fun hash(
        data: ByteArray,
        offset: Int,
        length: Int,
        seed: Int,
    ): Int {
        val c1 = -0x3361d2af // 0xcc9e2d51
        val c2 = 0x1b873593
        var h1 = seed
        val roundedEnd = offset + (length and 0xFFFFFFFC.toInt()) // Round down to 4-byte blocks

        var i = offset
        while (i < roundedEnd) {
            var k1 =
                (data[i].toInt() and 0xFF) or
                    ((data[i + 1].toInt() and 0xFF) shl 8) or
                    ((data[i + 2].toInt() and 0xFF) shl 16) or
                    ((data[i + 3].toInt() and 0xFF) shl 24)

            i += 4

            k1 *= c1
            k1 = Integer.rotateLeft(k1, 15)
            k1 *= c2

            h1 = h1 xor k1
            h1 = Integer.rotateLeft(h1, 13)
            h1 = h1 * 5 + -0x19ab949c // 0xe6546b64
        }

        // processing tail (remaining bytes)
        var k1 = 0
        when (length and 3) {
            3 -> {
                k1 = k1 or ((data[i + 2].toInt() and 0xFF) shl 16)
                k1 = k1 or ((data[i + 1].toInt() and 0xFF) shl 8)
                k1 = k1 or (data[i].toInt() and 0xFF)

                k1 *= c1
                k1 = Integer.rotateLeft(k1, 15)
                k1 *= c2

                h1 = h1 xor k1
            }

            2 -> {
                k1 = k1 or (data[i + 1].toInt() and 0xFF shl 8)
                k1 = k1 or (data[i].toInt() and 0xFF)

                k1 *= c1
                k1 = Integer.rotateLeft(k1, 15)
                k1 *= c2

                h1 = h1 xor k1
            }

            1 -> {
                k1 = k1 or (data[i].toInt() and 0xFF)

                k1 *= c1
                k1 = Integer.rotateLeft(k1, 15)
                k1 *= c2

                h1 = h1 xor k1
            }
        }

        // final mix
        h1 = h1 xor length
        h1 = fmix32(h1)

        return h1
    }

    private fun fmix32(h: Int): Int {
        var f = h
        f = f xor (f ushr 16)
        f *= -0x7a143595 // 0x85ebca6b
        f = f xor (f ushr 13)
        f *= -0x3d4d51cb // 0xc2b2ae35
        f = f xor (f ushr 16)

        return f
    }
}
