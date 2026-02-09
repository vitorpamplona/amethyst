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
package com.vitorpamplona.quartz.nip57Zaps

import com.vitorpamplona.quartz.nip04Dm.crypto.Nip04
import com.vitorpamplona.quartz.nip19Bech32.bech32.Bech32
import com.vitorpamplona.quartz.utils.RandomInstance
import com.vitorpamplona.quartz.utils.ciphers.AESCBC
import com.vitorpamplona.quartz.utils.sha256.sha256
import kotlinx.coroutines.CancellationException

class PrivateZapEncryption {
    companion object {
        fun createEncryptionPrivateKey(
            privkey: String,
            id: String,
            createdAt: Long,
        ): ByteArray {
            val str = privkey + id + createdAt.toString()
            val strbyte = str.encodeToByteArray()
            return sha256(strbyte)
        }

        fun encryptPrivateZapMessage(
            msg: String,
            privkey: ByteArray,
            pubkey: ByteArray,
        ): String {
            val sharedSecret = Nip04.getSharedSecret(privkey, pubkey)
            val iv = RandomInstance.bytes(16)

            val utf8message = msg.encodeToByteArray()

            val cipher = AESCBC(sharedSecret, iv)
            val encryptedMsg = cipher.encrypt(utf8message)

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

            val cipher = AESCBC(sharedSecret, Bech32.five2eight(iv, 0))

            try {
                val decryptedMsgBytes = cipher.decrypt(encryptedBytes)
                return decryptedMsgBytes.decodeToString()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                throw IllegalArgumentException("Bad padding: ${e.message}")
            }
        }
    }
}
