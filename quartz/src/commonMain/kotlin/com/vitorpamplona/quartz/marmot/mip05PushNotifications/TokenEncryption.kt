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
package com.vitorpamplona.quartz.marmot.mip05PushNotifications

import com.vitorpamplona.quartz.marmot.mip05PushNotifications.TokenEncryption.PLATFORM_APNS
import com.vitorpamplona.quartz.marmot.mip05PushNotifications.TokenEncryption.PLATFORM_FCM
import com.vitorpamplona.quartz.marmot.mip05PushNotifications.tags.TokenTag
import com.vitorpamplona.quartz.nip44Encryption.crypto.ChaCha20Poly1305
import com.vitorpamplona.quartz.utils.RandomInstance
import com.vitorpamplona.quartz.utils.Secp256k1Instance
import com.vitorpamplona.quartz.utils.mac.MacInstance
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Handles EncryptedToken creation and decryption for Marmot push notifications (MIP-05).
 *
 * EncryptedToken format (MUST be exactly 1084 bytes per MIP-05):
 *   ephemeral_pubkey(32) || nonce(12) || ciphertext(1040 = 1024 plaintext + 16 tag)
 *
 * Token plaintext (MUST be exactly 1024 bytes per MIP-05):
 *   platform(1) || token_length(2 BE) || device_token(N) || random_padding(1024-3-N)
 *
 * Key derivation (MIP-05 §"Key Derivation"):
 *   1. ECDH: shared_x = secp256k1_ecdh(ephemeral_privkey, server_pubkey)  — raw 32-byte x
 *   2. PRK = HKDF-Extract(salt="mip05-v1", IKM=shared_x)
 *   3. encryption_key = HKDF-Expand(PRK, info="mip05-token-encryption", 32)
 *   4. Encrypt padded plaintext with ChaCha20-Poly1305(key, nonce, plaintext, aad="")
 *
 * Platform values: 0x01 = APNs, 0x02 = FCM
 */
object TokenEncryption {
    /** Token plaintext MUST be exactly 1024 bytes per MIP-05. */
    private const val PADDED_PAYLOAD_SIZE = 1024
    private const val NONCE_SIZE = 12
    private const val PUBKEY_SIZE = 32
    private const val HEADER_SIZE = 3 // platform(1) + token_length(2)
    private const val MAX_TOKEN_SIZE = PADDED_PAYLOAD_SIZE - HEADER_SIZE

    private val HKDF_SALT = "mip05-v1".encodeToByteArray()
    private val HKDF_INFO = "mip05-token-encryption".encodeToByteArray()
    private val EMPTY_AAD = ByteArray(0)

    const val PLATFORM_APNS: Byte = 0x01
    const val PLATFORM_FCM: Byte = 0x02

