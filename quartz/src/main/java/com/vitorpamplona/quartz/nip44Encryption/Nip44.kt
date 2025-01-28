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
package com.vitorpamplona.quartz.nip44Encryption

import com.vitorpamplona.quartz.nip01Core.jackson.EventMapper
import com.vitorpamplona.quartz.nip04Dm.Nip04
import fr.acinq.secp256k1.Secp256k1
import java.security.SecureRandom
import java.util.Base64

class Nip44(
    secp256k1: Secp256k1,
    random: SecureRandom,
    val nip04: Nip04,
) {
    public val v1 = Nip44v1(secp256k1, random)
    public val v2 = Nip44v2(secp256k1, random)

    fun clearCache() {
        v1.clearCache()
        v2.clearCache()
    }

    /** NIP 44v2 Utils */
    fun getSharedSecret(
        privateKey: ByteArray,
        pubKey: ByteArray,
    ): ByteArray = v2.getConversationKey(privateKey, pubKey)

    fun computeSharedSecret(
        privateKey: ByteArray,
        pubKey: ByteArray,
    ): ByteArray = v2.computeConversationKey(privateKey, pubKey)

    fun encrypt(
        msg: String,
        privateKey: ByteArray,
        pubKey: ByteArray,
    ): Nip44v2.EncryptedInfo {
        // current version should be used.
        return v2.encrypt(msg, privateKey, pubKey)
    }

    // Always decrypt from any version/any encoding
    fun decrypt(
        payload: String,
        privateKey: ByteArray,
        pubKey: ByteArray,
    ): String? {
        if (payload.isEmpty()) return null
        return if (payload[0] == '{') {
            decryptNIP44FromJackson(payload, privateKey, pubKey)
        } else {
            decryptNIP44FromBase64(payload, privateKey, pubKey)
        }
    }

    class EncryptedInfoString(
        val ciphertext: String,
        val nonce: String,
        val v: Int,
        val mac: String?,
    )

    fun decryptNIP44FromJackson(
        json: String,
        privateKey: ByteArray,
        pubKey: ByteArray,
    ): String? {
        val info = EventMapper.mapper.readValue(json, EncryptedInfoString::class.java)

        return when (info.v) {
            Nip04.EncryptedInfo.V -> {
                val encryptedInfo =
                    Nip04.EncryptedInfo(
                        ciphertext = Base64.getDecoder().decode(info.ciphertext),
                        nonce = Base64.getDecoder().decode(info.nonce),
                    )
                nip04.decrypt(encryptedInfo, privateKey, pubKey)
            }

            Nip44v1.EncryptedInfo.V -> {
                val encryptedInfo =
                    Nip44v1.EncryptedInfo(
                        ciphertext = Base64.getDecoder().decode(info.ciphertext),
                        nonce = Base64.getDecoder().decode(info.nonce),
                    )
                v1.decrypt(encryptedInfo, privateKey, pubKey)
            }

            Nip44v2.EncryptedInfo.V -> {
                val encryptedInfo =
                    Nip44v2.EncryptedInfo(
                        ciphertext = Base64.getDecoder().decode(info.ciphertext),
                        nonce = Base64.getDecoder().decode(info.nonce),
                        mac = Base64.getDecoder().decode(info.mac),
                    )
                v2.decrypt(encryptedInfo, privateKey, pubKey)
            }

            else -> null
        }
    }

    fun decryptNIP44FromBase64(
        payload: String,
        privateKey: ByteArray,
        pubKey: ByteArray,
    ): String? {
        if (payload.isEmpty()) return null

        val byteArray = Base64.getDecoder().decode(payload)

        return when (byteArray[0].toInt()) {
            Nip04.EncryptedInfo.V -> nip04.decrypt(payload, privateKey, pubKey)
            Nip44v1.EncryptedInfo.V -> v1.decrypt(payload, privateKey, pubKey)
            Nip44v2.EncryptedInfo.V -> v2.decrypt(payload, privateKey, pubKey)
            else -> null
        }
    }
}
