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
 * Pure-Kotlin incremental SHA-2 (FIPS 180-4), for the targets without a platform
 * digest Quartz trusts to call directly. JVM and Android use JCA instead.
 *
 * The subclasses provide the block function; this class does the buffering and the
 * padding. The message length is counted in a [Long], so inputs past 2 GiB hash
 * correctly. SHA-2 has no table lookups indexed by secret data, so this is
 * constant-time by construction, which matters when it runs under HMAC.
 */
internal sealed class Sha2Digest(
    val blockSize: Int,
    val digestSize: Int,
    private val lengthSize: Int,
) {
    private val buffer = ByteArray(blockSize)
    private var bufferLength = 0
    private var byteCount = 0L

    protected abstract fun compress(
        block: ByteArray,
        offset: Int,
    )

    protected abstract fun resetState()

    protected abstract fun writeState(
        out: ByteArray,
        offset: Int,
    )

    fun update(byte: Byte) {
        buffer[bufferLength++] = byte
        byteCount++
        if (bufferLength == blockSize) {
            compress(buffer, 0)
            bufferLength = 0
        }
    }

    fun update(
        input: ByteArray,
        offset: Int = 0,
        length: Int = input.size - offset,
    ) {
        require(offset >= 0 && length >= 0 && length <= input.size - offset) {
            "range [$offset, $offset + $length) is outside an array of ${input.size}"
        }
        byteCount += length
        var pos = offset
        var remaining = length

        if (bufferLength > 0) {
            val take = minOf(remaining, blockSize - bufferLength)
            input.copyInto(buffer, bufferLength, pos, pos + take)
            bufferLength += take
            pos += take
            remaining -= take
            if (bufferLength < blockSize) return
            compress(buffer, 0)
            bufferLength = 0
        }

        while (remaining >= blockSize) {
            compress(input, pos)
            pos += blockSize
            remaining -= blockSize
        }

        if (remaining > 0) {
            input.copyInto(buffer, 0, pos, pos + remaining)
            bufferLength = remaining
        }
    }

    /** Writes the digest into [out] at [offset] and resets, ready for a new message. */
    fun digestInto(
        out: ByteArray,
        offset: Int = 0,
    ) {
        require(offset >= 0 && digestSize <= out.size - offset) { "no room for a $digestSize-byte digest" }
        val bitsLow = byteCount shl 3
        val bitsHigh = byteCount ushr 61

        buffer[bufferLength++] = 0x80.toByte()
        if (bufferLength > blockSize - lengthSize) {
            buffer.fill(0, bufferLength, blockSize)
            compress(buffer, 0)
            bufferLength = 0
        }
        buffer.fill(0, bufferLength, blockSize)
        for (i in 0 until 8) {
            buffer[blockSize - 1 - i] = (bitsLow ushr (8 * i)).toByte()
        }
        if (lengthSize == 16) {
            for (i in 0 until 8) {
                buffer[blockSize - 9 - i] = (bitsHigh ushr (8 * i)).toByte()
            }
        }
        compress(buffer, 0)
        writeState(out, offset)
        reset()
    }

    fun digest(): ByteArray = ByteArray(digestSize).also { digestInto(it) }

    fun reset() {
        resetState()
        buffer.fill(0)
        bufferLength = 0
        byteCount = 0
    }
}

internal class Sha256Digest : Sha2Digest(blockSize = 64, digestSize = 32, lengthSize = 8) {
    private val state = Sha256Compression.IV.copyOf()
    private val w = IntArray(64)

    override fun compress(
        block: ByteArray,
        offset: Int,
    ) = Sha256Compression.compress(state, block, offset, w)

    override fun resetState() {
        Sha256Compression.IV.copyInto(state)
        w.fill(0)
    }

    override fun writeState(
        out: ByteArray,
        offset: Int,
    ) {
        for (i in 0 until 8) {
            val word = state[i]
            val o = offset + 4 * i
            out[o] = (word ushr 24).toByte()
            out[o + 1] = (word ushr 16).toByte()
            out[o + 2] = (word ushr 8).toByte()
            out[o + 3] = word.toByte()
        }
    }
}

internal class Sha512Digest : Sha2Digest(blockSize = 128, digestSize = 64, lengthSize = 16) {
    private val state = IV.copyOf()
    private val w = LongArray(80)

