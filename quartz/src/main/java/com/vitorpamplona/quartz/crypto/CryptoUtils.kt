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
package com.vitorpamplona.quartz.crypto

import com.vitorpamplona.quartz.crypto.nip01.Nip01
import com.vitorpamplona.quartz.crypto.nip04.Nip04
import com.vitorpamplona.quartz.crypto.nip06.Nip06
import com.vitorpamplona.quartz.crypto.nip44.Nip44
import com.vitorpamplona.quartz.crypto.nip44.Nip44v2
import com.vitorpamplona.quartz.crypto.nip49.Nip49
import com.vitorpamplona.quartz.encoders.HexKey
import fr.acinq.secp256k1.Secp256k1
import java.security.SecureRandom

object CryptoUtils {
    private val secp256k1 = Secp256k1.get()
    private val random = SecureRandom()

    public val nip01 = Nip01(secp256k1, random)
    public val nip06 = Nip06(secp256k1)
    public val nip04 = Nip04(secp256k1, random)
    public val nip44 = Nip44(secp256k1, random, nip04)
    public val nip49 = Nip49(secp256k1, random)

    fun clearCache() {
        nip04.clearCache()
        nip44.clearCache()
    }

    fun randomInt(bound: Int): Int = random.nextInt(bound)

    fun random(size: Int): ByteArray {
        val bytes = ByteArray(size)
        random.nextBytes(bytes)
        return bytes
    }

    /** Provides a 32B "private key" aka random number */
    fun privkeyCreate() = nip01.privkeyCreate()

    fun pubkeyCreate(privKey: ByteArray) = nip01.pubkeyCreate(privKey)

    fun signString(
        message: String,
        privKey: ByteArray,
        auxrand32: ByteArray = random(32),
    ): ByteArray = nip01.signString(message, privKey, auxrand32)

    fun sign(
        data: ByteArray,
        privKey: ByteArray,
        auxrand32: ByteArray? = null,
    ): ByteArray = nip01.sign(data, privKey, auxrand32)

    fun verifySignature(
        signature: ByteArray,
        hash: ByteArray,
        pubKey: ByteArray,
    ): Boolean = nip01.verify(signature, hash, pubKey)

    fun sha256(data: ByteArray): ByteArray {
        // Creates a new buffer every time
        return nip01.sha256(data)
    }

    /** NIP 04 Utils */
    fun encryptNIP04(
        msg: String,
        privateKey: ByteArray,
        pubKey: ByteArray,
    ): String = nip04.encrypt(msg, privateKey, pubKey)

    fun encryptNIP04(
        msg: String,
        sharedSecret: ByteArray,
    ): Nip04.EncryptedInfo = nip04.encrypt(msg, sharedSecret)

    fun decryptNIP04(
        msg: String,
        privateKey: ByteArray,
        pubKey: ByteArray,
    ): String = nip04.decrypt(msg, privateKey, pubKey)

    fun decryptNIP04(
        encryptedInfo: Nip04.EncryptedInfo,
        privateKey: ByteArray,
        pubKey: ByteArray,
    ): String = nip04.decrypt(encryptedInfo, privateKey, pubKey)

    fun decryptNIP04(
        msg: String,
        sharedSecret: ByteArray,
    ): String = nip04.decrypt(msg, sharedSecret)

    private fun decryptNIP04(
        cipher: String,
        nonce: String,
        sharedSecret: ByteArray,
    ): String = nip04.decrypt(cipher, nonce, sharedSecret)

    private fun decryptNIP04(
        encryptedMsg: ByteArray,
        iv: ByteArray,
        sharedSecret: ByteArray,
    ): String = nip04.decrypt(encryptedMsg, iv, sharedSecret)

    fun getSharedSecretNIP04(
        privateKey: ByteArray,
        pubKey: ByteArray,
    ): ByteArray = nip04.getSharedSecret(privateKey, pubKey)

    fun computeSharedSecretNIP04(
        privateKey: ByteArray,
        pubKey: ByteArray,
    ): ByteArray = nip04.computeSharedSecret(privateKey, pubKey)

    /** NIP 06 Utils */
    fun isValidMnemonic(mnemonic: String): Boolean = nip06.isValidMnemonic(mnemonic)

    fun privateKeyFromMnemonic(
        mnemonic: String,
        account: Long = 0,
    ) = nip06.privateKeyFromMnemonic(mnemonic, account)

    /** NIP 44 Utils */
    fun getSharedSecretNIP44(
        privateKey: ByteArray,
        pubKey: ByteArray,
    ): ByteArray = nip44.getSharedSecret(privateKey, pubKey)

    fun computeSharedSecretNIP44(
        privateKey: ByteArray,
        pubKey: ByteArray,
    ): ByteArray = nip44.computeSharedSecret(privateKey, pubKey)

    fun encryptNIP44(
        msg: String,
        privateKey: ByteArray,
        pubKey: ByteArray,
    ): Nip44v2.EncryptedInfo = nip44.encrypt(msg, privateKey, pubKey)

    fun decryptNIP44(
        payload: String,
        privateKey: ByteArray,
        pubKey: ByteArray,
    ): String? = nip44.decrypt(payload, privateKey, pubKey)

    /** NIP 49 Utils */
    fun decryptNIP49(
        payload: String,
        password: String,
    ): String? {
        if (payload.isEmpty() || password.isEmpty()) return null
        return nip49.decrypt(payload, password)
    }

    fun encryptNIP49(
        key: HexKey,
        password: String,
    ): String = nip49.encrypt(key, password)
}
