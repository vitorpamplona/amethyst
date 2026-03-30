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
package com.vitorpamplona.quartz.nip44Encryption.crypto

/**
 * Pure Kotlin implementation of ChaCha20, HChaCha20, and XChaCha20.
 *
 * All functions are stateless and thread-safe — they use only local variables
 * and produce no side effects beyond their return values.
 *
 * References:
 * - RFC 8439: ChaCha20 and Poly1305
 * - draft-irtf-cfrg-xchacha: XChaCha20 (HChaCha20 + ChaCha20)
 */
object ChaCha20Core {
    // "expand 32-byte k" as little-endian integers
    private const val SIGMA0 = 0x61707865
    private const val SIGMA1 = 0x3320646e
    private const val SIGMA2 = 0x79622d32
    private const val SIGMA3 = 0x6b206574

    /**
     * ChaCha20 quarter round (RFC 8439 §2.1).
     * Operates in-place on the 16-word state.
     */
    private fun quarterRound(
        state: IntArray,
        a: Int,
        b: Int,
        c: Int,
        d: Int,
    ) {
        state[a] += state[b]
        state[d] = (state[d] xor state[a]).rotateLeft(16)
        state[c] += state[d]
        state[b] = (state[b] xor state[c]).rotateLeft(12)
        state[a] += state[b]
        state[d] = (state[d] xor state[a]).rotateLeft(8)
        state[c] += state[d]
        state[b] = (state[b] xor state[c]).rotateLeft(7)
    }

    /**
     * Perform 20 rounds (10 double-rounds) of ChaCha on the state.
     */
    private fun chaCha20Rounds(state: IntArray) {
        repeat(10) {
            // Column rounds
            quarterRound(state, 0, 4, 8, 12)
            quarterRound(state, 1, 5, 9, 13)
            quarterRound(state, 2, 6, 10, 14)
            quarterRound(state, 3, 7, 11, 15)
            // Diagonal rounds
            quarterRound(state, 0, 5, 10, 15)
            quarterRound(state, 1, 6, 11, 12)
            quarterRound(state, 2, 7, 8, 13)
            quarterRound(state, 3, 4, 9, 14)
        }
    }

    /**
     * Initialize the ChaCha20 state matrix from key, counter, and nonce.
     * Layout (RFC 8439 §2.3):
     *   cccccccc  cccccccc  cccccccc  cccccccc
     *   kkkkkkkk  kkkkkkkk  kkkkkkkk  kkkkkkkk
     *   kkkkkkkk  kkkkkkkk  kkkkkkkk  kkkkkkkk
     *   bbbbbbbb  nnnnnnnn  nnnnnnnn  nnnnnnnn
     */
    private fun initState(
        key: ByteArray,
        counter: Int,
        nonce: ByteArray,
    ): IntArray {
        val state = IntArray(16)
        state[0] = SIGMA0
        state[1] = SIGMA1
        state[2] = SIGMA2
        state[3] = SIGMA3
        state[4] = key.littleEndianToInt(0)
        state[5] = key.littleEndianToInt(4)
        state[6] = key.littleEndianToInt(8)
        state[7] = key.littleEndianToInt(12)
        state[8] = key.littleEndianToInt(16)
        state[9] = key.littleEndianToInt(20)
        state[10] = key.littleEndianToInt(24)
        state[11] = key.littleEndianToInt(28)
        state[12] = counter
        state[13] = nonce.littleEndianToInt(0)
        state[14] = nonce.littleEndianToInt(4)
        state[15] = nonce.littleEndianToInt(8)
        return state
    }

    /**
     * ChaCha20 block function (RFC 8439 §2.3).
     * Produces a 64-byte keystream block.
     *
     * @param key 32-byte key
     * @param counter block counter
     * @param nonce 12-byte nonce (IETF variant)
     * @return 64-byte keystream block
     */
    fun chaCha20Block(
        key: ByteArray,
        counter: Int,
        nonce: ByteArray,
    ): ByteArray {
        val initial = initState(key, counter, nonce)
        val working = initial.copyOf()
        chaCha20Rounds(working)

        // Add initial state to working state and serialize
        val output = ByteArray(64)
        for (i in 0..15) {
            (working[i] + initial[i]).intToLittleEndian(output, i * 4)
        }
        return output
    }

