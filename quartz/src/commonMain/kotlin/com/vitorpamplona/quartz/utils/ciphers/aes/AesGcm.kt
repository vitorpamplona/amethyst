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
package com.vitorpamplona.quartz.utils.ciphers.aes

/**
 * AES-GCM (NIST SP 800-38D) with a 128-bit tag, on [AesBitsliced] and [Ghash]. Output
 * is ciphertext followed by the tag, the same layout JCA's "AES/GCM/NoPadding" uses.
 * Any nonce length works; a 12-byte one takes the spec's fast path for J0.
 */
internal object AesGcm {
    const val TAG_LENGTH = 16

    fun encrypt(
        key: ByteArray,
        nonce: ByteArray,
        plaintext: ByteArray,
        aad: ByteArray,
    ): ByteArray {
        val aes = AesBitsliced(key)
        val h = hashKey(aes)
        val j0 = j0(h, nonce)
        val out = ByteArray(plaintext.size + TAG_LENGTH)
        ctr(aes, j0, plaintext, plaintext.size, out)
        tag(aes, h, j0, aad, out, plaintext.size).copyInto(out, plaintext.size)
        return out
    }

    /** Throws [IllegalArgumentException] when the tag does not verify; nothing is decrypted then. */
    fun decrypt(
        key: ByteArray,
        nonce: ByteArray,
        input: ByteArray,
        aad: ByteArray,
    ): ByteArray {
        require(input.size >= TAG_LENGTH) { "AES-GCM input is shorter than its tag" }
        val aes = AesBitsliced(key)
        val h = hashKey(aes)
        val j0 = j0(h, nonce)
        val ciphertextLength = input.size - TAG_LENGTH
        val expected = tag(aes, h, j0, aad, input, ciphertextLength)
        var diff = 0
        for (i in 0 until TAG_LENGTH) {
            diff = diff or (expected[i].toInt() xor input[ciphertextLength + i].toInt())
        }
        require(diff == 0) { "AES-GCM authentication failed" }
        val out = ByteArray(ciphertextLength)
        ctr(aes, j0, input, ciphertextLength, out)
        return out
    }

    private fun hashKey(aes: AesBitsliced): ByteArray = ByteArray(16).also { aes.encryptBlocks(it, 0, it, 0, 1) }

    private fun j0(
        h: ByteArray,
        nonce: ByteArray,
    ): ByteArray {
        require(nonce.isNotEmpty()) { "AES-GCM nonce must not be empty" }
        if (nonce.size == 12) {
            return nonce.copyOf(16).also { it[15] = 1 }
        }
        val ghash = Ghash(h)
        ghash.update(nonce)
        ghash.updateLengths(0, nonce.size.toLong() * 8)
        return ByteArray(16).also { ghash.digestInto(it) }
    }

    /** XORs [input] with the keystream from inc32(J0) on, four blocks per AES call. */
    private fun ctr(
        aes: AesBitsliced,
        j0: ByteArray,
        input: ByteArray,
        length: Int,
        out: ByteArray,
    ) {
        val counters = ByteArray(64)
        val keystream = ByteArray(64)
        for (b in 0 until 4) j0.copyInto(counters, 16 * b, 0, 12)
        var counter =
            ((j0[12].toInt() and 0xFF) shl 24) or ((j0[13].toInt() and 0xFF) shl 16) or
                ((j0[14].toInt() and 0xFF) shl 8) or (j0[15].toInt() and 0xFF)

        var pos = 0
        while (pos < length) {
            val chunk = minOf(64, length - pos)
            val blocks = (chunk + 15) / 16
            for (b in 0 until blocks) {
                counter++
                val o = 16 * b + 12
                counters[o] = (counter ushr 24).toByte()
                counters[o + 1] = (counter ushr 16).toByte()
                counters[o + 2] = (counter ushr 8).toByte()
                counters[o + 3] = counter.toByte()
            }
            aes.encryptBlocks(counters, 0, keystream, 0, blocks)
            for (i in 0 until chunk) {
                out[pos + i] = (input[pos + i].toInt() xor keystream[i].toInt()).toByte()
            }
            pos += chunk
        }
        keystream.fill(0)
    }

    private fun tag(
        aes: AesBitsliced,
        h: ByteArray,
        j0: ByteArray,
        aad: ByteArray,
        ciphertext: ByteArray,
        ciphertextLength: Int,
    ): ByteArray {
        val ghash = Ghash(h)
        ghash.update(aad)
        ghash.update(ciphertext, 0, ciphertextLength)
        ghash.updateLengths(aad.size.toLong() * 8, ciphertextLength.toLong() * 8)
        val s = ByteArray(16).also { ghash.digestInto(it) }
        val ekj0 = j0.copyOf()
        aes.encryptBlocks(ekj0, 0, ekj0, 0, 1)
        for (i in 0 until 16) {
            s[i] = (s[i].toInt() xor ekj0[i].toInt()).toByte()
        }
        return s
    }
}
