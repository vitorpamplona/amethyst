package com.vitorpamplona.amethyst.service

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
    private val random = SecureRandom()

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

    fun encrypt(msg: String, privateKey: ByteArray, pubKey: ByteArray): String {
        val sharedSecret = getSharedSecret(privateKey, pubKey)
        return encrypt(msg, sharedSecret)
    }

    fun encrypt(msg: String, sharedSecret: ByteArray): String {
        val iv = ByteArray(16)
        random.nextBytes(iv)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(sharedSecret, "AES"), IvParameterSpec(iv))
        val ivBase64 = Base64.getEncoder().encodeToString(iv)
        val encryptedMsg = cipher.doFinal(msg.toByteArray())
        val encryptedMsgBase64 = Base64.getEncoder().encodeToString(encryptedMsg)
        return "$encryptedMsgBase64?iv=$ivBase64"
    }

    fun decrypt(msg: String, privateKey: ByteArray, pubKey: ByteArray): String {
        val sharedSecret = getSharedSecret(privateKey, pubKey)
        return decrypt(msg, sharedSecret)
    }

    fun decrypt(msg: String, sharedSecret: ByteArray): String {
        val parts = msg.split("?iv=")
        val iv = parts[1].run { Base64.getDecoder().decode(this) }
        val encryptedMsg = parts.first().run { Base64.getDecoder().decode(this) }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(sharedSecret, "AES"), IvParameterSpec(iv))
        return String(cipher.doFinal(encryptedMsg))
    }

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
    fun getSharedSecret(privateKey: ByteArray, pubKey: ByteArray): ByteArray =
        secp256k1.pubKeyTweakMul(Hex.decode("02") + pubKey, privateKey).copyOfRange(1, 33)
}

fun Int.toByteArray(): ByteArray {
    val bytes = ByteArray(4)
    (0..3).forEach {
        bytes[3 - it] = ((this ushr (8 * it)) and 0xFFFF).toByte()
    }
    return bytes
}
