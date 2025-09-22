/**
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
package com.vitorpamplona.quartz.nip04Dm.crypto

import com.vitorpamplona.quartz.utils.RandomInstance
import com.vitorpamplona.quartz.utils.Secp256k1Instance
import com.vitorpamplona.quartz.utils.ciphers.AESCBC

class Encryption {
    fun encrypt(
        msg: String,
        privateKey: ByteArray,
        pubKey: ByteArray,
    ): String = encrypt(msg, computeSharedSecret(privateKey, pubKey))

    fun decrypt(
        msg: String,
        privateKey: ByteArray,
        pubKey: ByteArray,
    ): String = decrypt(msg, computeSharedSecret(privateKey, pubKey))

    fun encrypt(
        msg: String,
        sharedSecret: ByteArray,
    ): String = encryptToEncoder(msg, sharedSecret).encodeToNIP04()

    fun encryptToEncoder(
        msg: String,
        sharedSecret: ByteArray,
    ): EncryptedInfo {
        val iv = RandomInstance.bytes(16)
        return EncryptedInfo(AESCBC(sharedSecret, iv).encrypt(msg.encodeToByteArray()), iv)
    }

    fun decrypt(
        msg: String,
        sharedSecret: ByteArray,
    ): String {
        val decoded = EncryptedInfo.decode(msg)
        check(decoded != null) { "Unable to decode msg $msg as NIP04" }
        return decrypt(decoded, sharedSecret)
    }

    fun decrypt(
        msg: EncryptedInfo,
        sharedSecret: ByteArray,
    ): String = decrypt(msg.ciphertext, msg.nonce, sharedSecret)

    fun decrypt(
        encryptedMsg: ByteArray,
        iv: ByteArray,
        sharedSecret: ByteArray,
    ): String = AESCBC(sharedSecret, iv).decrypt(encryptedMsg).decodeToString()

    fun computeSharedSecret(
        privateKey: ByteArray,
        pubKey: ByteArray,
    ): ByteArray = Secp256k1Instance.pubKeyTweakMulCompact(pubKey, privateKey)
}
