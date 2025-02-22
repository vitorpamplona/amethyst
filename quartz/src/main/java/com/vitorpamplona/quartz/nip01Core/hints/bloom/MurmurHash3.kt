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
    companion object {
        const val ROUND_DOWN = 0xFFFFFFFC.toInt()
        const val C1 = -0x3361d2af // 0xcc9e2d51
        const val C2 = 0x1b873593
    }

    /**
     * Generates 32 bit hash .
     * @param data the byte array to hash
     * @param seed the seed for the hash (int)
     * @return 32 bit hash of the given array
     */
    fun hash(
        data: ByteArray,
        seed: Int,
    ): Int {
        var h1 = seed
        val roundedEnd = data.size and ROUND_DOWN // Round down to 4-byte blocks

        var i = 0
        var k1 = 0
        while (i < roundedEnd) {
            k1 =
                (
                    data[i++].toInt() and 0xFF or
                        (data[i++].toInt() and 0xFF shl 8) or
                        (data[i++].toInt() and 0xFF shl 16) or
                        (data[i++].toInt() and 0xFF shl 24)
                ) * C1

            h1 = h1 xor (((k1 shl 15) or (k1 ushr -15)) * C2)
            h1 = ((h1 shl 13) or (h1 ushr -13)) * 5 + -0x19ab949c // 0xe6546b64
        }

        // processing tail (remaining bytes)
        k1 = 0
        when (data.size and 3) {
            3 -> {
                k1 = k1 or ((data[i + 2].toInt() and 0xFF) shl 16)
                k1 = k1 or ((data[i + 1].toInt() and 0xFF) shl 8)
                k1 = k1 or (data[i].toInt() and 0xFF)

                k1 *= C1
                k1 = (k1 shl 15) or (k1 ushr -15)
                k1 *= C2

                h1 = h1 xor k1
            }

            2 -> {
                k1 = k1 or (data[i + 1].toInt() and 0xFF shl 8)
                k1 = k1 or (data[i].toInt() and 0xFF)

                k1 *= C1
                k1 = (k1 shl 15) or (k1 ushr -15)
                k1 *= C2

                h1 = h1 xor k1
            }

            1 -> {
                k1 = k1 or (data[i].toInt() and 0xFF)

                k1 *= C1
                k1 = (k1 shl 15) or (k1 ushr -15)
                k1 *= C2

                h1 = h1 xor k1
            }
        }

        // final mix
        h1 = h1 xor data.size

        // fmix32
        h1 = (h1 xor (h1 ushr 16)) * -0x7a143595 // 0x85ebca6b
        h1 = (h1 xor (h1 ushr 13)) * -0x3d4d51cb // 0xc2b2ae35
        h1 = h1 xor (h1 ushr 16)

        return h1
    }
}
