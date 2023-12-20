package com.vitorpamplona.quartz.crypto

import android.util.Log
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.utils.Key
import com.vitorpamplona.quartz.encoders.Hex
import fr.acinq.secp256k1.Secp256k1
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

class Nip44v1(val secp256k1: Secp256k1, val random: SecureRandom) {
    private val sharedKeyCache = SharedKeyCache()
    private val h02 = Hex.decode("02")
    private val libSodium = SodiumAndroid()

    fun clearCache() {
        sharedKeyCache.clearCache()
    }

    fun encrypt(msg: String, privateKey: ByteArray, pubKey: ByteArray): EncryptedInfo {
        val sharedSecret = getSharedSecret(privateKey, pubKey)
        return encrypt(msg, sharedSecret)
    }

    fun encrypt(msg: String, sharedSecret: ByteArray): EncryptedInfo {
        val nonce = ByteArray(24)
        random.nextBytes(nonce)

        val cipher = cryptoStreamXChaCha20Xor(
            libSodium = libSodium,
            messageBytes = msg.toByteArray(),
            nonce = nonce,
            key = Key.fromBytes(sharedSecret)
        )

        return EncryptedInfo(
            ciphertext = cipher ?: ByteArray(0),
            nonce = nonce,
        )
    }

    fun decrypt(payload: String, privateKey: ByteArray, pubKey: ByteArray): String? {
        val sharedSecret = getSharedSecret(privateKey, pubKey)
        return decrypt(payload, sharedSecret)
    }

    fun decrypt(encryptedInfo: EncryptedInfo, privateKey: ByteArray, pubKey: ByteArray): String? {
        val sharedSecret = getSharedSecret(privateKey, pubKey)
        return decrypt(encryptedInfo, sharedSecret)
    }

    fun decrypt(payload: String, sharedSecret: ByteArray): String? {
        val encryptedInfo = EncryptedInfo.decodePayload(payload) ?: return null
        return decrypt(encryptedInfo, sharedSecret)
    }

    fun decrypt(encryptedInfo: EncryptedInfo, sharedSecret: ByteArray): String? {
        return cryptoStreamXChaCha20Xor(
            libSodium = libSodium,
            messageBytes = encryptedInfo.ciphertext,
            nonce = encryptedInfo.nonce,
            key = Key.fromBytes(sharedSecret)
        )?.decodeToString()
    }

    fun getSharedSecret(privateKey: ByteArray, pubKey: ByteArray): ByteArray {
        val preComputed = sharedKeyCache.get(privateKey, pubKey)
        if (preComputed != null) return preComputed

        val computed = computeSharedSecret(privateKey, pubKey)
        sharedKeyCache.add(privateKey, pubKey, computed)
        return computed
    }

    /**
     * @return 32B shared secret
     */
    fun computeSharedSecret(privateKey: ByteArray, pubKey: ByteArray): ByteArray =
        sha256(
            secp256k1.pubKeyTweakMul(h02 + pubKey, privateKey).copyOfRange(1, 33)
        )

    fun sha256(data: ByteArray): ByteArray {
        // Creates a new buffer every time
        return MessageDigest.getInstance("SHA-256").digest(data)
    }

    class EncryptedInfo(
        val ciphertext: ByteArray,
        val nonce: ByteArray
    ) {
        companion object {
            const val v: Int = 1

            fun decodePayload(payload: String): EncryptedInfo? {
                return try {
                    val byteArray = Base64.getDecoder().decode(payload)
                    check(byteArray[0].toInt() == v)
                    return EncryptedInfo(
                        nonce = byteArray.copyOfRange(1, 25),
                        ciphertext = byteArray.copyOfRange(25, byteArray.size)
                    )
                } catch (e: Exception) {
                    Log.w("NIP44v1", "Unable to Parse encrypted payload: ${payload}")
                    null
                }
            }
        }

        fun encodePayload(): String {
            return Base64.getEncoder().encodeToString(
                byteArrayOf(v.toByte()) + nonce + ciphertext
            )
        }
    }
}