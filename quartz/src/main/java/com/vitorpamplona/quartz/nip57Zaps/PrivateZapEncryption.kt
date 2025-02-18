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
package com.vitorpamplona.quartz.nip57Zaps

import com.vitorpamplona.quartz.nip04Dm.crypto.Nip04
import com.vitorpamplona.quartz.nip19Bech32.bech32.Bech32
import com.vitorpamplona.quartz.utils.sha256.sha256
import java.nio.charset.Charset
import java.security.SecureRandom
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class PrivateZapEncryption {
    companion object {
        fun createEncryptionPrivateKey(
            privkey: String,
            id: String,
            createdAt: Long,
        ): ByteArray {
            val str = privkey + id + createdAt.toString()
            val strbyte = str.toByteArray(Charset.forName("utf-8"))
            return sha256(strbyte)
        }

        fun encryptPrivateZapMessage(
            msg: String,
            privkey: ByteArray,
            pubkey: ByteArray,
        ): String {
            val sharedSecret = Nip04.getSharedSecret(privkey, pubkey)
            val iv = ByteArray(16)
            SecureRandom().nextBytes(iv)

            val keySpec = SecretKeySpec(sharedSecret, "AES")
            val ivSpec = IvParameterSpec(iv)

            val utf8message = msg.toByteArray(Charset.forName("utf-8"))
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
            val encryptedMsg = cipher.doFinal(utf8message)

            val encryptedMsgBech32 =
                Bech32.encode("pzap", Bech32.eight2five(encryptedMsg), Bech32.Encoding.Bech32)
            val ivBech32 = Bech32.encode("iv", Bech32.eight2five(iv), Bech32.Encoding.Bech32)

            return encryptedMsgBech32 + "_" + ivBech32
        }

        fun decryptPrivateZapMessage(
            msg: String,
            privkey: ByteArray,
            pubkey: ByteArray,
        ): String {
            val sharedSecret = Nip04.getSharedSecret(privkey, pubkey)
            if (sharedSecret.size != 16 && sharedSecret.size != 32) {
                throw IllegalArgumentException("Invalid shared secret size")
            }
            val parts = msg.split("_")
            if (parts.size != 2) {
                throw IllegalArgumentException("Invalid message format")
            }
            val iv = parts[1].run { Bech32.decode(this).second }
            val encryptedMsg = parts.first().run { Bech32.decode(this).second }
            val encryptedBytes = Bech32.five2eight(encryptedMsg, 0)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(sharedSecret, "AES"),
                IvParameterSpec(
                    Bech32.five2eight(iv, 0),
                ),
            )

            try {
                val decryptedMsgBytes = cipher.doFinal(encryptedBytes)
                return String(decryptedMsgBytes)
            } catch (ex: BadPaddingException) {
                throw IllegalArgumentException("Bad padding: ${ex.message}")
            }
        }
    }
}
