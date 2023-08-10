package com.vitorpamplona.amethyst.service

import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.utils.Key
import fr.acinq.secp256k1.Hex
import fr.acinq.secp256k1.Secp256k1
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {
    private val secp256k1 = Secp256k1.get()
    private val libSodium = SodiumAndroid()
    private val random = SecureRandom()

    fun randomInt(bound: Int): Int {
        return random.nextInt(bound)
    }

    /**
     * Provides a 32B "private key" aka random number
     */
    fun privkeyCreate(): ByteArray {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return bytes
    }

    fun pubkeyCreate(privKey: ByteArray) =
        secp256k1.pubKeyCompress(secp256k1.pubkeyCreate(privKey)).copyOfRange(1, 33)

    fun sign(data: ByteArray, privKey: ByteArray): ByteArray =
        secp256k1.signSchnorr(data, privKey, null)

    fun verifySignature(
        signature: ByteArray,
        hash: ByteArray,
        pubKey: ByteArray
    ): Boolean {
        return secp256k1.verifySchnorr(signature, hash, pubKey)
    }

    fun sha256(data: ByteArray): ByteArray {
        // Creates a new buffer every time
        return MessageDigest.getInstance("SHA-256").digest(data)
    }

    /**
     * @return 32B shared secret
     */
    fun getSharedSecretNIP04(privateKey: ByteArray, pubKey: ByteArray): ByteArray =
        secp256k1.pubKeyTweakMul(Hex.decode("02") + pubKey, privateKey).copyOfRange(1, 33)

    fun encryptNIP04(msg: String, privateKey: ByteArray, pubKey: ByteArray): String {
        val encryptionInfo = encryptNIP04(msg, getSharedSecretNIP04(privateKey, pubKey))
        return "${encryptionInfo.ciphertext}?iv=${encryptionInfo.nonce}"
    }

    fun encryptNIP04Json(msg: String, privateKey: ByteArray, pubKey: ByteArray): EncryptedInfo {
        return encryptNIP04(msg, getSharedSecretNIP04(privateKey, pubKey))
    }

    fun encryptNIP04(msg: String, sharedSecret: ByteArray): EncryptedInfo {
        val iv = ByteArray(16)
        random.nextBytes(iv)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(sharedSecret, "AES"), IvParameterSpec(iv))
        val ivBase64 = Base64.getEncoder().encodeToString(iv)
        val encryptedMsg = cipher.doFinal(msg.toByteArray())
        val encryptedMsgBase64 = Base64.getEncoder().encodeToString(encryptedMsg)
        return EncryptedInfo(encryptedMsgBase64, ivBase64, Nip44Version.NIP04.versionCode)
    }

    fun decryptNIP04(msg: String, privateKey: ByteArray, pubKey: ByteArray): String {
        val sharedSecret = getSharedSecretNIP04(privateKey, pubKey)
        return decryptNIP04(msg, sharedSecret)
    }

    fun decryptNIP04(encryptedInfo: EncryptedInfo, privateKey: ByteArray, pubKey: ByteArray): String {
        val sharedSecret = getSharedSecretNIP04(privateKey, pubKey)
        return decryptNIP04(encryptedInfo.ciphertext, encryptedInfo.nonce, sharedSecret)
    }

    fun decryptNIP04(msg: String, sharedSecret: ByteArray): String {
        val parts = msg.split("?iv=")
        return decryptNIP04(parts[0], parts[1], sharedSecret)
    }

    private fun decryptNIP04(cipher: String, nonce: String, sharedSecret: ByteArray): String {
        val iv = Base64.getDecoder().decode(nonce)
        val encryptedMsg = Base64.getDecoder().decode(cipher)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(sharedSecret, "AES"), IvParameterSpec(iv))
        return String(cipher.doFinal(encryptedMsg))
    }

    fun encryptNIP24(msg: String, privateKey: ByteArray, pubKey: ByteArray): EncryptedInfo {
        val sharedSecret = getSharedSecretNIP24(privateKey, pubKey)
        return encryptNIP24(msg, sharedSecret)
    }

    fun encryptNIP24(msg: String, sharedSecret: ByteArray): EncryptedInfo {
        val nonce = ByteArray(24)
        random.nextBytes(nonce)

        val cipher = cryptoStreamXChaCha20Xor(
            libSodium = libSodium,
            messageBytes = msg.toByteArray(),
            nonce = nonce,
            key = Key.fromBytes(sharedSecret)
        )

        val cipherBase64 = Base64.getEncoder().encodeToString(cipher)
        val nonceBase64 = Base64.getEncoder().encodeToString(nonce)

        return EncryptedInfo(
            ciphertext = cipherBase64,
            nonce = nonceBase64,
            v = Nip44Version.NIP24.versionCode
        )
    }

    fun decryptNIP24(encryptedInfo: EncryptedInfo, privateKey: ByteArray, pubKey: ByteArray): String? {
        val sharedSecret = getSharedSecretNIP24(privateKey, pubKey)
        return decryptNIP24(encryptedInfo, sharedSecret)
    }

    fun decryptNIP24(encryptedInfo: EncryptedInfo, sharedSecret: ByteArray): String? {
        return cryptoStreamXChaCha20Xor(
            libSodium = libSodium,
            messageBytes = Base64.getDecoder().decode(encryptedInfo.ciphertext),
            nonce = Base64.getDecoder().decode(encryptedInfo.nonce),
            key = Key.fromBytes(sharedSecret)
        )?.decodeToString()
    }

    /**
     * @return 32B shared secret
     */
    fun getSharedSecretNIP24(privateKey: ByteArray, pubKey: ByteArray): ByteArray =
        sha256(secp256k1.pubKeyTweakMul(Hex.decode("02") + pubKey, privateKey).copyOfRange(1, 33))
}

data class EncryptedInfo(val ciphertext: String, val nonce: String, val v: Int)

enum class Nip44Version(val versionCode: Int) {
    NIP04(0),
    NIP24(1)
}
