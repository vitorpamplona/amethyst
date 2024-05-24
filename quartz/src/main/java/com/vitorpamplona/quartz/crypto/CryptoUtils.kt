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

import android.util.Log
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.events.Event
import fr.acinq.secp256k1.Secp256k1
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object CryptoUtils {
    private val secp256k1 = Secp256k1.get()
    private val random = SecureRandom()

    private val nip04 = Nip04(secp256k1, random)
    private val nip44v1 = Nip44v1(secp256k1, random)
    private val nip44v2 = Nip44v2(secp256k1, random)
    private val nip49 = Nip49(secp256k1, random)

    fun clearCache() {
        nip04.clearCache()
        nip44v1.clearCache()
        nip44v2.clearCache()
    }

    fun randomInt(bound: Int): Int {
        return random.nextInt(bound)
    }

    /** Provides a 32B "private key" aka random number */
    fun privkeyCreate() = random(32)

    fun random(size: Int): ByteArray {
        val bytes = ByteArray(size)
        random.nextBytes(bytes)
        return bytes
    }

    fun pubkeyCreateBitcoin(privKey: ByteArray) = secp256k1.pubKeyCompress(secp256k1.pubkeyCreate(privKey))

    fun pubkeyCreate(privKey: ByteArray) = secp256k1.pubKeyCompress(secp256k1.pubkeyCreate(privKey)).copyOfRange(1, 33)

    fun isPrivKeyValid(il: ByteArray): Boolean {
        return secp256k1.secKeyVerify(il)
    }

    fun sign(
        data: ByteArray,
        privKey: ByteArray,
    ): ByteArray = secp256k1.signSchnorr(data, privKey, null)

    fun verifySignature(
        signature: ByteArray,
        hash: ByteArray,
        pubKey: ByteArray,
    ): Boolean {
        return secp256k1.verifySchnorr(signature, hash, pubKey)
    }

    fun sha256(data: ByteArray): ByteArray {
        // Creates a new buffer every time
        return MessageDigest.getInstance("SHA-256").digest(data)
    }

    /** NIP 04 Utils */
    fun encryptNIP04(
        msg: String,
        privateKey: ByteArray,
        pubKey: ByteArray,
    ): String {
        return nip04.encrypt(msg, privateKey, pubKey)
    }

    fun encryptNIP04(
        msg: String,
        sharedSecret: ByteArray,
    ): Nip04.EncryptedInfo {
        return nip04.encrypt(msg, sharedSecret)
    }

    fun decryptNIP04(
        msg: String,
        privateKey: ByteArray,
        pubKey: ByteArray,
    ): String {
        return nip04.decrypt(msg, privateKey, pubKey)
    }

    fun decryptNIP04(
        encryptedInfo: Nip04.EncryptedInfo,
        privateKey: ByteArray,
        pubKey: ByteArray,
    ): String {
        return nip04.decrypt(encryptedInfo, privateKey, pubKey)
    }

    fun decryptNIP04(
        msg: String,
        sharedSecret: ByteArray,
    ): String {
        return nip04.decrypt(msg, sharedSecret)
    }

    private fun decryptNIP04(
        cipher: String,
        nonce: String,
        sharedSecret: ByteArray,
    ): String {
        return nip04.decrypt(cipher, nonce, sharedSecret)
    }

    private fun decryptNIP04(
        encryptedMsg: ByteArray,
        iv: ByteArray,
        sharedSecret: ByteArray,
    ): String {
        return nip04.decrypt(encryptedMsg, iv, sharedSecret)
    }

    fun getSharedSecretNIP04(
        privateKey: ByteArray,
        pubKey: ByteArray,
    ): ByteArray {
        return nip04.getSharedSecret(privateKey, pubKey)
    }

    fun computeSharedSecretNIP04(
        privateKey: ByteArray,
        pubKey: ByteArray,
    ): ByteArray {
        return nip04.computeSharedSecret(privateKey, pubKey)
    }

    /** NIP 44v1 Utils */
    fun encryptNIP44v1(
        msg: String,
        privateKey: ByteArray,
        pubKey: ByteArray,
    ): Nip44v1.EncryptedInfo {
        return nip44v1.encrypt(msg, privateKey, pubKey)
    }

    fun encryptNIP44v1(
        msg: String,
        sharedSecret: ByteArray,
    ): Nip44v1.EncryptedInfo {
        return nip44v1.encrypt(msg, sharedSecret)
    }

    fun decryptNIP44v1(
        encryptedInfo: Nip44v1.EncryptedInfo,
        privateKey: ByteArray,
        pubKey: ByteArray,
    ): String? {
        return nip44v1.decrypt(encryptedInfo, privateKey, pubKey)
    }

    fun decryptNIP44v1(
        encryptedInfo: String,
        privateKey: ByteArray,
        pubKey: ByteArray,
    ): String? {
        return nip44v1.decrypt(encryptedInfo, privateKey, pubKey)
    }

    fun decryptNIP44v1(
        encryptedInfo: Nip44v1.EncryptedInfo,
        sharedSecret: ByteArray,
    ): String? {
        return nip44v1.decrypt(encryptedInfo, sharedSecret)
    }

    fun getSharedSecretNIP44v1(
        privateKey: ByteArray,
        pubKey: ByteArray,
    ): ByteArray {
        return nip44v1.getSharedSecret(privateKey, pubKey)
    }

    fun computeSharedSecretNIP44v1(
        privateKey: ByteArray,
        pubKey: ByteArray,
    ): ByteArray {
        return nip44v1.computeSharedSecret(privateKey, pubKey)
    }

    /** NIP 44v2 Utils */
    fun encryptNIP44v2(
        msg: String,
        privateKey: ByteArray,
        pubKey: ByteArray,
    ): Nip44v2.EncryptedInfo {
        return nip44v2.encrypt(msg, privateKey, pubKey)
    }

    fun encryptNIP44v2(
        msg: String,
        sharedSecret: ByteArray,
    ): Nip44v2.EncryptedInfo {
        return nip44v2.encrypt(msg, sharedSecret)
    }

    fun decryptNIP44v2(
        encryptedInfo: Nip44v2.EncryptedInfo,
        privateKey: ByteArray,
        pubKey: ByteArray,
    ): String? {
        return nip44v2.decrypt(encryptedInfo, privateKey, pubKey)
    }

    fun decryptNIP44v2(
        encryptedInfo: String,
        privateKey: ByteArray,
        pubKey: ByteArray,
    ): String? {
        return nip44v2.decrypt(encryptedInfo, privateKey, pubKey)
    }

    fun decryptNIP44v2(
        encryptedInfo: Nip44v2.EncryptedInfo,
        sharedSecret: ByteArray,
    ): String? {
        return nip44v2.decrypt(encryptedInfo, sharedSecret)
    }

    fun getSharedSecretNIP44v2(
        privateKey: ByteArray,
        pubKey: ByteArray,
    ): ByteArray {
        return nip44v2.getConversationKey(privateKey, pubKey)
    }

    fun computeSharedSecretNIP44v2(
        privateKey: ByteArray,
        pubKey: ByteArray,
    ): ByteArray {
        return nip44v2.computeConversationKey(privateKey, pubKey)
    }

    fun decryptNIP44(
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
    ): String? {
        return nip49.encrypt(key, password, 16, Nip49.EncryptedInfo.CLIENT_DOES_NOT_TRACK)
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
        return try {
            val info = Event.mapper.readValue(json, EncryptedInfoString::class.java)

            when (info.v) {
                Nip04.EncryptedInfo.V -> {
                    val encryptedInfo =
                        Nip04.EncryptedInfo(
                            ciphertext = Base64.getDecoder().decode(info.ciphertext),
                            nonce = Base64.getDecoder().decode(info.nonce),
                        )
                    decryptNIP04(encryptedInfo, privateKey, pubKey)
                }
                Nip44v1.EncryptedInfo.V -> {
                    val encryptedInfo =
                        Nip44v1.EncryptedInfo(
                            ciphertext = Base64.getDecoder().decode(info.ciphertext),
                            nonce = Base64.getDecoder().decode(info.nonce),
                        )
                    decryptNIP44v1(encryptedInfo, privateKey, pubKey)
                }
                Nip44v2.EncryptedInfo.V -> {
                    val encryptedInfo =
                        Nip44v2.EncryptedInfo(
                            ciphertext = Base64.getDecoder().decode(info.ciphertext),
                            nonce = Base64.getDecoder().decode(info.nonce),
                            mac = Base64.getDecoder().decode(info.mac),
                        )
                    decryptNIP44v2(encryptedInfo, privateKey, pubKey)
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.e("CryptoUtils", "Could not identify the version for NIP44 payload $json")
            e.printStackTrace()
            null
        }
    }

    fun decryptNIP44FromBase64(
        payload: String,
        privateKey: ByteArray,
        pubKey: ByteArray,
    ): String? {
        if (payload.isEmpty()) return null

        return try {
            val byteArray = Base64.getDecoder().decode(payload)

            when (byteArray[0].toInt()) {
                Nip04.EncryptedInfo.V -> decryptNIP04(payload, privateKey, pubKey)
                Nip44v1.EncryptedInfo.V -> decryptNIP44v1(payload, privateKey, pubKey)
                Nip44v2.EncryptedInfo.V -> decryptNIP44v2(payload, privateKey, pubKey)
                else -> null
            }
        } catch (e: Exception) {
            Log.e("CryptoUtils", "Could not identify the version for NIP44 payload $payload")
            e.printStackTrace()
            null
        }
    }

    fun sum(
        first: ByteArray,
        second: ByteArray,
    ): ByteArray {
        return secp256k1.privKeyTweakAdd(first, second)
    }
}