    /**
     * ChaCha20 IETF stream cipher XOR (RFC 8439 §2.4).
     * XORs the message with the ChaCha20 keystream.
     *
     * Optimized to parse key/nonce once and reuse state across blocks.
     * Full 64-byte blocks use word-level XOR (4 bytes at a time).
     *
     * @param message plaintext or ciphertext
     * @param key 32-byte key
     * @param nonce 12-byte nonce
     * @param counter initial block counter (0 for stream cipher, 1 for AEAD)
     * @return XOR'd output (same length as message)
     */
    fun chaCha20Xor(
        message: ByteArray,
        key: ByteArray,
        nonce: ByteArray,
        counter: Int = 0,
    ): ByteArray {
        val output = ByteArray(message.size)
        if (message.isEmpty()) return output

        val fullBlocks = message.size / 64
        val remainder = message.size % 64

        // Parse key and nonce once into the initial state template
        val initial = initState(key, counter, nonce)
        val working = IntArray(16)

        for (i in 0 until fullBlocks) {
            initial[12] = counter + i
            initial.copyInto(working)
            chaCha20Rounds(working)

            // XOR at word level: read 4 message bytes as Int, XOR with keystream word, write 4 bytes
            val off = i * 64
            for (j in 0..15) {
                val ks = working[j] + initial[j]
                val mw = message.littleEndianToInt(off + j * 4)
                (ks xor mw).intToLittleEndian(output, off + j * 4)
            }
        }

        if (remainder > 0) {
            initial[12] = counter + fullBlocks
            initial.copyInto(working)
            chaCha20Rounds(working)

            val off = fullBlocks * 64
            // Process full words within the remainder
            val fullWords = remainder / 4
            for (j in 0 until fullWords) {
                val ks = working[j] + initial[j]
                val mw = message.littleEndianToInt(off + j * 4)
                (ks xor mw).intToLittleEndian(output, off + j * 4)
            }
            // Process remaining bytes (0-3 bytes)
            val tailStart = fullWords * 4
            if (tailStart < remainder) {
                // Serialize just this one keystream word
                val ks = working[fullWords] + initial[fullWords]
                val ksByte0 = (ks and 0xFF)
                val ksByte1 = (ks ushr 8 and 0xFF)
                val ksByte2 = (ks ushr 16 and 0xFF)
                val ksByte3 = (ks ushr 24 and 0xFF)
                val ksBytes = intArrayOf(ksByte0, ksByte1, ksByte2, ksByte3)
                for (b in tailStart until remainder) {
                    output[off + b] = (message[off + b].toInt() xor ksBytes[b - tailStart]).toByte()
                }
            }
        }

        return output
    }

    /**
     * HChaCha20 (draft-irtf-cfrg-xchacha §2.2).
     * Derives a 32-byte subkey from a 32-byte key and 16-byte input.
     * Used as the first step of XChaCha20.
     *
     * @param key 32-byte key
     * @param input 16-byte input (first 16 bytes of the 24-byte XChaCha20 nonce)
     * @return 32-byte subkey
     */
    fun hChaCha20(
        key: ByteArray,
        input: ByteArray,
    ): ByteArray {
        val state = IntArray(16)
        state[0] = SIGMA0
        state[1] = SIGMA1
        state[2] = SIGMA2
        state[3] = SIGMA3
        state[4] = key.littleEndianToInt(0)
        state[5] = key.littleEndianToInt(4)
        state[6] = key.littleEndianToInt(8)
        state[7] = key.littleEndianToInt(12)
        state[8] = key.littleEndianToInt(16)
        state[9] = key.littleEndianToInt(20)
        state[10] = key.littleEndianToInt(24)
        state[11] = key.littleEndianToInt(28)
        state[12] = input.littleEndianToInt(0)
        state[13] = input.littleEndianToInt(4)
        state[14] = input.littleEndianToInt(8)
        state[15] = input.littleEndianToInt(12)

        chaCha20Rounds(state)

        // Output words 0-3 and 12-15 (skip the key material in 4-11)
        val output = ByteArray(32)
        state[0].intToLittleEndian(output, 0)
        state[1].intToLittleEndian(output, 4)
        state[2].intToLittleEndian(output, 8)
        state[3].intToLittleEndian(output, 12)
        state[12].intToLittleEndian(output, 16)
        state[13].intToLittleEndian(output, 20)
        state[14].intToLittleEndian(output, 24)
        state[15].intToLittleEndian(output, 28)
        return output
    }

