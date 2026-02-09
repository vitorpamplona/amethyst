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

import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip04Dm.crypto.EncryptedInfo
import com.vitorpamplona.quartz.nip04Dm.crypto.Nip04
import kotlin.io.encoding.Base64

object Nip44 {
    val v1 = Nip44v1()
    val v2 = Nip44v2()

    fun clearCache() {
        v1.clearCache()
        v2.clearCache()
    }

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
    ): String {
        require(payload.isNotBlank()) { "Payload must not be blank" }
        return if (payload[0] == '{') {
            decryptNIP44FromJackson(payload, privateKey, pubKey)
        } else {
            decryptNIP44FromBase64(payload, privateKey, pubKey)
        }
    }

    private fun decryptNIP44FromJackson(
        json: String,
        privateKey: ByteArray,
        pubKey: ByteArray,
    ): String {
        // Ignores if it is not a valid json
        val info =
            try {
                JsonMapper.fromJson<EncryptedInfoString>(json)
            } catch (e: Exception) {
                throw IllegalArgumentException("Unable to parse NIP-44 JSON: $json", e)
            }

        return when (info.v) {
            EncryptedInfo.V -> {
                val encryptedInfo =
                    EncryptedInfo(
                        ciphertext = Base64.decode(info.ciphertext),
                        nonce = Base64.decode(info.nonce),
                    )
                Nip04.decrypt(encryptedInfo, privateKey, pubKey)
            }

            Nip44v1.EncryptedInfo.V -> {
                val encryptedInfo =
                    Nip44v1.EncryptedInfo(
                        ciphertext = Base64.decode(info.ciphertext),
                        nonce = Base64.decode(info.nonce),
                    )
                v1.decrypt(encryptedInfo, privateKey, pubKey)
            }

            Nip44v2.EncryptedInfo.V -> {
                if (info.mac == null) {
                    throw IllegalArgumentException("Invalid NIP-44v2 JSON. Missing MAC: $json")
                }
                val encryptedInfo =
                    Nip44v2.EncryptedInfo(
                        ciphertext = Base64.decode(info.ciphertext),
                        nonce = Base64.decode(info.nonce),
                        mac = Base64.decode(info.mac),
                    )
                v2.decrypt(encryptedInfo, privateKey, pubKey)
            }

            else -> {
                throw IllegalArgumentException("Invalid or unsupported NIP-44 version code ${info.v}")
            }
        }
    }

    private fun decryptNIP44FromBase64(
        ciphertext: String,
        privateKey: ByteArray,
        pubKey: ByteArray,
    ): String {
        require(ciphertext.isNotBlank()) { "ciphertext must not be blank" }

        // Ignores if it is not base64
        val byteArray = Base64.decode(ciphertext)

        return when (byteArray[0].toInt()) {
            EncryptedInfo.V -> Nip04.decrypt(ciphertext, privateKey, pubKey)
            Nip44v1.EncryptedInfo.V -> v1.decrypt(ciphertext, privateKey, pubKey)
            Nip44v2.EncryptedInfo.V -> v2.decrypt(ciphertext, privateKey, pubKey)
            else -> throw IllegalArgumentException("Invalid or unsupported NIP-44 version code ${byteArray[0].toInt()}")
        }
    }
}
