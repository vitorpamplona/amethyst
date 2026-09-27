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
package com.vitorpamplona.quartz.nip44Encryption

import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip44Encryption.crypto.ChaCha20
import com.vitorpamplona.quartz.nip44Encryption.crypto.Hkdf
import com.vitorpamplona.quartz.utils.RandomInstance
import com.vitorpamplona.quartz.utils.Secp256k1Instance
import com.vitorpamplona.quartz.utils.equalsConstantTime
import kotlinx.coroutines.CancellationException
import kotlin.io.encoding.Base64

/**
 * NIP-44 v2 encryption.
 *
 * @param maxPayloadLength the largest base64 payload [decrypt] accepts. NIP-44 lets plaintexts reach
 * 2^32 - 1 bytes but asks implementations to set their own ceiling and to enforce it before base64
 * decoding, since decrypting needs several times the payload size in working memory.
 */
class Nip44v2(
    val maxPayloadLength: Int = DEFAULT_MAX_PAYLOAD_LENGTH,
) {
    private val sharedKeyCache = SharedKeyCache()
    private val hkdf = Hkdf()
    private val chaCha = ChaCha20()

    private val saltPrefix = "nip44-v2".encodeToByteArray()
    private val hashLength = 32

    private val minPlaintextSize: Int = 0x0001 // 1b msg => padded to 32b
    private val maxPlaintextSize: Int = 0xffff // 65535 (64kb-1) => padded to 64kb, u16 prefix

    // Lengths at or above this use the extended [0,0][u32] prefix, and only those may.
    private val extMinPlaintextSize: Long = 0x10000 // 65536 => padded to 64kb, 6-byte prefix
    private val extMaxPlaintextSize: Long = 0xffffffff // 4294967295 => padded to 2^32

    fun clearCache() {
        sharedKeyCache.clearCache()
    }

    fun encrypt(
        msg: String,
        privateKey: ByteArray,
        pubKey: ByteArray,
    ): EncryptedInfo = encrypt(msg, getConversationKey(privateKey, pubKey))

    fun encrypt(
        plaintext: String,
        conversationKey: ByteArray,
    ): EncryptedInfo {
        val nonce = RandomInstance.bytes(hashLength)
        return encryptWithNonce(plaintext, conversationKey, nonce)
    }

    fun encryptWithNonce(
        plaintext: String,
        conversationKey: ByteArray,
        nonce: ByteArray,
    ): EncryptedInfo {
        val messageKeys = getMessageKeys(conversationKey, nonce)
        val padded = pad(plaintext)

        val ciphertext = chaCha.encrypt(padded, messageKeys.chachaNonce, messageKeys.chachaKey)

        val mac = hmacAad(messageKeys.hmacKey, ciphertext, nonce)

        return EncryptedInfo(
            nonce = nonce,
            ciphertext = ciphertext,
            mac = mac,
        )
    }

    fun decrypt(
        payload: String,
        privateKey: ByteArray,
        pubKey: ByteArray,
    ): String = decrypt(payload, getConversationKey(privateKey, pubKey))

    fun decrypt(
        decoded: EncryptedInfo,
        privateKey: ByteArray,
        pubKey: ByteArray,
    ): String = decrypt(decoded, getConversationKey(privateKey, pubKey))

    fun decrypt(
        payload: String,
        conversationKey: ByteArray,
    ): String = decrypt(EncryptedInfo.decodePayload(payload, maxPayloadLength), conversationKey)

    fun checkHMacAad(
        messageKey: Hkdf.MessageKey,
        decoded: EncryptedInfo,
    ) {
        val calculatedMac = hmacAad(messageKey.hmacKey, decoded.ciphertext, decoded.nonce)

        check(calculatedMac.equalsConstantTime(decoded.mac)) {
            "Invalid Mac: Calculated ${calculatedMac.toHexKey()}, decoded: ${decoded.mac.toHexKey()}"
        }
    }

    fun decrypt(
        decoded: EncryptedInfo,
        conversationKey: ByteArray,
    ): String {
        val messageKey = checkMessageKeys(conversationKey, decoded)

        return unpad(
            chaCha.decrypt(
                decoded.ciphertext,
                messageKey.chachaNonce,
                messageKey.chachaKey,
            ),
        )
    }

    fun getConversationKey(
        privateKey: ByteArray,
        pubKey: ByteArray,
    ): ByteArray {
        val preComputed = sharedKeyCache.get(privateKey, pubKey)
        if (preComputed != null) return preComputed

        val computed = computeConversationKey(privateKey, pubKey)
        sharedKeyCache.add(privateKey, pubKey, computed)
        return computed
    }

    fun calcPaddedLen(len: Int): Long = calcPaddedLen(len.toLong())

    /**
     * NIP-44 padded length. Integer-only: floating point loses precision above 2^24, and the
     * result can reach 2^32 (for 2^32 - 1), which only fits 64-bit arithmetic.
     */
    fun calcPaddedLen(len: Long): Long {
        check(len > 0) { "expected positive integer" }
        if (len <= 32) return 32
        // 1 shl (floor(log2(len - 1)) + 1) == 1 shl bitLength(len - 1)
        val nextPower = 1L shl (Long.SIZE_BITS - (len - 1).countLeadingZeroBits())
        val chunk = if (nextPower <= 256) 32L else nextPower / 8
        return chunk * ((len - 1) / chunk + 1)
    }

    fun pad(plaintext: String): ByteArray {
        val unpadded = plaintext.encodeToByteArray()
        val unpaddedLen = unpadded.size

        check(unpaddedLen >= minPlaintextSize) { "Message is empty ($unpaddedLen): $plaintext" }

        val prefixLen = if (unpaddedLen <= maxPlaintextSize) 2 else 6
        val totalLen = prefixLen + calcPaddedLen(unpaddedLen)

        require(totalLen <= Int.MAX_VALUE) {
            "Message is too long for this platform ($unpaddedLen bytes pad to $totalLen)"
        }

        // Zero-filled: everything after the plaintext is already the padding.
        val padded = ByteArray(totalLen.toInt())
        if (prefixLen == 2) {
            // 2 bytes in big endian
            padded[0] = (unpaddedLen shr 8).toByte()
            padded[1] = (unpaddedLen and 0xFF).toByte()
        } else {
            // Extension to allow >= 64KB payloads: [0, 0] + 4 bytes in big endian
            padded[2] = (unpaddedLen shr 24).toByte()
            padded[3] = (unpaddedLen shr 16).toByte()
            padded[4] = (unpaddedLen shr 8).toByte()
            padded[5] = (unpaddedLen and 0xFF).toByte()
        }
        unpadded.copyInto(padded, prefixLen)
        return padded
    }

    fun unpad(padded: ByteArray): String {
        check(padded.size >= 2) { "Invalid padding: ${padded.size} bytes" }
        val unpaddedLenPreExt: Int = bytesToIntBigEndian(padded[0], padded[1])

        return if (unpaddedLenPreExt == 0) {
            // NIP-44 extension to handle bigger than 65K payloads
            check(padded.size >= 6) { "Invalid padding: ${padded.size} bytes" }
            val unpaddedLenExt: Long = bytesToLongBigEndian(padded[2], padded[3], padded[4], padded[5])

            // A length that fits the u16 prefix must use it: rejects a second encoding of short messages.
            check(unpaddedLenExt in extMinPlaintextSize..extMaxPlaintextSize) {
                "Invalid size $unpaddedLenExt not between $extMinPlaintextSize and $extMaxPlaintextSize"
            }

            check(padded.size.toLong() == 6 + calcPaddedLen(unpaddedLenExt)) {
                "Invalid padding ${calcPaddedLen(unpaddedLenExt)} != $unpaddedLenExt"
            }

            padded.decodeToString(6, 6 + unpaddedLenExt.toInt())
        } else {
            check(unpaddedLenPreExt in minPlaintextSize..maxPlaintextSize) {
                "Invalid size $unpaddedLenPreExt not between $minPlaintextSize and $maxPlaintextSize"
            }

            check(padded.size.toLong() == 2 + calcPaddedLen(unpaddedLenPreExt)) {
                "Invalid padding ${calcPaddedLen(unpaddedLenPreExt)} != $unpaddedLenPreExt"
            }

            padded.decodeToString(2, 2 + unpaddedLenPreExt)
        }
    }

    fun hmacAad(
        key: ByteArray,
        message: ByteArray,
        aad: ByteArray,
    ): ByteArray {
        check(aad.size == hashLength) {
            "AAD associated data must be 32 bytes, but it was ${aad.size} bytes"
        }

        return hkdf.extract(aad, message, key)
    }

    fun getMessageKeys(
        conversationKey: ByteArray,
        nonce: ByteArray,
    ): Hkdf.MessageKey = hkdf.fastExpand(conversationKey, nonce)

    fun checkMessageKeys(
        conversationKey: ByteArray,
        decoded: EncryptedInfo,
    ): Hkdf.MessageKey = hkdf.fastExpand(conversationKey, decoded.nonce, decoded.ciphertext, decoded.mac)

    /** @return 32B shared secret */
    fun computeConversationKey(
        privateKey: ByteArray,
        pubKey: ByteArray,
    ): ByteArray {
        val sharedX = Secp256k1Instance.pubKeyTweakMulCompact(pubKey, privateKey)
        return hkdf.extract(sharedX, saltPrefix)
    }

    class EncryptedInfo(
        val nonce: ByteArray,
        val ciphertext: ByteArray,
        val mac: ByteArray,
    ) {
        companion object {
            const val V: Int = 2

            // version (1) + nonce (32) + smallest padded plaintext with prefix (2 + 32) + mac (32)
            const val MIN_DECODED_LENGTH: Int = 99

            // base64 of [MIN_DECODED_LENGTH] bytes
            const val MIN_PAYLOAD_LENGTH: Int = 132

            fun decodePayload(
                payload: String,
                maxPayloadLength: Int = DEFAULT_MAX_PAYLOAD_LENGTH,
            ): EncryptedInfo {
                check(payload.isNotEmpty() && payload[0] != '#') { "Unknown encryption version ${payload.getOrNull(0)}" }
                check(payload.length >= MIN_PAYLOAD_LENGTH) {
                    "Invalid payload length ${payload.length} for $payload"
                }
                // Before base64 decoding, so an oversized payload costs nothing to reject.
                check(payload.length <= maxPayloadLength) {
                    "Payload length ${payload.length} exceeds the maximum of $maxPayloadLength"
                }

                return try {
                    val byteArray = Base64.decode(payload)
                    check(byteArray.size >= MIN_DECODED_LENGTH) { "Invalid decoded length ${byteArray.size}" }
                    check(byteArray[0].toInt() == V) { "Unknown encryption version ${byteArray[0]}" }
                    EncryptedInfo(
                        nonce = byteArray.copyOfRange(1, 33),
                        ciphertext = byteArray.copyOfRange(33, byteArray.size - 32),
                        mac = byteArray.copyOfRange(byteArray.size - 32, byteArray.size),
                    )
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    throw IllegalStateException("NIP-44v2 Unable to Parse encrypted payload: $payload", e)
                }
            }
        }

        fun encodePayload(): String = Base64.encode(byteArrayOf(V.toByte()) + nonce + ciphertext + mac)
    }

    // -----------------------------------
    // FASTER METHODS THAN BUFFER WRAPPING
    // -----------------------------------
    private fun bytesToIntBigEndian(
        byte1: Byte,
        byte2: Byte,
    ): Int = (byte1.toInt() and 0xFF shl 8 or (byte2.toInt() and 0xFF))

    private fun bytesToLongBigEndian(
        byte1: Byte,
        byte2: Byte,
        byte3: Byte,
        byte4: Byte,
    ): Long =
        ((byte1.toLong() and 0xFF) shl 24) or
            ((byte2.toLong() and 0xFF) shl 16) or
            ((byte3.toLong() and 0xFF) shl 8) or
            (byte4.toLong() and 0xFF)

    companion object {
        /**
         * Default ceiling for a base64 payload: 64M chars (~48 MB decoded). Comfortably above the
         * spec's 20,000,000-byte test vector while bounding what a hostile event can make us allocate.
         */
        const val DEFAULT_MAX_PAYLOAD_LENGTH: Int = 64 * 1024 * 1024
    }
}
