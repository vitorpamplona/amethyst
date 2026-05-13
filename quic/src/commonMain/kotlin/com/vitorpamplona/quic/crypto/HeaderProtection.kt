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
    /**
     * Compute the HP mask from a 16-byte standalone sample buffer. Retained
     * for callers (mostly tests) that already have a heap-allocated sample
     * blob — the production hot path uses [maskInto] to avoid every per-
     * packet allocation.
     */
    abstract fun mask(
        hpKey: ByteArray,
        sample: ByteArray,
    ): ByteArray

    /**
     * Compute the HP mask from a 16-byte sample window inside [src] starting
     * at [srcOffset]. Avoids the `copyOfRange` of the sample on every
     * outbound and inbound packet (round-5 #P1) but still allocates a
     * fresh 5-byte mask + an internal 16-byte AES scratch on each call.
     * Use [maskInto] when caller-owned scratch + mask buffers are
     * available (the production writer/parser path).
     */
    abstract fun maskAt(
        hpKey: ByteArray,
        src: ByteArray,
        srcOffset: Int,
    ): ByteArray

    /**
     * Allocation-free HP mask: write the 5 mask bytes into [dstMask], using
     * [scratch16] (caller-owned 16-byte buffer) for the AES-ECB output
     * intermediate. Returns [dstMask] for fluent use.
     *
     * Both buffers are MUTATED — callers must consume the mask before the
     * next [maskInto] call on the same buffers. The hot QUIC writer/parser
     * path in `Short/LongHeaderPacket.build` + `parseAndDecrypt` keeps a
     * per-PacketProtection scratch + mask pair and consumes the mask
     * immediately during [com.vitorpamplona.quic.crypto.applyHeaderProtectionMask],
     * so the lifetime is trivially safe.
     */
    abstract fun maskInto(
        hpKey: ByteArray,
        src: ByteArray,
        srcOffset: Int,
        scratch16: ByteArray,
        dstMask: ByteArray,
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

    override fun maskAt(
        hpKey: ByteArray,
        src: ByteArray,
        srcOffset: Int,
    ): ByteArray = maskInto(hpKey, src, srcOffset, ByteArray(16), ByteArray(5))

    override fun maskInto(
        hpKey: ByteArray,
        src: ByteArray,
        srcOffset: Int,
        scratch16: ByteArray,
        dstMask: ByteArray,
    ): ByteArray {
        require(srcOffset >= 0 && srcOffset + 16 <= src.size) { "HP sample window out of range" }
        require(hpKey.size in setOf(16, 24, 32)) { "AES-ECB key must be 16/24/32 bytes" }
        require(scratch16.size == 16) { "AES scratch must be 16 bytes" }
        require(dstMask.size >= 5) { "HP mask buffer must be at least 5 bytes" }
        aesEncryptOneBlock.encryptInto(hpKey, src, srcOffset, scratch16, 0)
        // Mask is the first 5 bytes per RFC 9001 §5.4.3.
        scratch16.copyInto(dstMask, 0, 0, 5)
        return dstMask
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
        return maskAt(hpKey, sample, 0)
    }

    override fun maskAt(
        hpKey: ByteArray,
        src: ByteArray,
        srcOffset: Int,
    ): ByteArray = chacha20Mask(hpKey, src, srcOffset)

    override fun maskInto(
        hpKey: ByteArray,
        src: ByteArray,
        srcOffset: Int,
        scratch16: ByteArray,
        dstMask: ByteArray,
    ): ByteArray {
        require(dstMask.size >= 5) { "HP mask buffer must be at least 5 bytes" }
        // ChaCha20 HP unavoidably allocates a fresh 5-byte ciphertext via the
        // [ChaCha20BlockEncrypt] SPI, plus a 12-byte nonce slice (Quartz's
        // `ChaCha20Core.chaCha20Xor` takes a standalone nonce). Copy the
        // result into the caller's [dstMask] so call-site shape matches the
        // AES-ECB path; a future pass could push src+offset through the
        // ChaCha20 SPI to fully eliminate the slice. [scratch16] is unused
        // here.
        val mask = chacha20Mask(hpKey, src, srcOffset)
        mask.copyInto(dstMask, 0, 0, 5)
        return dstMask
    }

    private fun chacha20Mask(
        hpKey: ByteArray,
        src: ByteArray,
        srcOffset: Int,
    ): ByteArray {
        require(srcOffset >= 0 && srcOffset + 16 <= src.size) { "ChaCha20 HP sample window out of range" }
        require(hpKey.size == 32) { "ChaCha20 HP key must be 32 bytes" }
        val counter =
            ((src[srcOffset].toInt() and 0xFF)) or
                ((src[srcOffset + 1].toInt() and 0xFF) shl 8) or
                ((src[srcOffset + 2].toInt() and 0xFF) shl 16) or
                ((src[srcOffset + 3].toInt() and 0xFF) shl 24)
        val nonce = src.copyOfRange(srcOffset + 4, srcOffset + 16)
        return chacha20Encrypt.encrypt(hpKey, nonce, counter, ByteArray(5))
    }
}

/**
 * SPI for one-block AES-ECB encryption (provided by jvmAndroid via JCA).
 * Two shapes:
 *
 *  - [encrypt] — returns a freshly allocated 16-byte ciphertext. Retained
 *    for callers that don't have a destination buffer at hand.
 *  - [encryptInto] — fills caller-owned [dst] starting at [dstOffset] with
 *    the AES-ECB encryption of `src[srcOffset..srcOffset+16)`. The hot QUIC
 *    header-protection path uses this overload so the per-packet allocation
 *    of both the sample slice AND the cipher output goes away (round-5 #P1).
 */
interface AesOneBlockEncrypt {
    fun encrypt(
        key: ByteArray,
        block: ByteArray,
    ): ByteArray

    fun encryptInto(
        key: ByteArray,
        src: ByteArray,
        srcOffset: Int,
        dst: ByteArray,
        dstOffset: Int,
    ) {
        // Default impl: copy the 16-byte sample out and call the existing
        // allocation-shaped overload. Concrete platform impls override
        // this with a zero-allocation Cipher.doFinal range overload.
        val ct = encrypt(key, src.copyOfRange(srcOffset, srcOffset + 16))
        ct.copyInto(dst, dstOffset, 0, 16)
    }
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
