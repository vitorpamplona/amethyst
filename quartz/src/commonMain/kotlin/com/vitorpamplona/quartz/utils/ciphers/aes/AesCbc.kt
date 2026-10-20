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
 * AES-CBC with PKCS#7 padding (NIST SP 800-38A §6.2), matching JCA's "AES/CBC/PKCS5Padding".
 * Holds the key schedule, so one instance serves many messages; it is thread-safe.
 */
internal class AesCbcKey(
    key: ByteArray,
) {
    private val aes = AesBitsliced(key)

    fun encrypt(
        iv: ByteArray,
        plaintext: ByteArray,
    ): ByteArray {
        require(iv.size == 16) { "AES-CBC IV must be 16 bytes, was ${iv.size}" }
        val scratch = AesBitsliced.Scratch()
        val pad = 16 - plaintext.size % 16
        val out = plaintext.copyOf(plaintext.size + pad)
        out.fill(pad.toByte(), plaintext.size, out.size)

        var prev = iv
        var prevOffset = 0
        for (o in out.indices step 16) {
            for (i in 0 until 16) {
                out[o + i] = (out[o + i].toInt() xor prev[prevOffset + i].toInt()).toByte()
            }
            aes.encryptBlocks(out, o, out, o, 1, scratch)
            prev = out
            prevOffset = o
        }
        return out
    }

    /** Throws [IllegalArgumentException] on a length that is not whole blocks or on bad padding. */
    fun decrypt(
        iv: ByteArray,
        ciphertext: ByteArray,
    ): ByteArray {
        require(iv.size == 16) { "AES-CBC IV must be 16 bytes, was ${iv.size}" }
        require(ciphertext.isNotEmpty() && ciphertext.size % 16 == 0) {
            "AES-CBC ciphertext must be a non-empty multiple of 16 bytes, was ${ciphertext.size}"
        }
        val scratch = AesBitsliced.Scratch()
        val out = ByteArray(ciphertext.size)
        var o = 0
        while (o < ciphertext.size) {
            val blocks = minOf(4, (ciphertext.size - o) / 16)
            aes.decryptBlocks(ciphertext, o, out, o, blocks, scratch)
            o += 16 * blocks
        }
        for (i in 0 until 16) {
            out[i] = (out[i].toInt() xor iv[i].toInt()).toByte()
        }
        for (i in 16 until out.size) {
            out[i] = (out[i].toInt() xor ciphertext[i - 16].toInt()).toByte()
        }

        // Check every byte of the last block, with no early exit.
        val pad = out[out.size - 1].toInt() and 0xFF
        var bad = ((pad - 1) ushr 31) or ((16 - pad) ushr 31)
        for (i in 1..16) {
            val inPad = (i - pad - 1) ushr 31
            val differs = ((out[out.size - i].toInt() and 0xFF) xor pad).let { (it or -it) ushr 31 }
            bad = bad or (inPad and differs)
        }
        if (bad != 0) {
            out.fill(0)
            throw IllegalArgumentException("AES-CBC padding is invalid")
        }
        return out.copyOf(out.size - pad)
    }
}

/** One-shot AES-CBC, for callers that use a key once. */
internal object AesCbc {
    fun encrypt(
        key: ByteArray,
        iv: ByteArray,
        plaintext: ByteArray,
    ): ByteArray = AesCbcKey(key).encrypt(iv, plaintext)

    /** Throws [IllegalArgumentException] on a length that is not whole blocks or on bad padding. */
    fun decrypt(
        key: ByteArray,
        iv: ByteArray,
        ciphertext: ByteArray,
    ): ByteArray = AesCbcKey(key).decrypt(iv, ciphertext)
}
