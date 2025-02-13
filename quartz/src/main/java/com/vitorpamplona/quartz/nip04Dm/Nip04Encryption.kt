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
package com.vitorpamplona.quartz.nip04Dm

import com.vitorpamplona.quartz.nip44Encryption.SharedKeyCache
import com.vitorpamplona.quartz.utils.Hex
import fr.acinq.secp256k1.Secp256k1
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class Nip04Encryption(
    val secp256k1: Secp256k1,
    val random: SecureRandom,
) {
    private val sharedKeyCache = SharedKeyCache()
    private val h02 = Hex.decode("02")

    fun clearCache() {
        sharedKeyCache.clearCache()
    }

    fun encrypt(
        msg: String,
        privateKey: ByteArray,
        pubKey: ByteArray,
    ): String = encrypt(msg, getSharedSecret(privateKey, pubKey)).encodeToNIP04()

    fun encrypt(
        msg: String,
        sharedSecret: ByteArray,
    ): Nip04Encoder {
        val iv = ByteArray(16)
        random.nextBytes(iv)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(sharedSecret, "AES"), IvParameterSpec(iv))
        val encryptedMsg = cipher.doFinal(msg.toByteArray())
        return Nip04Encoder(encryptedMsg, iv)
    }

    fun decrypt(
        msg: String,
        privateKey: ByteArray,
        pubKey: ByteArray,
    ): String {
        val sharedSecret = getSharedSecret(privateKey, pubKey)
        return decrypt(msg, sharedSecret)
    }

    fun decrypt(
        encryptedInfo: Nip04Encoder,
        privateKey: ByteArray,
        pubKey: ByteArray,
    ): String {
        val sharedSecret = getSharedSecret(privateKey, pubKey)
        return decrypt(encryptedInfo.ciphertext, encryptedInfo.nonce, sharedSecret)
    }

    fun decrypt(
        msg: String,
        sharedSecret: ByteArray,
    ): String {
        val decoded = Nip04Encoder.decodeFromNIP04(msg)
        check(decoded != null) { "Unable to decode msg $msg as NIP04" }
        return decrypt(decoded.ciphertext, decoded.nonce, sharedSecret)
    }

    fun decrypt(
        cipher: String,
        nonce: String,
        sharedSecret: ByteArray,
    ): String {
        val iv = Base64.getDecoder().decode(nonce)
        val encryptedMsg = Base64.getDecoder().decode(cipher)
        return decrypt(encryptedMsg, iv, sharedSecret)
    }

    fun decrypt(
        encryptedMsg: ByteArray,
        iv: ByteArray,
        sharedSecret: ByteArray,
    ): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(sharedSecret, "AES"), IvParameterSpec(iv))
        return String(cipher.doFinal(encryptedMsg))
    }

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
    ): ByteArray = secp256k1.pubKeyTweakMul(h02 + pubKey, privateKey).copyOfRange(1, 33)

    companion object {
        fun isNIP04(encoded: String) = Nip04Encoder.isNIP04(encoded)
    }
}