    /**
     * Encrypts a device token for a notification server.
     *
     * @param platform platform identifier (PLATFORM_APNS or PLATFORM_FCM)
     * @param deviceToken raw device token bytes
     * @param serverPubKey 32-byte notification server public key
     * @return base64-encoded EncryptedToken (280 bytes when decoded)
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun encrypt(
        platform: Byte,
        deviceToken: ByteArray,
        serverPubKey: ByteArray,
    ): String {
        require(deviceToken.size <= MAX_TOKEN_SIZE) {
            "Device token too large: ${deviceToken.size} bytes, max $MAX_TOKEN_SIZE"
        }
        require(serverPubKey.size == PUBKEY_SIZE) { "Server pubkey must be $PUBKEY_SIZE bytes" }

        // Build padded payload: platform(1) || token_length(2 BE) || token || random_padding
        val payload = ByteArray(PADDED_PAYLOAD_SIZE)
        payload[0] = platform
        payload[1] = (deviceToken.size ushr 8 and 0xFF).toByte()
        payload[2] = (deviceToken.size and 0xFF).toByte()
        deviceToken.copyInto(payload, HEADER_SIZE)
        // Fill remaining bytes with random padding
        val paddingStart = HEADER_SIZE + deviceToken.size
        val padding = RandomInstance.bytes(PADDED_PAYLOAD_SIZE - paddingStart)
        padding.copyInto(payload, paddingStart)

        // Generate ephemeral keypair for ECDH (x-only 32-byte pubkey)
        val ephemeralPrivKey = RandomInstance.bytes(32)
        val compressedPubKey = Secp256k1Instance.compressedPubKeyFor(ephemeralPrivKey)
        // Extract the 32-byte x-only public key by dropping the SEC1 prefix byte
        val ephemeralPubKey = compressedPubKey.copyOfRange(1, 33)

        // ECDH: shared_x = secp256k1_ecdh(ephemeral_privkey, server_pubkey) — raw 32-byte x
        // per MIP-05. Do NOT hash; HKDF-Extract will mix the salt.
        val sharedX = Secp256k1Instance.pubKeyTweakMulCompact(serverPubKey, ephemeralPrivKey)

        // HKDF-Extract then Expand to get encryption key
        val encryptionKey = hkdfDeriveKey(sharedX)

        // Encrypt with ChaCha20-Poly1305
        val nonce = RandomInstance.bytes(NONCE_SIZE)
        val ciphertextWithTag = ChaCha20Poly1305.encrypt(payload, EMPTY_AAD, nonce, encryptionKey)

        // Assemble: ephemeral_pubkey(32) || nonce(12) || ciphertext+tag(1040)
        val result = ByteArray(TokenTag.ENCRYPTED_TOKEN_SIZE)
        ephemeralPubKey.copyInto(result, 0)
        nonce.copyInto(result, PUBKEY_SIZE)
        ciphertextWithTag.copyInto(result, PUBKEY_SIZE + NONCE_SIZE)

        return Base64.encode(result)
    }

    /**
     * Decrypts an EncryptedToken using the notification server's private key.
     *
     * @param encryptedTokenBase64 base64-encoded EncryptedToken
     * @param serverPrivKey 32-byte notification server private key
     * @return decoded token info
     * @throws IllegalStateException if authentication fails
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun decrypt(
        encryptedTokenBase64: String,
        serverPrivKey: ByteArray,
    ): DecryptedToken {
        val data = Base64.decode(encryptedTokenBase64)
        require(data.size == TokenTag.ENCRYPTED_TOKEN_SIZE) {
            "EncryptedToken must be ${TokenTag.ENCRYPTED_TOKEN_SIZE} bytes, got ${data.size}"
        }

        // Parse components
        val ephemeralPubKey = data.copyOfRange(0, PUBKEY_SIZE)
        val nonce = data.copyOfRange(PUBKEY_SIZE, PUBKEY_SIZE + NONCE_SIZE)
        val ciphertextWithTag = data.copyOfRange(PUBKEY_SIZE + NONCE_SIZE, data.size)

        // ECDH: shared_x = secp256k1_ecdh(server_privkey, ephemeral_pubkey) — raw 32-byte x
        // per MIP-05.
        val sharedX = Secp256k1Instance.pubKeyTweakMulCompact(ephemeralPubKey, serverPrivKey)

        // Derive encryption key
        val encryptionKey = hkdfDeriveKey(sharedX)

        // Decrypt
        val payload = ChaCha20Poly1305.decrypt(ciphertextWithTag, EMPTY_AAD, nonce, encryptionKey)

        // Parse payload: platform(1) || token_length(2 BE) || token || padding
        val platform = payload[0]
        val tokenLength = ((payload[1].toInt() and 0xFF) shl 8) or (payload[2].toInt() and 0xFF)
        require(tokenLength in 0..MAX_TOKEN_SIZE) { "Invalid token length: $tokenLength" }

        val deviceToken = payload.copyOfRange(HEADER_SIZE, HEADER_SIZE + tokenLength)

        return DecryptedToken(platform, deviceToken)
    }

    /**
     * HKDF-Extract(salt="mip05-v1", IKM=sharedX) then HKDF-Expand(PRK, info="mip05-token-encryption", 32).
     */
    private fun hkdfDeriveKey(sharedX: ByteArray): ByteArray {
        // HKDF-Extract: PRK = HMAC-SHA256(salt, IKM)
        val extractMac = MacInstance("HmacSHA256", HKDF_SALT)
        extractMac.update(sharedX)
        val prk = extractMac.doFinal()

        // HKDF-Expand: T(1) = HMAC-SHA256(PRK, info || 0x01), take first 32 bytes
        val expandMac = MacInstance("HmacSHA256", prk)
        expandMac.update(HKDF_INFO)
        expandMac.update(0x01.toByte())
        return expandMac.doFinal()
    }

    /**
     * Result of decrypting an EncryptedToken.
     */
    data class DecryptedToken(
        /** Platform identifier: [PLATFORM_APNS] or [PLATFORM_FCM] */
        val platform: Byte,
        /** Raw device token bytes */
        val deviceToken: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is DecryptedToken) return false
            return platform == other.platform && deviceToken.contentEquals(other.deviceToken)
        }

        override fun hashCode(): Int {
            var result = platform.toInt()
            result = 31 * result + deviceToken.contentHashCode()
            return result
        }
    }
}
