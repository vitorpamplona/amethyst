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
package com.vitorpamplona.quartz.nip01Core.hints.bloom

class MurmurHash3 {
    companion object {
        private const val ROUND_DOWN = 0xFFFFFFFC.toInt()
        private const val C1_32 = -0x3361d2af // 0xcc9e2d51
        private const val C2_32 = 0x1b873593
        private const val N_32: Int = -0x19ab949c // 0xe6546b64

        private const val C1_128_X64: Long = -0x783c846eeebdac2bL
        private const val C2_128_X64: Long = 0x4cf5ad432745937fL

        private const val R1_128_X64: Int = 31
        private const val R2_128_X64: Int = 27
        private const val R3_128_X64: Int = 33
    }

    /**
     * Generates 32 bit hash, x86 variant.
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
                ) * C1_32

            h1 = h1 xor k1.rotateLeft(15) * C2_32
            h1 = h1.rotateLeft(13) * 5 + N_32
        }

        // processing tail (remaining bytes)
        k1 = 0
        when (data.size and 3) {
            3 -> {
                k1 = k1 or ((data[i + 2].toInt() and 0xFF) shl 16)
                k1 = k1 or ((data[i + 1].toInt() and 0xFF) shl 8)
                k1 = k1 or (data[i].toInt() and 0xFF)

                k1 *= C1_32
                k1 = (k1 shl 15) or (k1 ushr -15)
                k1 *= C2_32

                h1 = h1 xor k1
            }

            2 -> {
                k1 = k1 or (data[i + 1].toInt() and 0xFF shl 8)
                k1 = k1 or (data[i].toInt() and 0xFF)

                k1 *= C1_32
                k1 = (k1 shl 15) or (k1 ushr -15)
                k1 *= C2_32

                h1 = h1 xor k1
            }

            1 -> {
                k1 = k1 or (data[i].toInt() and 0xFF)

                k1 *= C1_32
                k1 = (k1 shl 15) or (k1 ushr -15)
                k1 *= C2_32

                h1 = h1 xor k1
            }
        }

        // final mix
        h1 = h1 xor data.size

        // fmix32
        h1 = h1.fmix()

        return h1
    }

    public fun hash128x64(
        data: ByteArray,
        seed: Long,
    ): Pair<Long, Long> {
        var h1 = seed
        var h2 = seed
        val len = data.size
        val nblocks = len / 16

        var i = 0
        while (i < nblocks) {
            val k1 =
                (
                    data[i++].long() or
                        (data[i++].long() shl 8) or
                        (data[i++].long() shl 16) or
                        (data[i++].long() shl 24) or
                        (data[i++].long() shl 32) or
                        (data[i++].long() shl 40) or
                        (data[i++].long() shl 48) or
                        (data[i++].long() shl 56)
                ) * C1_128_X64

            val k2 =
                (
                    data[i++].long() or
                        (data[i++].long() shl 8) or
                        (data[i++].long() shl 16) or
                        (data[i++].long() shl 24) or
                        (data[i++].long() shl 32) or
                        (data[i++].long() shl 40) or
                        (data[i++].long() shl 48) or
                        (data[i++].long() shl 56)
                ) * C2_128_X64

            h1 = h1 xor k1.rotateLeft(R1_128_X64) * C2_128_X64
            h1 = (h1.rotateLeft(R2_128_X64) + h2) * 5 + 0x52dce729

            h2 = h2 xor k2.rotateLeft(R3_128_X64) * C1_128_X64
            h2 = (h2.rotateLeft(R1_128_X64) + h1) * 5 + 0x38495ab5
        }

        val index = nblocks * 16
        val rem = len - index
        var k1 = 0L
        var k2 = 0L

        if (rem == 15) {
            k2 = k2 xor (data[index + 14].long() shl 48)
        }
        if (rem >= 14) {
            k2 = k2 xor (data[index + 13].long() shl 40)
        }
        if (rem >= 13) {
            k2 = k2 xor (data[index + 12].long() shl 32)
        }
        if (rem >= 12) {
            k2 = k2 xor (data[index + 11].long() shl 24)
        }
        if (rem >= 11) {
            k2 = k2 xor (data[index + 10].long() shl 16)
        }
        if (rem >= 10) {
            k2 = k2 xor (data[index + 9].long() shl 8)
        }
        if (rem >= 9) {
            k2 = k2 xor data[index + 8].long()
            h2 = h2 xor (k2 * C2_128_X64).rotateLeft(R3_128_X64) * C1_128_X64
        }
        if (rem >= 8) {
            k1 = k1 xor (data[index + 7].long() shl 56)
        }
        if (rem >= 7) {
            k1 = k1 xor (data[index + 6].long() shl 48)
        }
        if (rem >= 6) {
            k1 = k1 xor (data[index + 5].long() shl 40)
        }
        if (rem >= 5) {
            k1 = k1 xor (data[index + 4].long() shl 32)
        }
        if (rem >= 4) {
            k1 = k1 xor (data[index + 3].long() shl 24)
        }
        if (rem >= 3) {
            k1 = k1 xor (data[index + 2].long() shl 16)
        }
        if (rem >= 2) {
            k1 = k1 xor (data[index + 1].long() shl 8)
        }
        if (rem >= 1) {
            k1 = k1 xor data[index].long()
            h1 = h1 xor (k1 * C1_128_X64).rotateLeft(R1_128_X64) * C2_128_X64
        }

        h1 = h1 xor len.toLong()
        h2 = h2 xor len.toLong()

        h1 += h2
        h2 += h1

        h1 = h1.fmix()
        h2 = h2.fmix()

        h1 += h2
        h2 += h1

        return Pair(h1, h2)
    }

    fun Byte.long() = toLong() and 0xffL

    private fun Int.fmix(): Int {
        var h = this
        h = (h xor (h ushr 16)) * -0x7a143595 // 0x85ebca6b
        h = (h xor (h ushr 13)) * -0x3d4d51cb // 0xc2b2ae35
        h = h xor (h ushr 16)
        return h
    }

    private fun Long.fmix(): Long {
        var h = this
        h = (h xor (h ushr 33)) * -0xae502812aa7333L
        h = (h xor (h ushr 33)) * -0x3b314601e57a13adL
        h = h xor (h ushr 33)
        return h
    }
}