    override fun compress(
        block: ByteArray,
        offset: Int,
    ) {
        for (t in 0 until 16) {
            var word = 0L
            val i = offset + 8 * t
            for (j in 0 until 8) {
                word = (word shl 8) or (block[i + j].toLong() and 0xff)
            }
            w[t] = word
        }
        for (t in 16 until 80) {
            val w15 = w[t - 15]
            val w2 = w[t - 2]
            val s0 = w15.rotateRight(1) xor w15.rotateRight(8) xor (w15 ushr 7)
            val s1 = w2.rotateRight(19) xor w2.rotateRight(61) xor (w2 ushr 6)
            w[t] = w[t - 16] + s0 + w[t - 7] + s1
        }

        var a = state[0]
        var b = state[1]
        var c = state[2]
        var d = state[3]
        var e = state[4]
        var f = state[5]
        var g = state[6]
        var h = state[7]

        for (t in 0 until 80) {
            val bigSigma1 = e.rotateRight(14) xor e.rotateRight(18) xor e.rotateRight(41)
            val ch = g xor (e and (f xor g))
            val t1 = h + bigSigma1 + ch + K[t] + w[t]
            val bigSigma0 = a.rotateRight(28) xor a.rotateRight(34) xor a.rotateRight(39)
            val maj = (a and b) or (c and (a or b))
            val t2 = bigSigma0 + maj
            h = g
            g = f
            f = e
            e = d + t1
            d = c
            c = b
            b = a
            a = t1 + t2
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

    override fun resetState() {
        IV.copyInto(state)
        w.fill(0)
    }

    override fun writeState(
        out: ByteArray,
        offset: Int,
    ) {
        for (i in 0 until 8) {
            val word = state[i]
            val o = offset + 8 * i
            for (j in 0 until 8) {
                out[o + j] = (word ushr (56 - 8 * j)).toByte()
            }
        }
    }

    private companion object {
        // FIPS 180-4 §5.3.5 and §4.2.3: the first 64 bits of the fractional parts of
        // the square roots of the first 8 primes, and of the cube roots of the first 80.
        val IV =
            longArrayOf(
                0x6a09e667f3bcc908UL.toLong(),
                0xbb67ae8584caa73bUL.toLong(),
                0x3c6ef372fe94f82bUL.toLong(),
                0xa54ff53a5f1d36f1UL.toLong(),
                0x510e527fade682d1UL.toLong(),
                0x9b05688c2b3e6c1fUL.toLong(),
                0x1f83d9abfb41bd6bUL.toLong(),
                0x5be0cd19137e2179UL.toLong(),
            )

        val K =
            longArrayOf(
                0x428a2f98d728ae22UL.toLong(),
                0x7137449123ef65cdUL.toLong(),
                0xb5c0fbcfec4d3b2fUL.toLong(),
                0xe9b5dba58189dbbcUL.toLong(),
                0x3956c25bf348b538UL.toLong(),
                0x59f111f1b605d019UL.toLong(),
                0x923f82a4af194f9bUL.toLong(),
                0xab1c5ed5da6d8118UL.toLong(),
                0xd807aa98a3030242UL.toLong(),
                0x12835b0145706fbeUL.toLong(),
                0x243185be4ee4b28cUL.toLong(),
                0x550c7dc3d5ffb4e2UL.toLong(),
                0x72be5d74f27b896fUL.toLong(),
                0x80deb1fe3b1696b1UL.toLong(),
                0x9bdc06a725c71235UL.toLong(),
                0xc19bf174cf692694UL.toLong(),
                0xe49b69c19ef14ad2UL.toLong(),
                0xefbe4786384f25e3UL.toLong(),
                0x0fc19dc68b8cd5b5UL.toLong(),
                0x240ca1cc77ac9c65UL.toLong(),
                0x2de92c6f592b0275UL.toLong(),
                0x4a7484aa6ea6e483UL.toLong(),
                0x5cb0a9dcbd41fbd4UL.toLong(),
                0x76f988da831153b5UL.toLong(),
                0x983e5152ee66dfabUL.toLong(),
                0xa831c66d2db43210UL.toLong(),
                0xb00327c898fb213fUL.toLong(),
                0xbf597fc7beef0ee4UL.toLong(),
                0xc6e00bf33da88fc2UL.toLong(),
                0xd5a79147930aa725UL.toLong(),
                0x06ca6351e003826fUL.toLong(),
                0x142929670a0e6e70UL.toLong(),
                0x27b70a8546d22ffcUL.toLong(),
                0x2e1b21385c26c926UL.toLong(),
                0x4d2c6dfc5ac42aedUL.toLong(),
                0x53380d139d95b3dfUL.toLong(),
                0x650a73548baf63deUL.toLong(),
                0x766a0abb3c77b2a8UL.toLong(),
                0x81c2c92e47edaee6UL.toLong(),
                0x92722c851482353bUL.toLong(),
                0xa2bfe8a14cf10364UL.toLong(),
                0xa81a664bbc423001UL.toLong(),
                0xc24b8b70d0f89791UL.toLong(),
                0xc76c51a30654be30UL.toLong(),
                0xd192e819d6ef5218UL.toLong(),
                0xd69906245565a910UL.toLong(),
                0xf40e35855771202aUL.toLong(),
                0x106aa07032bbd1b8UL.toLong(),
                0x19a4c116b8d2d0c8UL.toLong(),
                0x1e376c085141ab53UL.toLong(),
                0x2748774cdf8eeb99UL.toLong(),
                0x34b0bcb5e19b48a8UL.toLong(),
                0x391c0cb3c5c95a63UL.toLong(),
                0x4ed8aa4ae3418acbUL.toLong(),
                0x5b9cca4f7763e373UL.toLong(),
                0x682e6ff3d6b2b8a3UL.toLong(),
                0x748f82ee5defb2fcUL.toLong(),
                0x78a5636f43172f60UL.toLong(),
                0x84c87814a1f0ab72UL.toLong(),
                0x8cc702081a6439ecUL.toLong(),
                0x90befffa23631e28UL.toLong(),
                0xa4506cebde82bde9UL.toLong(),
                0xbef9a3f7b2c67915UL.toLong(),
                0xc67178f2e372532bUL.toLong(),
                0xca273eceea26619cUL.toLong(),
                0xd186b8c721c0c207UL.toLong(),
                0xeada7dd6cde0eb1eUL.toLong(),
                0xf57d4f7fee6ed178UL.toLong(),
                0x06f067aa72176fbaUL.toLong(),
                0x0a637dc5a2c898a6UL.toLong(),
                0x113f9804bef90daeUL.toLong(),
                0x1b710b35131c471bUL.toLong(),
                0x28db77f523047d84UL.toLong(),
                0x32caab7b40c72493UL.toLong(),
                0x3c9ebe0a15c9bebcUL.toLong(),
                0x431d67c49c100d4cUL.toLong(),
                0x4cc5d4becb3e42b6UL.toLong(),
                0x597f299cfc657e2aUL.toLong(),
                0x5fcb6fab3ad6faecUL.toLong(),
                0x6c44198c4a475817UL.toLong(),
            )
    }
}
