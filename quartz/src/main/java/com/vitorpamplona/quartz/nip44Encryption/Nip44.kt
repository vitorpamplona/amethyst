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

import android.util.Log
import com.vitorpamplona.quartz.nip01Core.jackson.EventMapper
import com.vitorpamplona.quartz.nip04Dm.crypto.EncryptedInfo
import com.vitorpamplona.quartz.nip04Dm.crypto.Nip04
import java.util.Base64

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
    ): String? {
        if (payload.isEmpty()) return null
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
    ): String? {
        // Ignores if it is not a valid json
        val info =
            try {
                EventMapper.mapper.readValue(json, EncryptedInfoString::class.java)
            } catch (e: Exception) {
                Log.e("NIP44", "Unable to parse json $json")
                return null
            }

        return when (info.v) {
            EncryptedInfo.V -> {
                val encryptedInfo =
                    EncryptedInfo(
                        ciphertext = Base64.getDecoder().decode(info.ciphertext),
                        nonce = Base64.getDecoder().decode(info.nonce),
                    )
                Nip04.decrypt(encryptedInfo, privateKey, pubKey)
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

    private fun decryptNIP44FromBase64(
        payload: String,
        privateKey: ByteArray,
        pubKey: ByteArray,
    ): String? {
        if (payload.isEmpty()) return null

        // Ignores if it is not base64
        val byteArray =
            try {
                Base64.getDecoder().decode(payload)
            } catch (e: Exception) {
                Log.e("NIP44", "Unable to parse base64 $payload")
                return null
            }

        return when (byteArray[0].toInt()) {
            EncryptedInfo.V -> Nip04.decrypt(payload, privateKey, pubKey)
            Nip44v1.EncryptedInfo.V -> v1.decrypt(payload, privateKey, pubKey)
            Nip44v2.EncryptedInfo.V -> v2.decrypt(payload, privateKey, pubKey)
            else -> null
        }
    }
}
