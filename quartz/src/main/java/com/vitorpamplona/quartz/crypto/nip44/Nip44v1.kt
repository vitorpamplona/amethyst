/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.quartz.crypto.nip44

import android.util.Log
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.utils.Key
import com.vitorpamplona.quartz.crypto.SharedKeyCache
import com.vitorpamplona.quartz.crypto.sha256Hash
import com.vitorpamplona.quartz.encoders.Hex
import fr.acinq.secp256k1.Secp256k1
import java.security.SecureRandom
import java.util.Base64

class Nip44v1(
    val secp256k1: Secp256k1,
    val random: SecureRandom,
) {
    private val sharedKeyCache = SharedKeyCache()
    private val h02 = Hex.decode("02")
    private val libSodium = SodiumAndroid()

    fun clearCache() {
        sharedKeyCache.clearCache()
    }

    fun encrypt(
        msg: String,
        privateKey: ByteArray,
        pubKey: ByteArray,
    ): EncryptedInfo {
        val sharedSecret = getSharedSecret(privateKey, pubKey)
        return encrypt(msg, sharedSecret)
    }

    fun encrypt(
        msg: String,
        sharedSecret: ByteArray,
    ): EncryptedInfo {
        val nonce = ByteArray(24)
        random.nextBytes(nonce)

        val cipher =
            cryptoStreamXChaCha20Xor(
                libSodium = libSodium,
                messageBytes = msg.toByteArray(),
                nonce = nonce,
                key = Key.fromBytes(sharedSecret),
            )

        return EncryptedInfo(
            ciphertext = cipher ?: ByteArray(0),
            nonce = nonce,
        )
    }

    fun decrypt(
        payload: String,
        privateKey: ByteArray,
        pubKey: ByteArray,
    ): String? {
        val sharedSecret = getSharedSecret(privateKey, pubKey)
        return decrypt(payload, sharedSecret)
    }

    fun decrypt(
        encryptedInfo: EncryptedInfo,
        privateKey: ByteArray,
        pubKey: ByteArray,
    ): String? {
        val sharedSecret = getSharedSecret(privateKey, pubKey)
        return decrypt(encryptedInfo, sharedSecret)
    }

    fun decrypt(
        payload: String,
        sharedSecret: ByteArray,
    ): String? {
        val encryptedInfo = EncryptedInfo.decodePayload(payload) ?: return null
        return decrypt(encryptedInfo, sharedSecret)
    }

    fun decrypt(
        encryptedInfo: EncryptedInfo,
        sharedSecret: ByteArray,
    ): String? =
        cryptoStreamXChaCha20Xor(
            libSodium = libSodium,
            messageBytes = encryptedInfo.ciphertext,
            nonce = encryptedInfo.nonce,
            key = Key.fromBytes(sharedSecret),
        )?.decodeToString()

    fun getSharedSecret(
        privateKey: ByteArray,
        pubKey: ByteArray,
    ): ByteArray {
        val preComputed = sharedKeyCache.get(privateKey, pubKey)
        if (preComputed != null) return preComputed

        val computed = computeSharedSecret(privateKey, pubKey)
        sharedKeyCache.add(privateKey, pubKey, computed)
        return computed
    }

    /** @return 32B shared secret */
    fun computeSharedSecret(
        privateKey: ByteArray,
        pubKey: ByteArray,
    ): ByteArray =
        sha256Hash(
            secp256k1.pubKeyTweakMul(h02 + pubKey, privateKey).copyOfRange(1, 33),
        )

    class EncryptedInfo(
        val ciphertext: ByteArray,
        val nonce: ByteArray,
    ) {
        companion object {
            const val V: Int = 1

            fun decodePayload(payload: String): EncryptedInfo? {
                return try {
                    val byteArray = Base64.getDecoder().decode(payload)
                    check(byteArray[0].toInt() == V)
                    return EncryptedInfo(
                        nonce = byteArray.copyOfRange(1, 25),
                        ciphertext = byteArray.copyOfRange(25, byteArray.size),
                    )
                } catch (e: Exception) {
                    Log.w("NIP44v1", "Unable to Parse encrypted payload: $payload")
                    null
                }
            }
        }

        fun encodePayload(): String =
            Base64
                .getEncoder()
                .encodeToString(
                    byteArrayOf(V.toByte()) + nonce + ciphertext,
                )
    }
}
