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
package com.vitorpamplona.quartz.utils.sha2

/**
 * The SHA-256 block function (FIPS 180-4 §6.2), shared by the incremental [Sha256Digest]
 * and the proof-of-work miner's
 * [com.vitorpamplona.quartz.nip13Pow.miner.Sha256Midstate], which resumes from a
 * saved state.
 */
internal object Sha256Compression {
    const val BLOCK = 64

    private val IV =
        intArrayOf(
            0x6a09e667,
            -0x4498517b,
            0x3c6ef372,
            -0x5ab00ac6,
            0x510e527f,
            -0x64fa9774,
            0x1f83d9ab,
            0x5be0cd19,
        )

    private val K =
        intArrayOf(
            0x428a2f98,
            0x71374491,
            -0x4a3f0431,
            -0x164a245b,
            0x3956c25b,
            0x59f111f1,
            -0x6dc07d5c,
            -0x54e3a12b,
            -0x27f85568,
            0x12835b01,
            0x243185be,
            0x550c7dc3,
            0x72be5d74,
            -0x7f214e02,
            -0x6423f959,
            -0x3e640e8c,
            -0x1b64963f,
            -0x1041b87a,
            0x0fc19dc6,
            0x240ca1cc,
            0x2de92c6f,
            0x4a7484aa,
            0x5cb0a9dc,
            0x76f988da,
            -0x67c1aeae,
            -0x57ce3993,
            -0x4ffcd838,
            -0x40a68039,
            -0x391ff40d,
            -0x2a586eb9,
            0x06ca6351,
            0x14292967,
            0x27b70a85,
            0x2e1b2138,
            0x4d2c6dfc,
            0x53380d13,
            0x650a7354,
            0x766a0abb,
            -0x7e3d36d2,
            -0x6d8dd37b,
            -0x5d40175f,
            -0x57e599b5,
            -0x3db47490,
            -0x3893ae5d,
            -0x2e6d17e7,
            -0x2966f9dc,
            -0xbf1ca7b,
            0x106aa070,
            0x19a4c116,
            0x1e376c08,
            0x2748774c,
            0x34b0bcb5,
            0x391c0cb3,
            0x4ed8aa4a,
            0x5b9cca4f,
            0x682e6ff3,
            0x748f82ee,
            0x78a5636f,
            -0x7b3787ec,
            -0x7338fdf8,
            -0x6f410006,
            -0x5baf9315,
            -0x41065c09,
            -0x398e870e,
        )

    /** Loads the FIPS 180-4 initial hash value into [state]. */
    fun initState(state: IntArray) {
        IV.copyInto(state)
    }

    /**
     * One-shot SHA-256 of `data[offset until offset + length]` into [out] at [outOffset],
     * straight on the block function: no digest object, no buffer to reset afterwards.
     */
    fun hash(
        data: ByteArray,
        offset: Int,
        length: Int,
        out: ByteArray,
        outOffset: Int = 0,
    ) {
        require(offset >= 0 && length >= 0 && length <= data.size - offset) {
            "range [$offset, $offset + $length) is outside an array of ${data.size}"
        }
        require(outOffset >= 0 && 32 <= out.size - outOffset) { "no room for a 32-byte digest" }
        val state = IV.copyOf()
        val w = IntArray(64)
        var pos = offset
        val end = offset + length
        while (end - pos >= BLOCK) {
            compress(state, data, pos, w)
            pos += BLOCK
        }

        val remaining = end - pos
        // 0x80 marker + 8-byte length: one block if they fit after the remainder, else two
        val tail = ByteArray(if (remaining < BLOCK - 8) BLOCK else 2 * BLOCK)
        data.copyInto(tail, 0, pos, end)
        tail[remaining] = 0x80.toByte()
        val bitLength = length.toLong() * 8
        for (i in 0 until 8) {
            tail[tail.size - 1 - i] = (bitLength ushr (8 * i)).toByte()
        }
        compress(state, tail, 0, w)
        if (tail.size > BLOCK) compress(state, tail, BLOCK, w)

        for (i in 0 until 8) {
            val word = state[i]
            val o = outOffset + 4 * i
            out[o] = (word ushr 24).toByte()
            out[o + 1] = (word ushr 16).toByte()
            out[o + 2] = (word ushr 8).toByte()
            out[o + 3] = word.toByte()
        }
    }

    /**
     * One SHA-256 compression of `block[offset until offset + 64]` into [state].
     *
     * Eight rounds per iteration with the working variables renamed instead of
     * shifted, so a round is just its arithmetic: no seven-way variable shuffle.
     */
    fun compress(
        state: IntArray,
        block: ByteArray,
        offset: Int,
        w: IntArray,
    ) {
        for (t in 0 until 16) {
            val i = offset + 4 * t
            w[t] = (block[i].toInt() shl 24) or
                ((block[i + 1].toInt() and 0xff) shl 16) or
                ((block[i + 2].toInt() and 0xff) shl 8) or
                (block[i + 3].toInt() and 0xff)
        }
        for (t in 16 until 64) {
            val w15 = w[t - 15]
            val w2 = w[t - 2]
            val s0 = w15.rotateRight(7) xor w15.rotateRight(18) xor (w15 ushr 3)
            val s1 = w2.rotateRight(17) xor w2.rotateRight(19) xor (w2 ushr 10)
            w[t] = w[t - 16] + s0 + w[t - 7] + s1
        }

        val k = K
        var a = state[0]
        var b = state[1]
        var c = state[2]
        var d = state[3]
        var e = state[4]
        var f = state[5]
        var g = state[6]
        var h = state[7]

        var t = 0
        while (t < 64) {
            h += bigSigma1(e) + ch(e, f, g) + k[t] + w[t]
            d += h
            h += bigSigma0(a) + maj(a, b, c)
            t++

            g += bigSigma1(d) + ch(d, e, f) + k[t] + w[t]
            c += g
            g += bigSigma0(h) + maj(h, a, b)
            t++

            f += bigSigma1(c) + ch(c, d, e) + k[t] + w[t]
            b += f
            f += bigSigma0(g) + maj(g, h, a)
            t++

            e += bigSigma1(b) + ch(b, c, d) + k[t] + w[t]
            a += e
            e += bigSigma0(f) + maj(f, g, h)
            t++

            d += bigSigma1(a) + ch(a, b, c) + k[t] + w[t]
            h += d
            d += bigSigma0(e) + maj(e, f, g)
            t++

            c += bigSigma1(h) + ch(h, a, b) + k[t] + w[t]
            g += c
            c += bigSigma0(d) + maj(d, e, f)
            t++

            b += bigSigma1(g) + ch(g, h, a) + k[t] + w[t]
            f += b
            b += bigSigma0(c) + maj(c, d, e)
            t++

            a += bigSigma1(f) + ch(f, g, h) + k[t] + w[t]
            e += a
            a += bigSigma0(b) + maj(b, c, d)
            t++
        }

        state[0] += a
        state[1] += b
        state[2] += c
        state[3] += d
        state[4] += e
        state[5] += f
        state[6] += g
        state[7] += h
    }
}

private fun bigSigma0(x: Int) = x.rotateRight(2) xor x.rotateRight(13) xor x.rotateRight(22)

private fun bigSigma1(x: Int) = x.rotateRight(6) xor x.rotateRight(11) xor x.rotateRight(25)

private fun ch(
    x: Int,
    y: Int,
    z: Int,
) = z xor (x and (y xor z))

private fun maj(
    x: Int,
    y: Int,
    z: Int,
) = (x and y) or (z and (x or y))
