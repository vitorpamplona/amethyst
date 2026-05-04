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
package com.vitorpamplona.quic.crypto

/**
 * QUIC header-protection sample mask generator (RFC 9001 §5.4).
 *
 * - AES suites: take a 16-byte sample, AES-ECB encrypt with the HP key, use
 *   the first 5 bytes as the mask.
 * - ChaCha20 suite: the first 4 bytes of the sample are the counter, next 12
 *   are the nonce; ChaCha20-encrypt 5 zero bytes; that's the mask.
 */
sealed class HeaderProtection {
    abstract fun mask(
        hpKey: ByteArray,
        sample: ByteArray,
    ): ByteArray
}

/** AES-128-ECB header protection. Implemented via the platform AES helper. */
class AesEcbHeaderProtection(
    private val aesEncryptOneBlock: AesOneBlockEncrypt,
) : HeaderProtection() {
    override fun mask(
        hpKey: ByteArray,
        sample: ByteArray,
    ): ByteArray {
        require(sample.size == 16) { "AES sample must be 16 bytes" }
        require(hpKey.size in setOf(16, 24, 32)) { "AES-ECB key must be 16/24/32 bytes" }
        val out = aesEncryptOneBlock.encrypt(hpKey, sample)
        return out.copyOfRange(0, 5)
    }
}

/** ChaCha20-based header protection per RFC 9001 §5.4.4. */
class ChaCha20HeaderProtection(
    private val chacha20Encrypt: ChaCha20BlockEncrypt,
) : HeaderProtection() {
    override fun mask(
        hpKey: ByteArray,
        sample: ByteArray,
    ): ByteArray {
        require(sample.size == 16) { "ChaCha20 HP sample must be 16 bytes" }
        require(hpKey.size == 32) { "ChaCha20 HP key must be 32 bytes" }
        val counter =
            ((sample[0].toInt() and 0xFF)) or
                ((sample[1].toInt() and 0xFF) shl 8) or
                ((sample[2].toInt() and 0xFF) shl 16) or
                ((sample[3].toInt() and 0xFF) shl 24)
        val nonce = sample.copyOfRange(4, 16)
        return chacha20Encrypt.encrypt(hpKey, nonce, counter, ByteArray(5))
    }
}

/** SPI for one-block AES encryption (provided by jvmAndroid via JCA). */
fun interface AesOneBlockEncrypt {
    fun encrypt(
        key: ByteArray,
        block: ByteArray,
    ): ByteArray
}

/** SPI for ChaCha20 keystream encryption with explicit counter. */
fun interface ChaCha20BlockEncrypt {
    fun encrypt(
        key: ByteArray,
        nonce: ByteArray,
        counter: Int,
        plaintext: ByteArray,
    ): ByteArray
}

/**
 * Apply header protection to a packet header in-place.
 *
 * Per RFC 9001 §5.4.1:
 *   - first byte: low bits XORed with `mask[0] & 0x0F` (short header) or
 *     `mask[0] & 0x1F` (long header). The header form is detected from the
 *     high bit of the first byte: 1 = long header (uses 0x0F low-bit mask
 *     because the upper four bits include version-related flags... wait,
 *     RFC says the opposite — see notes).
 *
 * Actually RFC 9001 §5.4.1 is precise:
 *   - long header: mask first byte with 0x0F (4 protected bits)
 *   - short header: mask first byte with 0x1F (5 protected bits)
 * The packet number bytes (1..4 of them) are XORed with `mask[1..pnLen]`.
 */
fun applyHeaderProtectionMask(
    packet: ByteArray,
    firstByteOffset: Int,
    pnOffset: Int,
    pnLen: Int,
    mask: ByteArray,
) {
    require(pnLen in 1..4) { "pnLen must be 1..4 (was $pnLen)" }
    require(mask.size >= 5) { "HP mask must be at least 5 bytes" }
    val firstByte = packet[firstByteOffset].toInt() and 0xFF
    val isLong = (firstByte and 0x80) != 0
    val firstByteMask = if (isLong) 0x0F else 0x1F
    packet[firstByteOffset] = (firstByte xor (mask[0].toInt() and firstByteMask)).toByte()
    for (i in 0 until pnLen) {
        packet[pnOffset + i] = (packet[pnOffset + i].toInt() xor mask[1 + i].toInt()).toByte()
    }
}
