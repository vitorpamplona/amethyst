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

import kotlin.concurrent.Volatile

/**
 * AES-GCM (NIST SP 800-38D) with a 128-bit tag, on [AesBitsliced] and [Ghash]. Output
 * is ciphertext followed by the tag, the same layout JCA's "AES/GCM/NoPadding" uses.
 * Any nonce length works; a 12-byte one takes the spec's fast path for J0.
 *
 * Holds the key schedule and the hash key H, so reusing one instance for many
 * messages skips both. It is thread-safe: each call keeps its own scratch.
 *
 * A bitsliced AES call costs the same for one block as for four, so E(J0) for the
 * tag, and H the first time it is needed, ride in the first counter batch instead of
 * taking calls of their own.
 */
internal class AesGcmKey(
    key: ByteArray,
) {
    private val aes = AesBitsliced(key)

    // Computed on first use. Racing threads compute the same bytes, so the race is benign.
    @Volatile
    private var hashKey: ByteArray? = null

    fun encrypt(
        nonce: ByteArray,
        plaintext: ByteArray,
        aad: ByteArray,
    ): ByteArray {
        val call = Call(nonce)
        val out = ByteArray(plaintext.size + TAG_LENGTH)
        call.ctr(plaintext, plaintext.size, out)
        call.tag(aad, out, plaintext.size).copyInto(out, plaintext.size)
        return out
    }

    /** Throws [IllegalArgumentException] when the tag does not verify; no plaintext is returned then. */
    fun decrypt(
        nonce: ByteArray,
        input: ByteArray,
        aad: ByteArray,
    ): ByteArray {
        require(input.size >= TAG_LENGTH) { "AES-GCM input is shorter than its tag" }
        val call = Call(nonce)
        val ciphertextLength = input.size - TAG_LENGTH
        val out = ByteArray(ciphertextLength)
        call.ctr(input, ciphertextLength, out)
        val expected = call.tag(aad, input, ciphertextLength)
        var diff = 0
        for (i in 0 until TAG_LENGTH) {
            diff = diff or (expected[i].toInt() xor input[ciphertextLength + i].toInt())
        }
        if (diff != 0) {
            out.fill(0)
            throw IllegalArgumentException("AES-GCM authentication failed")
        }
        return out
    }

    /** One encryption or decryption: its J0, the E(J0) the counter pass produces, and scratch. */
    private inner class Call(
        nonce: ByteArray,
    ) {
        val scratch = AesBitsliced.Scratch()
        val j0: ByteArray
        val encryptedJ0 = ByteArray(16)

        init {
            require(nonce.isNotEmpty()) { "AES-GCM nonce must not be empty" }
            j0 =
                if (nonce.size == 12) {
                    nonce.copyOf(16).also { it[15] = 1 }
                } else {
                    // J0 is a GHASH of the nonce, so H is needed before the counter pass.
                    val ghash = Ghash(h())
                    ghash.update(nonce)
                    ghash.updateLengths(0, nonce.size.toLong() * 8)
                    ByteArray(16).also { ghash.digestInto(it) }
                }
        }

        fun h(): ByteArray =
            hashKey ?: ByteArray(16).also {
                aes.encryptBlocks(it, 0, it, 0, 1, scratch)
                hashKey = it
            }

        /**
         * XORs [input] with the keystream from inc32(J0) on, four blocks per AES call.
         * The first call also encrypts J0, and the zero block for H when H is not known yet.
         */
        fun ctr(
            input: ByteArray,
            length: Int,
            out: ByteArray,
        ) {
            val blocks = ByteArray(64)
            val keystream = ByteArray(64)
            var counter =
                ((j0[12].toInt() and 0xFF) shl 24) or ((j0[13].toInt() and 0xFF) shl 16) or
                    ((j0[14].toInt() and 0xFF) shl 8) or (j0[15].toInt() and 0xFF)

            var pos = 0
            var first = true
            while (first || pos < length) {
                var slot = 0
                var hSlot = -1
                var j0Slot = -1
                if (first) {
                    if (hashKey == null) {
                        blocks.fill(0, 0, 16)
                        hSlot = slot++
                    }
                    j0.copyInto(blocks, 16 * slot)
                    j0Slot = slot++
                }
                val dataSlot = slot
                while (slot < 4 && pos + 16 * (slot - dataSlot) < length) {
                    counter++
                    val o = 16 * slot
                    j0.copyInto(blocks, o, 0, 12)
                    blocks[o + 12] = (counter ushr 24).toByte()
                    blocks[o + 13] = (counter ushr 16).toByte()
                    blocks[o + 14] = (counter ushr 8).toByte()
                    blocks[o + 15] = counter.toByte()
                    slot++
                }
                aes.encryptBlocks(blocks, 0, keystream, 0, slot, scratch)
                if (hSlot >= 0) hashKey = keystream.copyOfRange(16 * hSlot, 16 * hSlot + 16)
                if (j0Slot >= 0) keystream.copyInto(encryptedJ0, 0, 16 * j0Slot, 16 * j0Slot + 16)

                val chunk = minOf(16 * (slot - dataSlot), length - pos)
                val k = 16 * dataSlot
                for (i in 0 until chunk) {
                    out[pos + i] = (input[pos + i].toInt() xor keystream[k + i].toInt()).toByte()
                }
                pos += chunk
                first = false
            }
            keystream.fill(0)
        }

        /** GHASH over the AAD and ciphertext, masked with E(J0). Call after [ctr]. */
        fun tag(
            aad: ByteArray,
            ciphertext: ByteArray,
            ciphertextLength: Int,
        ): ByteArray {
            val ghash = Ghash(h())
            ghash.update(aad)
            ghash.update(ciphertext, 0, ciphertextLength)
            ghash.updateLengths(aad.size.toLong() * 8, ciphertextLength.toLong() * 8)
            val s = ByteArray(16).also { ghash.digestInto(it) }
            for (i in 0 until 16) {
                s[i] = (s[i].toInt() xor encryptedJ0[i].toInt()).toByte()
            }
            return s
        }
    }

    companion object {
        const val TAG_LENGTH = 16
    }
}

/** One-shot AES-GCM, for callers that use a key once. */
internal object AesGcm {
    const val TAG_LENGTH = AesGcmKey.TAG_LENGTH

    fun encrypt(
        key: ByteArray,
        nonce: ByteArray,
        plaintext: ByteArray,
        aad: ByteArray,
    ): ByteArray = AesGcmKey(key).encrypt(nonce, plaintext, aad)

    /** Throws [IllegalArgumentException] when the tag does not verify. */
    fun decrypt(
        key: ByteArray,
        nonce: ByteArray,
        input: ByteArray,
        aad: ByteArray,
    ): ByteArray = AesGcmKey(key).decrypt(nonce, input, aad)
}