    /**
     * HChaCha20 variant that avoids allocating a 16-byte input copy.
     * Reads the first 16 bytes of the nonce directly.
     *
     * @param key 32-byte key
     * @param nonce 24-byte nonce (only first 16 bytes are used)
     */
    internal fun hChaCha20FromNonce24(
        key: ByteArray,
        nonce: ByteArray,
    ): ByteArray {
        val state = IntArray(16)
        state[0] = SIGMA0
        state[1] = SIGMA1
        state[2] = SIGMA2
        state[3] = SIGMA3
        state[4] = key.littleEndianToInt(0)
        state[5] = key.littleEndianToInt(4)
        state[6] = key.littleEndianToInt(8)
        state[7] = key.littleEndianToInt(12)
        state[8] = key.littleEndianToInt(16)
        state[9] = key.littleEndianToInt(20)
        state[10] = key.littleEndianToInt(24)
        state[11] = key.littleEndianToInt(28)
        state[12] = nonce.littleEndianToInt(0)
        state[13] = nonce.littleEndianToInt(4)
        state[14] = nonce.littleEndianToInt(8)
        state[15] = nonce.littleEndianToInt(12)

        chaCha20Rounds(state)

        val output = ByteArray(32)
        state[0].intToLittleEndian(output, 0)
        state[1].intToLittleEndian(output, 4)
        state[2].intToLittleEndian(output, 8)
        state[3].intToLittleEndian(output, 12)
        state[12].intToLittleEndian(output, 16)
        state[13].intToLittleEndian(output, 20)
        state[14].intToLittleEndian(output, 24)
        state[15].intToLittleEndian(output, 28)
        return output
    }

    /**
     * Generates the first 32 bytes of keystream block 0 (used as Poly1305 one-time key).
     * Avoids allocating a full 64-byte block.
     */
    internal fun chaCha20PolyKey(
        key: ByteArray,
        nonce: ByteArray,
    ): ByteArray {
        val initial = initState(key, 0, nonce)
        val working = initial.copyOf()
        chaCha20Rounds(working)

        val polyKey = ByteArray(32)
        for (i in 0..7) {
            (working[i] + initial[i]).intToLittleEndian(polyKey, i * 4)
        }
        return polyKey
    }

    /**
     * XChaCha20 stream cipher XOR (draft-irtf-cfrg-xchacha §2.3).
     * Uses HChaCha20 to derive a subkey, then applies ChaCha20 with the remaining nonce bytes.
     *
     * @param message plaintext or ciphertext
     * @param nonce 24-byte nonce
     * @param key 32-byte key
     * @return XOR'd output (same length as message)
     */
    fun xChaCha20Xor(
        message: ByteArray,
        nonce: ByteArray,
        key: ByteArray,
    ): ByteArray {
        // Step 1: Derive subkey using first 16 bytes of nonce (no copyOfRange)
        val subKey = hChaCha20FromNonce24(key, nonce)

        // Step 2: Build 12-byte subnonce: 4 zero bytes + last 8 bytes of nonce
        val subNonce = ByteArray(12)
        nonce.copyInto(subNonce, destinationOffset = 4, startIndex = 16, endIndex = 24)

        // Step 3: Encrypt with ChaCha20 using subkey, counter=0
        return chaCha20Xor(message, subKey, subNonce, counter = 0)
    }
}

// --- Little-endian conversion helpers ---

internal fun ByteArray.littleEndianToInt(offset: Int): Int =
    (this[offset].toInt() and 0xFF) or
        ((this[offset + 1].toInt() and 0xFF) shl 8) or
        ((this[offset + 2].toInt() and 0xFF) shl 16) or
        ((this[offset + 3].toInt() and 0xFF) shl 24)

internal fun Int.intToLittleEndian(
    output: ByteArray,
    offset: Int,
) {
    output[offset] = (this and 0xFF).toByte()
    output[offset + 1] = (this ushr 8 and 0xFF).toByte()
    output[offset + 2] = (this ushr 16 and 0xFF).toByte()
    output[offset + 3] = (this ushr 24 and 0xFF).toByte()
}
